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
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.sharma2464.mediacompression.compress.CompressionWorker
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.sharma2464.mediacompression.compress.CompressJobSettings
import com.sharma2464.mediacompression.compress.CompressSettingsEstimator
import com.sharma2464.mediacompression.compress.CompressionEstimator
import com.sharma2464.mediacompression.compress.CompressionPathResolver
import com.sharma2464.mediacompression.compress.CompressionProfile
import com.sharma2464.mediacompression.compress.CompressionStrength
import com.sharma2464.mediacompression.compress.CompressJobSummary
import com.sharma2464.mediacompression.compress.CompressionStatus
import com.sharma2464.mediacompression.debug.DebugSessionLog
import com.sharma2464.mediacompression.compress.VideoMetadataProbe
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
import com.sharma2464.mediacompression.ui.compress.atticus.AtticusCompleteScreen
import com.sharma2464.mediacompression.ui.compress.atticus.AtticusProgressScreen
import com.sharma2464.mediacompression.ui.compress.strengthFromJob
import kotlinx.coroutines.launch
import java.io.File

enum class CompressDialogStage { Preview, Progress, Complete }

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
    val finished by CompressionStatus.finished.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(finished) {
        if (finished != null && stage == CompressDialogStage.Progress) {
            onStageChange(CompressDialogStage.Complete)
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
                        onCompressionStopped = onCloseAfterBatch,
                        onShowComplete = { onStageChange(CompressDialogStage.Complete) },
                    )
                }
                CompressDialogStage.Complete -> {
                    val summary = finished
                    if (summary != null && summary.items.isNotEmpty()) {
                        CompleteStage(
                            summary = summary,
                            onDone = {
                                CompressionStatus.dismissFinished()
                                onCloseAfterBatch()
                            },
                        )
                    } else {
                        LaunchedEffect(Unit) {
                            CompressionStatus.dismissFinished()
                            onCloseAfterBatch()
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompleteStage(
    summary: com.sharma2464.mediacompression.compress.CompressionFinishedSummary,
    onDone: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text("Compress") })
        },
    ) { padding ->
        AtticusCompleteScreen(
            summary = summary,
            onDone = onDone,
            modifier = Modifier.padding(padding),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ProgressStage(
    batch: com.sharma2464.mediacompression.compress.CompressionBatch?,
    onBack: () -> Unit,
    onCompressionStopped: () -> Unit,
    onShowComplete: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val finished by CompressionStatus.finished.collectAsState()
    var showCancelConfirmation by remember { mutableStateOf(false) }
    var cancelInProgress by remember { mutableStateOf(false) }
    var cancelResultMessage by remember { mutableStateOf<String?>(null) }
    var compressionWorkActive by remember { mutableStateOf(false) }
    LaunchedEffect(context) {
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(CompressionWorker.WORK_NAME)
            .collect { infos ->
                compressionWorkActive = infos.any {
                    it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
                }
            }
    }
    LaunchedEffect(batch?.currentIndex) {
        if (batch != null) {
            showCancelConfirmation = false
            if (cancelResultMessage == null) {
                cancelInProgress = false
            }
        }
    }
    var seenActiveCompression by remember { mutableStateOf(false) }
    LaunchedEffect(batch, compressionWorkActive) {
        if (batch != null || compressionWorkActive) {
            seenActiveCompression = true
        }
    }
    LaunchedEffect(batch, compressionWorkActive, seenActiveCompression, cancelResultMessage, cancelInProgress) {
        if (
            seenActiveCompression &&
            batch == null &&
            !compressionWorkActive &&
            cancelResultMessage == null &&
            !cancelInProgress
        ) {
            onCompressionStopped()
        }
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
        val fraction = batchOverallFraction(batch)
        val currentPct = batch.files.getOrNull(batch.currentIndex)?.percent ?: 0
        val phaseLabel = when {
            currentPct >= 85 && currentPct < 100 -> "Finalizing encode"
            fraction >= 0.9f && fraction < 0.995f -> "Saving"
            else -> null
        }
        val speedLabel = if (batch.rateBytesPerSec > 0) {
            val mbPerSec = batch.rateBytesPerSec / (1024.0 * 1024.0)
            String.format(java.util.Locale.US, "%.1f MB/s", mbPerSec)
        } else {
            "—"
        }
        val videoMeta = remember(batch.currentFileUri, batch.currentFileKind) {
            if (batch.currentFileKind == com.sharma2464.mediacompression.data.FileKind.VIDEO) {
                VideoMetadataProbe.probeUri(batch.currentFileUri)
            } else {
                null
            }
        }
        val currentFile = batch.files.getOrNull(batch.currentIndex)
            ?: batch.files.lastOrNull { it.state == FileCompressionState.IN_PROGRESS }
        val settingsLines = remember(
            batch.jobSettings,
            batch.modeLabel,
            videoMeta,
            batch.videoEngine,
            currentName,
            currentPct,
            fraction,
            speedLabel,
            phaseLabel,
        ) {
            CompressJobSummary.progressLines(
                currentFileName = currentName,
                filePercent = currentPct,
                batchPercent = (fraction * 100).toInt(),
                speedLabel = speedLabel,
                phaseLabel = phaseLabel,
            ) + CompressJobSummary.lines(
                batch.jobSettings,
                batch.modeLabel,
                videoMeta,
                batch.videoEngine,
            )
        }
        Column(Modifier.padding(padding)) {
            AtticusProgressScreen(
                title = "Compressing files",
                progress = fraction,
                fileUri = batch.currentFileUri,
                sourceUri = currentFile?.sourceUri ?: batch.currentFileUri,
                fileKind = batch.currentFileKind,
                durationMs = batch.currentDurationMs,
                encodeOutputPath = currentFile?.encodeOutputPath,
                finishedOutputPath = currentFile?.finishedOutputPath,
                jobSettings = batch.jobSettings,
                videoMeta = videoMeta,
                settingsLines = settingsLines,
                currentPercent = currentPct,
                showCancelConfirmation = showCancelConfirmation,
                cancelInProgress = cancelInProgress,
                cancelResultMessage = cancelResultMessage,
                previewActive = !cancelInProgress && cancelResultMessage == null,
                onRequestCancel = { showCancelConfirmation = true },
                onDeclineCancel = { showCancelConfirmation = false },
                onConfirmCancel = {
                    showCancelConfirmation = false
                    cancelInProgress = true
                    CompressionStatus.requestCancel()
                    WorkManager.getInstance(context).cancelUniqueWork(CompressionWorker.WORK_NAME)
                    scope.launch {
                        val wm = WorkManager.getInstance(context)
                        val infos = withTimeoutOrNull(15_000) {
                            wm.getWorkInfosForUniqueWorkFlow(CompressionWorker.WORK_NAME)
                                .filter { list ->
                                    val state = list.firstOrNull()?.state
                                    state == WorkInfo.State.CANCELLED ||
                                        state == WorkInfo.State.SUCCEEDED ||
                                        state == WorkInfo.State.FAILED ||
                                        list.isEmpty()
                                }
                                .first()
                        }
                        val state = infos?.firstOrNull()?.state
                        val hadCancelledFile = CompressionStatus.batch.value?.files?.any {
                            it.state == FileCompressionState.CANCELLED
                        } == true
                        cancelInProgress = false
                        // #region agent log
                        DebugSessionLog.log(
                            context,
                            "H2",
                            "CompressionBatchDialog.kt:onConfirmCancel",
                            "cancel_work_finished",
                            mapOf(
                                "workState" to (state?.name ?: "null"),
                                "hadCancelledFile" to hadCancelledFile,
                                "batchNull" to (CompressionStatus.batch.value == null),
                                "batchFraction" to (
                                    CompressionStatus.batch.value?.let { batchOverallFraction(it) } ?: -1f
                                ),
                            ),
                        )
                        // #endregion
                        cancelResultMessage = when {
                            state == WorkInfo.State.CANCELLED || hadCancelledFile ->
                                "Compression was cancelled. Any finished files were kept."
                            state == WorkInfo.State.SUCCEEDED ->
                                "Compression finished before it could be stopped."
                            state == WorkInfo.State.FAILED ->
                                "Compression stopped due to an error."
                            infos == null ->
                                "Stop requested. Compression may still be winding down."
                            else ->
                                "Compression is no longer running."
                        }
                    }
                },
                onDismissCancelResult = {
                    cancelResultMessage = null
                    CompressionStatus.clear()
                    if (finished != null) {
                        onShowComplete()
                    } else {
                        onCompressionStopped()
                    }
                },
            )
        }
    }
}
