package com.sharma2464.tindercompression.compress

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.sharma2464.tindercompression.TinderCompressionApp

/** Hosts the long-running compression WorkManager job with a visible progress notification. */
class CompressionForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Starting compression…"))
        WorkManager.getInstance(this).enqueueUniqueWork(
            CompressionWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<CompressionWorker>().build(),
        )
        return START_NOT_STICKY
    }

    private fun buildNotification(text: String) =
        NotificationCompat.Builder(this, TinderCompressionApp.COMPRESSION_CHANNEL_ID)
            .setContentTitle("Compressing files")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()

    companion object {
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            context.startForegroundService(Intent(context, CompressionForegroundService::class.java))
        }

        fun updateProgress(context: Context, current: Int, total: Int, fileName: String) {
            val manager = context.getSystemService(android.app.NotificationManager::class.java)
            val notification = NotificationCompat.Builder(context, TinderCompressionApp.COMPRESSION_CHANNEL_ID)
                .setContentTitle("Compressing files ($current/$total)")
                .setContentText(fileName)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        }
    }
}
