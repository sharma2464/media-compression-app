package com.sharma2464.mediacompression.ui

import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.work.WorkManager
import com.sharma2464.mediacompression.compress.CompressJobSettings
import com.sharma2464.mediacompression.compress.CompressSettingsEstimator
import com.sharma2464.mediacompression.compress.CompressionEstimator
import com.sharma2464.mediacompression.compress.CompressionPathResolver
import com.sharma2464.mediacompression.compress.CompressionProfile
import com.sharma2464.mediacompression.compress.CompressionStrength
import com.sharma2464.mediacompression.compress.CompressionStatus
import com.sharma2464.mediacompression.compress.CompressionWorker
import com.sharma2464.mediacompression.compress.FileCompressionState
import com.sharma2464.mediacompression.compress.batchOverallFraction
import com.sharma2464.mediacompression.compress.enqueueCompression
import com.sharma2464.mediacompression.data.FileKind
import com.sharma2464.mediacompression.scan.classifyFile
import com.sharma2464.mediacompression.scan.guessMimeType
import com.sharma2464.mediacompression.scan.isCompressibleMedia
import com.sharma2464.mediacompression.settings.AppSettings
import com.sharma2464.mediacompression.settings.CompressionMode
import com.sharma2464.mediacompression.settings.StorageMode
import com.sharma2464.mediacompression.ui.compress.CompressFlowPreview
import com.sharma2464.mediacompression.ui.compress.atticus.AtticusProgressScreen
import com.sharma2464.mediacompression.ui.compress.strengthFromJob
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

fun buildCompressPreviewState(
    context: android.content.Context,
    selected: Set<File>,
    strength: CompressionStrength = AppSettings(context).compressionStrength,
): CompressPreviewState {
    val settings = AppSettings(context)
    val expanded = expandSelectedFiles(selected).filter { file ->
        isCompressibleMedia(classifyFile(guessMimeType(file.name)))
    }
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
    val job = CompressJobSettings.DEFAULT
    val primaryVideo = items.firstOrNull { it.kind == FileKind.VIDEO }?.file
    val estimated = if (settings.compressionMode == CompressionMode.LOSSLESS_ONLY) {
        CompressionEstimator.estimatedBytesAfter(pairs, CompressionProfile.resolve(settings.compressionMode, strength))
    } else {
        CompressSettingsEstimator.estimatedBytesAfter(pairs, settings.compressionMode, job, primaryVideo)
    }
    val profile = CompressionProfile.resolve(settings.compressionMode, strength)
    val modeLabel = profile.batchModeLabel()
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

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(10f),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (stage) {
                CompressDialogStage.Preview -> {
                    val state = previewState ?: return@Surface
                    CompressFlowPreview(
                        state = state,
                        onClose = onDismissPreview,
                        onStart = { job ->
                            scope.launch {
                                val appSettings = AppSettings(context)
                                appSettings.sessionCompressJobSettings = job
                                val strength = strengthFromJob(job)
                                appSettings.sessionCompressionStrength = strength
                                if (appSettings.compressionMode == CompressionMode.ADAPTIVE) {
                                    appSettings.compressionStrength = strength
                                }
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
                        onBack = onMinimizeProgress,
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ProgressStage(
    batch: com.sharma2464.mediacompression.compress.CompressionBatch?,
    onBack: () -> Unit,
    onCancelConfirmed: () -> Unit,
) {
    val context = LocalContext.current
    var cancelArmed by remember { mutableStateOf(false) }
    LaunchedEffect(batch) {
        cancelArmed = false
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Compress") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Minimize")
                    }
                },
                actions = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimize")
                    }
                },
            )
        },
    ) { padding ->
        if (batch == null) {
            Column(Modifier.padding(padding).padding(20.dp)) {
                Text("Starting…", style = MaterialTheme.typography.bodyMedium)
            }
            return@Scaffold
        }
        val currentName = batch.files.getOrNull(batch.currentIndex)?.fileName
            ?: batch.files.lastOrNull { it.state != FileCompressionState.QUEUED }?.fileName
            ?: "Preparing…"
        val rateText = if (batch.rateBytesPerSec > 0) {
            "${Formatter.formatShortFileSize(context, batch.rateBytesPerSec)}/s"
        } else {
            "—"
        }
        val fraction = batchOverallFraction(batch)
        Column(Modifier.padding(padding)) {
            AtticusProgressScreen(
                title = "Compressing files",
                subtitle = "${batch.modeLabel} · $currentName · $rateText",
                progress = fraction,
                onCancel = {
                    if (!cancelArmed) {
                        cancelArmed = true
                    } else {
                        onCancelConfirmed()
                    }
                },
            )
            if (cancelArmed) {
                Text(
                    "Tap Cancel again to stop. Finished files are kept.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
    }
}
