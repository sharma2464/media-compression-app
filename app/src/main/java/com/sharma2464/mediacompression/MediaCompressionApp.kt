package com.sharma2464.mediacompression

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class MediaCompressionApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                COMPRESSION_CHANNEL_ID,
                "Compression progress",
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val COMPRESSION_CHANNEL_ID = "compression_progress"
    }
}
