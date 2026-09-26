package com.kelele.manliu

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** A visible import task keeps running while the user reads or leaves the app. */
class ImportService : Service() {
    companion object {
        private const val CHANNEL = "manliu_import"
        private const val NOTIFICATION_ID = 1201

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ImportService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: ComicRepository
    private lateinit var notifications: NotificationManager
    private var processor: Job? = null
    @Volatile private var latestStartId = 0

    override fun onCreate() {
        super.onCreate()
        repository = ComicRepository.get(applicationContext)
        notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, "漫流图片导入", NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        val initial = notification("正在准备导入", 0, 0)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, initial)
        }
        if (processor?.isActive != true) {
            processor = scope.launch {
                try {
                    do {
                        val observedStart = latestStartId
                        while (repository.processNextImport { job ->
                            notifications.notify(
                                NOTIFICATION_ID,
                                notification("已处理 ${job.processed} / ${job.total} 张", job.processed, job.total),
                            )
                        }) { /* Continue queued tasks. */ }
                    } while (observedStart != latestStartId)
                } catch (error: Exception) {
                    repository.pauseRunningImport("导入中断：${error.message ?: "请稍后重试"}")
                } finally {
                    stopSelf(latestStartId)
                }
            }
        }
        return START_STICKY
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
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
