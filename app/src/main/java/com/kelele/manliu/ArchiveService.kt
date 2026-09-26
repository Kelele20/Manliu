package com.kelele.manliu

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ArchiveService : Service() {
    companion object {
        private const val EXPORT = "com.kelele.manliu.EXPORT"
        private const val RESTORE = "com.kelele.manliu.RESTORE"
        private const val CANCEL = "com.kelele.manliu.CANCEL_ARCHIVE"
        private const val CHANNEL = "manliu_backup"
        private const val NOTIFICATION_ID = 1202

        fun export(context: Context, uri: Uri, albumId: Long? = null) {
            start(context, EXPORT, uri, albumId)
        }

        fun restore(context: Context, uri: Uri) {
            start(context, RESTORE, uri, null)
        }

        fun cancel(context: Context) {
            context.startService(Intent(context, ArchiveService::class.java).setAction(CANCEL))
        }

        private fun start(context: Context, action: String, uri: Uri, albumId: Long?) {
            val intent = Intent(context, ArchiveService::class.java).setAction(action)
                .putExtra("uri", uri.toString())
            if (albumId != null) intent.putExtra("albumId", albumId)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var notifications: NotificationManager
    private var task: Job? = null

    override fun onCreate() {
        super.onCreate()
        notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, "漫流备份恢复", NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == CANCEL) {
            task?.cancel()
            if (task?.isActive != true) stopSelf(startId)
            return START_NOT_STICKY
        }
        if (task?.isActive == true) return START_NOT_STICKY
        val action = intent?.action
        val uri = intent?.getStringExtra("uri")?.let(Uri::parse)
        if (action !in setOf(EXPORT, RESTORE) || uri == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val label = if (action == EXPORT) "正在备份" else "正在恢复"
        val initial = ArchiveProgress(label, 0, 0, true)
        ArchiveStatus.update(initial)
        showNotification(initial, start = true)
        if (task?.isActive != true) {
            task = scope.launch {
                val archive = ArchiveManager(applicationContext, ComicRepository.get(applicationContext))
                try {
                    val onProgress: (Int, Int) -> Unit = { done, total ->
                        val next = ArchiveProgress(label, done, total, true)
                        ArchiveStatus.update(next)
                        showNotification(next)
                    }
                    if (action == EXPORT) {
                        archive.export(uri, intent.getLongExtra("albumId", 0).takeIf { it != 0L }, onProgress)
                    } else {
                        archive.restore(uri, onProgress)
                    }
                    val latest = ArchiveStatus.progress.value ?: initial
                    ArchiveStatus.update(latest.copy(label = if (action == EXPORT) "备份完成" else "恢复完成", running = false))
                } catch (_: CancellationException) {
                    ArchiveStatus.update(ArchiveProgress("任务已取消", 0, 0, false, "如有未完成的备份文件，可在文件管理器中删除"))
                } catch (error: Exception) {
                    ArchiveStatus.update(ArchiveProgress("备份恢复失败", 0, 0, false, error.message ?: "请重试"))
                } finally {
                    stopSelf(startId)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun showNotification(progress: ArchiveProgress, start: Boolean = false) {
        val returnToApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notice: Notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(progress.label)
            .setContentText("${progress.processed} / ${progress.total} 张图片")
            .setContentIntent(returnToApp)
            .setOnlyAlertOnce(true)
            .setOngoing(progress.running)
            .setProgress(progress.total, progress.processed, progress.total == 0)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            if (start) startForeground(NOTIFICATION_ID, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else notifications.notify(NOTIFICATION_ID, notice)
        } else {
            if (start) startForeground(NOTIFICATION_ID, notice)
            else notifications.notify(NOTIFICATION_ID, notice)
        }
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        task?.cancel()
        ArchiveStatus.update(ArchiveProgress("任务中断", 0, 0, false, "系统限制后台运行时长，请重新开始"))
        stopSelf(startId)
    }

    override fun onDestroy() {
        scope.cancel()
        if (ArchiveStatus.progress.value?.running == true) {
            ArchiveStatus.update(ArchiveProgress("任务中断", 0, 0, false, "请重新开始"))
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
