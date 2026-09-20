package com.sharma2464.mediacompression.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.work.WorkManager
import com.sharma2464.mediacompression.compress.CompressionStatus
import com.sharma2464.mediacompression.compress.CompressionWorker
import com.sharma2464.mediacompression.compress.FileCompressionState
import com.sharma2464.mediacompression.compress.FileProgress

/** Shown only while a compression batch is active; tap opens the progress dialog. */
@Composable
fun CompressionProgressFab() {
    val context = LocalContext.current
    val batch by CompressionStatus.batch.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    val currentBatch = batch ?: return

    FloatingActionButton(onClick = { showDialog = true }) {
        val overallFraction = remember(currentBatch) {
            val done = currentBatch.files.count { it.state == FileCompressionState.DONE }
            val currentPartial = currentBatch.files.getOrNull(currentBatch.currentIndex)
                ?.takeIf { it.state == FileCompressionState.IN_PROGRESS }?.percent ?: 0
            (done + currentPartial / 100f) / currentBatch.files.size.coerceAtLeast(1)
        }
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(progress = { overallFraction }, modifier = Modifier.size(40.dp))
            Icon(Icons.Default.Refresh, contentDescription = "Compression in progress")
        }
    }

    if (showDialog) {
        Dialog(onDismissRequest = { showDialog = false }) {
            Surface(shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(20.dp)) {
                    Text("Compressing files", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${currentBatch.modeLabel} compression",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    )

                    // Always rendered (even with a placeholder) so the dialog's height stays
                    // fixed — hiding this row whenever the rate ticked to 0 made the whole
                    // dialog (and everything below it) jump every time it toggled.
                    val rateText = if (currentBatch.rateBytesPerSec > 0) {
                        "Rate: ${Formatter.formatShortFileSize(context, currentBatch.rateBytesPerSec)}/s"
                    } else {
                        "Rate: —"
                    }
                    Text(
                        rateText,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )

                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(currentBatch.files, key = { it.fileName }) { file ->
                            FileProgressRow(file)
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 12.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { showDialog = false }, modifier = Modifier.weight(1f)) {
                            Text("Minimize")
                        }
                        Button(
                            onClick = {
                                CompressionStatus.requestCancel()
                                WorkManager.getInstance(context).cancelUniqueWork(CompressionWorker.WORK_NAME)
                                showDialog = false
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(" Cancel", modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileProgressRow(file: FileProgress) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                file.fileName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(stateLabel(file), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 8.dp))
        }
        val progress = when (file.state) {
            FileCompressionState.DONE -> 1f
            FileCompressionState.IN_PROGRESS -> file.percent / 100f
            else -> 0f
        }
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
    }
}

private fun stateLabel(file: FileProgress): String = when (file.state) {
    FileCompressionState.QUEUED -> "Queued"
    FileCompressionState.IN_PROGRESS -> "${file.percent}%"
    FileCompressionState.DONE -> "Done"
    FileCompressionState.FAILED -> "Failed"
    FileCompressionState.CANCELLED -> "Cancelled"
}
