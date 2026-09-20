package com.sharma2464.mediacompression

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.media3.common.util.UnstableApi
import com.arthenica.smartexception.java.Exceptions

@UnstableApi
class MediaCompressionApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // FFmpegKit loads this reflectively; an explicit reference keeps it in the dex merge.
        @Suppress("UNUSED_VARIABLE")
        val keepFfmpegKitHelper = Exceptions::class
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
