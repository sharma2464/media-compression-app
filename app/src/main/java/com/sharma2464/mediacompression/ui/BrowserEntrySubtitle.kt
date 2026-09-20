package com.sharma2464.mediacompression.ui

import android.content.Context
import android.text.format.Formatter
fun formatBrowserEntrySubtitle(context: Context, entry: BrowserEntry): String {
    if (entry.isDirectory) {
        val dirSize = Formatter.formatShortFileSize(context, entry.dirTotalSizeBytes)
        val fileCount = entry.dirFileCount
        return "$dirSize • $fileCount ${if (fileCount == 1) "file" else "files"}"
    }
    val fileSize = Formatter.formatShortFileSize(context, entry.sizeBytes)
    val fileType = entry.kind?.name ?: "FILE"
    val dateText = formatTimeFromNow(entry.dateTakenMs)
    return listOfNotNull(fileSize, fileType, dateText.takeIf { it.isNotEmpty() }).joinToString(" • ")
}
