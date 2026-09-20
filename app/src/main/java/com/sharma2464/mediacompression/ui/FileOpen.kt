package com.sharma2464.mediacompression.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.sharma2464.mediacompression.scan.guessMimeType
import java.io.File

fun openFileWithDefaultApp(context: Context, file: File) {
    if (!file.isFile || !file.canRead()) {
        Toast.makeText(context, "Cannot read file", Toast.LENGTH_SHORT).show()
        return
    }
    val authority = "${context.packageName}.fileprovider"
    val uri = FileProvider.getUriForFile(context, authority, file)
    val mime = guessMimeType(file.name)
    val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(view)
    } catch (_: ActivityNotFoundException) {
        try {
            view.setDataAndType(uri, "*/*")
            context.startActivity(Intent.createChooser(view, "Open with"))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
        }
    }
}
