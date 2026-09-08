package com.sharma2464.tindercompression.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sharma2464.tindercompression.data.FileEntry
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsSheet(entry: FileEntry, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(24.dp)) {
            Text("Name: ${entry.displayName}")
            Text("Path: ${entry.relativePath}")
            Text("Type: ${entry.mimeType} (${entry.kind})")
            Text("Size: ${Formatter.formatShortFileSize(context, entry.sizeBytes)}")
            Text("Last modified: ${DateFormat.getDateTimeInstance().format(Date(entry.lastModified))}")
            Text("Decision: ${entry.decision}")
            entry.compressedSizeBytes?.let { Text("Compressed size: ${Formatter.formatShortFileSize(context, it)}") }
            entry.wasLossless?.let { Text("Lossless: $it") }
        }
    }
}
