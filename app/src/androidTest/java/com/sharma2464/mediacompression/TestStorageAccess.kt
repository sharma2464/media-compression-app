package com.sharma2464.mediacompression

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File

object TestStorageAccess {
    val largestFilesDir: File = File("/storage/emulated/0/largest_files")

    fun ensureAllFilesAccess(device: UiDevice? = null): Boolean {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pkg = context.packageName
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "appops set $pkg MANAGE_EXTERNAL_STORAGE allow",
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$pkg"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            if (device != null) {
                val deadline = System.currentTimeMillis() + 90_000
                while (System.currentTimeMillis() < deadline) {
                    if (Environment.isExternalStorageManager()) break
                    Thread.sleep(500)
                }
            }
        }
        return Environment.isExternalStorageManager() || canReadLargestFiles()
    }

    fun canReadLargestFiles(): Boolean {
        val sample = File(largestFilesDir, "VID20260901164336.mp4")
        return sample.isFile && sample.canRead()
    }
}
