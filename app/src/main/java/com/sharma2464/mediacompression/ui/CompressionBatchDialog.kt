package com.sharma2464.mediacompression.ui

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.work.WorkManager
import com.sharma2464.mediacompression.compress.batchOverallFraction
import com.sharma2464.mediacompression.compress.CompressionEstimator
import com.sharma2464.mediacompression.compress.CompressionPathResolver
import com.sharma2464.mediacompression.compress.CompressionStatus
import com.sharma2464.mediacompression.compress.CompressionWorker
import com.sharma2464.mediacompression.compress.FileCompressionState
import com.sharma2464.mediacompression.compress.enqueueCompression
import com.sharma2464.mediacompression.data.FileKind
import com.sharma2464.mediacompression.scan.classifyFile
import com.sharma2464.mediacompression.scan.guessMimeType
import com.sharma2464.mediacompression.settings.AppSettings
import com.sharma2464.mediacompression.settings.CompressionMode
import com.sharma2464.mediacompression.settings.StorageMode
import kotlinx.coroutines.launch
import java.io.File

enum class CompressDialogStage { Preview, Progress }

data class CompressPreviewItem(
    val file: File,
    val name: String,
    val kind: FileKind?,
    val sizeBytes: Long,
)

data class CompressPreviewState(
    val items: List<CompressPreviewItem>,
    val totalBytes: Long,
    val locationPath: String,
    val destinationPath: String,
    val estimatedAfterBytes: Long,
    val modeLabel: String,
    val storageLabel: String,
    val selectedRoots: Set<File>,
)

fun buildCompressPreviewState(context: android.content.Context, selected: Set<File>): CompressPreviewState {
    val settings = AppSettings(context)
    val expanded = expandSelectedFiles(selected)
    val items = expanded.map { file ->
        val mime = guessMimeType(file.name)
        CompressPreviewItem(
            file = file,
            name = file.name,
            kind = classifyFile(mime),
            sizeBytes = file.length(),
        )
    }
    val totalBytes = items.sumOf { it.sizeBytes }
    val sample = expanded.firstOrNull() ?: selected.first()
    val pairs = items.map { it.file to it.kind }
    val estimated = CompressionEstimator.estimatedBytesAfter(pairs, settings.compressionMode)
    val modeLabel = when (settings.compressionMode) {
        CompressionMode.LOSSLESS_ONLY -> "Lossless only"
        CompressionMode.ADAPTIVE -> "Adaptive"
    }
    val storageLabel = when (settings.storageMode) {
        StorageMode.COMPRESSED_COPY -> "Compressed copy"
        StorageMode.REPLACE_IN_PLACE -> "Replace in place"
    }
    return CompressPreviewState(
        items = items,
        totalBytes = totalBytes,
        locationPath = CompressionPathResolver.commonParentPath(selected.toList()),
        destinationPath = CompressionPathResolver.destinationDisplayPath(context, sample),
        estimatedAfterBytes = estimated,
        modeLabel = modeLabel,
        storageLabel = storageLabel,
        selectedRoots = selected,
    )
}

