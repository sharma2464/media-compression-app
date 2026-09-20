package com.sharma2464.mediacompression.scan

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import java.io.File

data class BrowsableVolume(val label: String, val rootDir: File)

/** Lists mounted storage volumes as user-browsable tabs (Internal Storage, SD card, etc.). */
fun listBrowsableVolumes(context: Context): List<BrowsableVolume> {
    val volumes = mutableListOf<BrowsableVolume>()

    // Primary storage is always accessible at /sdcard or Environment.getExternalStorageDirectory()
    volumes += BrowsableVolume(
        label = "Internal Storage",
        rootDir = Environment.getExternalStorageDirectory(),
    )

    // Secondary volumes (SD cards, USB) only enumerable on API 24+ (StorageManager), with
    // real paths only on API 30+ (StorageVolume.directory). ponytail: pre-API 30, only the
    // primary volume is resolvable to a real path; upgrade to secondary-volume support when
    // minSdk advances past 29.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager ?: return volumes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            sm.storageVolumes.forEach { vol ->
                if (!vol.isPrimary && vol.state == Environment.MEDIA_MOUNTED) {
                    val dir = vol.directory
                    if (dir != null && dir.isDirectory) {
                        volumes += BrowsableVolume(
                            label = vol.getDescription(context),
                            rootDir = dir,
                        )
                    }
                }
            }
        }
    }

    return volumes
}
