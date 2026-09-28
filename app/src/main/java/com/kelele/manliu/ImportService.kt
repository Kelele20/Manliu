package com.kelele.manliu

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.ClipData
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.net.Uri
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow

object ImportFeedback {
    val message = MutableStateFlow<String?>(null)

    fun clear(value: String) {
        if (message.value == value) message.value = null
    }
}

/** A visible import task keeps running while the user reads or leaves the app. */
class ImportService : Service() {
    companion object {
        private const val CHANNEL = "manliu_import"
        private const val NOTIFICATION_ID = 1201
        private const val EXTRA_ALBUM_ID = "albumId"
        private const val EXTRA_URIS = "selectedUris"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ImportService::class.java))
        }

        fun startSelected(context: Context, albumId: Long, uris: List<Uri>) {
            val selected = uris.distinct().take(100)
            if (selected.isEmpty()) return
            val intent = Intent(context, ImportService::class.java).apply {
                putExtra(EXTRA_ALBUM_ID, albumId)
                putParcelableArrayListExtra(EXTRA_URIS, ArrayList(selected))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(context.contentResolver, "选中的图片", selected.first()).also { clip ->
                    selected.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                }
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: ComicRepository
    private lateinit var notifications: NotificationManager
    private val processorMutex = Mutex()

    override fun onCreate() {
        super.onCreate()
        repository = ComicRepository.get(applicationContext)
        notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, "漫流图片导入", NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val initial = notification("正在准备导入", 0, 0)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, initial)
        }
        val selected = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableArrayListExtra(EXTRA_URIS, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableArrayListExtra(EXTRA_URIS)
        }
        val albumId = intent?.getLongExtra(EXTRA_ALBUM_ID, 0L) ?: 0L
        scope.launch {
            processorMutex.withLock {
                try {
                    if (!selected.isNullOrEmpty()) {
                        try {
                            repository.createSelectedImport(albumId, selected)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            reportFailure(error.message ?: "无法开始导入")
                        }
                    }
                    while (true) {
                        if (repository.prepareNextImport { staged, total ->
                                notifications.notify(
                                    NOTIFICATION_ID,
                                    notification("正在保存所选图片 $staged / $total", staged, total),
                                )
                            }) continue
                        val processed = repository.processNextImport { job ->
                            notifications.notify(
                                NOTIFICATION_ID,
                                notification("已处理 ${job.processed} / ${job.total} 张", job.processed, job.total),
                            )
                        }
                        if (!processed) break
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    val message = error.message ?: "请稍后重试"
                    repository.pauseRunningImport("导入中断：$message")
                    reportFailure(message)
                } finally {
                    stopSelf(startId)
                }
            }
        }
        return START_STICKY
    }

    private fun failureNotification(message: String): Notification = Notification.Builder(this, CHANNEL)
        .setSmallIcon(android.R.drawable.stat_notify_error)
        .setContentTitle("导入未完成")
        .setContentText(message)
        .setAutoCancel(true)
        .build()

    private fun reportFailure(message: String) {
        ImportFeedback.message.value = "导入未完成：$message"
        notifications.notify(NOTIFICATION_ID + 1, failureNotification(message))
    }

    private fun notification(text: String, processed: Int, total: Int): Notification {
        val returnToApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("漫流正在导入图片")
            .setContentText(text)
            .setContentIntent(returnToApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(total, processed, total == 0)
            .build()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Android 15 limits how long a dataSync foreground service may run.
        runBlocking { repository.pauseRunningImport("系统暂时停止了后台任务，打开漫流可继续") }
        stopSelf(startId)
    }

    override fun onDestroy() {
        scope.cancel()
        // 重置全局反馈状态，防止 Service 重建时残留旧的错误信息
        ImportFeedback.message.value?.let { ImportFeedback.clear(it) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