private fun expandSelectedFiles(selected: Set<File>): List<File> {
    val files = mutableListOf<File>()
    selected.forEach { file ->
        if (file.isDirectory) {
            file.walk().filter { it.isFile }.forEach { files += it }
        } else {
            files += file
        }
    }
    return files
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompressionBatchDialog(
    visible: Boolean,
    stage: CompressDialogStage,
    previewState: CompressPreviewState?,
    onDismissPreview: () -> Unit,
    onMinimizeProgress: () -> Unit,
    onStageChange: (CompressDialogStage) -> Unit,
    onCloseAfterBatch: () -> Unit,
) {
    if (!visible) return

    val context = LocalContext.current
    val batch by CompressionStatus.batch.collectAsState()
    val scope = rememberCoroutineScope()
    var hadActiveBatch by remember { mutableStateOf(false) }

    LaunchedEffect(batch, stage) {
        if (batch != null) {
            hadActiveBatch = true
        } else if (hadActiveBatch && stage == CompressDialogStage.Progress) {
            hadActiveBatch = false
            onCloseAfterBatch()
        }
    }

    val onDismissRequest: () -> Unit = {
        when (stage) {
            CompressDialogStage.Preview -> onDismissPreview()
            CompressDialogStage.Progress -> onMinimizeProgress()
        }
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = MaterialTheme.shapes.large) {
            when (stage) {
                CompressDialogStage.Preview -> {
                    val state = previewState ?: return@Surface
                    PreviewStage(
                        state = state,
                        onCancel = onDismissPreview,
                        onStart = {
                            scope.launch {
                                enqueueCompression(context, state.selectedRoots)
                                onStageChange(CompressDialogStage.Progress)
                            }
                        },
                        onDestinationPicked = { uri ->
                            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            context.contentResolver.takePersistableUriPermission(uri, flags)
                            AppSettings(context).sessionDestinationTreeUri = uri.toString()
                        },
                    )
                }
                CompressDialogStage.Progress -> {
                    ProgressStage(
                        batch = batch,
                        onMinimize = onMinimizeProgress,
                        onCancelConfirmed = {
                            CompressionStatus.requestCancel()
                            WorkManager.getInstance(context).cancelUniqueWork(CompressionWorker.WORK_NAME)
                            onCloseAfterBatch()
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PreviewStage(
    state: CompressPreviewState,
    onCancel: () -> Unit,
    onStart: () -> Unit,
    onDestinationPicked: (Uri) -> Unit,
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(state.items.size <= 3) }
    var destinationPath by remember(state) { mutableStateOf(state.destinationPath) }

    val pickTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            onDestinationPicked(uri)
            destinationPath = CompressionPathResolver.destinationDisplayPath(
                context,
                state.items.firstOrNull()?.file ?: state.selectedRoots.first(),
            )
        }
    }

    Column(Modifier.padding(20.dp)) {
        Text("Compress files", style = MaterialTheme.typography.titleLarge)
        Text(
            "${state.items.size} files · ${Formatter.formatShortFileSize(context, state.totalBytes)}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        Text("Location", style = MaterialTheme.typography.labelMedium)
        Text(
            state.locationPath,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            modifier = Modifier.basicMarquee(),
        )
        Text("Destination", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
        Text(
            destinationPath,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            modifier = Modifier.basicMarquee(),
        )
        TextButton(onClick = { pickTree.launch(null) }) {
            Text("Change folder")
        }
        Text(
            "${state.modeLabel} · ${state.storageLabel}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Before: ${Formatter.formatShortFileSize(context, state.totalBytes)}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "Estimated after compression: ${Formatter.formatShortFileSize(context, state.estimatedAfterBytes)}",
            style = MaterialTheme.typography.bodyMedium,
        )

        if (state.items.size > 3) {
            Text(
                if (expanded) "Hide file list" else "Show file list (${state.items.size})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clickable { expanded = !expanded },
            )
        }
        if (expanded) {
            LazyColumn(Modifier.heightIn(max = 200.dp).padding(top = 4.dp)) {
                items(state.items, key = { it.file.absolutePath }) { item ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            item.name,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            Formatter.formatShortFileSize(context, item.sizeBytes),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("Cancel")
            }
            Button(onClick = onStart, modifier = Modifier.weight(1f).testTag("compress_start")) {
                Text("Start")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProgressStage(
    batch: com.sharma2464.mediacompression.compress.CompressionBatch?,
    onMinimize: () -> Unit,
    onCancelConfirmed: () -> Unit,
) {
    val context = LocalContext.current
    var cancelArmed by remember { mutableStateOf(false) }
    var cancelConfirmTaps by remember { mutableStateOf(0) }

    LaunchedEffect(batch) {
        cancelArmed = false
        cancelConfirmTaps = 0
    }

    Column(Modifier.padding(20.dp)) {
        Text("Compressing files", style = MaterialTheme.typography.titleLarge)

        if (batch == null) {
            Text("Starting…", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onMinimize) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimize")
                }
            }
            return@Column
        }

        Text(
            batch.modeLabel,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            "Total files: ${batch.files.size}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp),
        )

        val currentName = batch.files.getOrNull(batch.currentIndex)?.fileName
            ?: batch.files.lastOrNull { it.state != FileCompressionState.QUEUED }?.fileName
            ?: "Preparing…"
        Text(
            "Current: $currentName",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            modifier = Modifier.basicMarquee().padding(top = 4.dp),
        )

        val rateText = if (batch.rateBytesPerSec > 0) {
            "Rate: ${Formatter.formatShortFileSize(context, batch.rateBytesPerSec)}/s"
        } else {
            "Rate: —"
        }
        Text(
            rateText,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
        )

        val fraction = batchOverallFraction(batch)
        val percent = (fraction * 100).toInt().coerceIn(0, 100)
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        Text(
            "$percent%",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        if (cancelArmed) {
            Text(
                "Compression will stop. Already compressed files are kept.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        if (cancelConfirmTaps == 0) {
                            cancelConfirmTaps = 1
                        } else {
                            onCancelConfirmed()
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (cancelConfirmTaps == 0) "Double tap to cancel" else "Tap again to cancel")
                }
            }
            TextButton(
                onClick = {
                    cancelArmed = false
                    cancelConfirmTaps = 0
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Don't cancel")
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onMinimize) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimize")
                }
                TextButton(onClick = { cancelArmed = true }) {
                    Text("Cancel")
                }
            }
        }
    }
}
