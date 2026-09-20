package com.sharma2464.mediacompression.compress

import android.content.Context
import android.net.Uri
import com.sharma2464.mediacompression.scan.FolderScanner
import com.sharma2464.mediacompression.scan.resolveRealFile
import com.sharma2464.mediacompression.settings.AppSettings
import com.sharma2464.mediacompression.settings.StorageMode
import java.io.File

object CompressionPathResolver {
    fun destinationDisplayPath(context: Context, sampleSourceFile: File): String {
        val settings = AppSettings(context)
        val storageMode = settings.storageMode
        if (storageMode == StorageMode.REPLACE_IN_PLACE) {
            val parent = sampleSourceFile.parent ?: sampleSourceFile.absolutePath
            return "$parent (replace in place; originals in ${FolderScanner.ORIGINALS_DIR_NAME}/)"
        }
        val override = settings.sessionDestinationTreeUri ?: settings.destinationTreeUri
        override?.let { uriStr ->
            resolveRealFile(Uri.parse(uriStr))?.absolutePath?.let { return it }
            return uriStr
        }
        val parent = sampleSourceFile.parent ?: return "COMPRESSED/"
        return File(parent, FolderScanner.DEFAULT_DESTINATION_DIR_NAME).absolutePath
    }

    fun commonParentPath(files: List<File>): String {
        if (files.isEmpty()) return ""
        val parents = files.mapNotNull { it.parentFile?.absolutePath }.distinct()
        if (parents.size == 1) return parents.first()
        return files.first().parentFile?.absolutePath ?: files.first().absolutePath
    }
}
