package com.sharma2464.mediacompression.ui.compress

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MimeTypes
import com.sharma2464.mediacompression.compress.CompressFlowActions
import com.sharma2464.mediacompression.compress.initialCompressTargetMb
import com.sharma2464.mediacompression.compress.CompressFlowUiState
import com.sharma2464.mediacompression.compress.CompressJobSettings
import com.sharma2464.mediacompression.compress.CompressSettingsEstimator
import com.sharma2464.mediacompression.compress.CompressionPathResolver
import com.sharma2464.mediacompression.compress.CompressionStrength
import com.sharma2464.mediacompression.compress.PresetTier
import com.sharma2464.mediacompression.compress.VideoMetadataProbe
import com.sharma2464.mediacompression.data.FileKind
import com.sharma2464.mediacompression.settings.AppSettings
import com.sharma2464.mediacompression.settings.CompressionMode
import com.sharma2464.mediacompression.ui.CompressPreviewState
import com.sharma2464.mediacompression.ui.compress.atticus.AtticusConfigScreen

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CompressFlowPreview(
    state: CompressPreviewState,
    onClose: () -> Unit,
    onStart: (CompressJobSettings) -> Unit,
    onDestinationPicked: (Uri) -> Unit,
) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    var destinationPath by remember(state) { mutableStateOf(state.destinationPath) }
    var outputMenuExpanded by remember { mutableStateOf(false) }

    val pairs = remember(state.items) { state.items.map { it.file to it.kind } }
    val primaryVideo = remember(state.items) {
        state.items.firstOrNull { it.kind == FileKind.VIDEO }?.file
    }
    val primaryPhoto = remember(state.items) {
        state.items.firstOrNull {
            it.kind == FileKind.PHOTO || it.kind == FileKind.LIVE_PHOTO
        }?.file
    }
    val hasVideo = state.items.any { it.kind == FileKind.VIDEO }
    val sizingBytes = remember(state, primaryVideo, primaryPhoto) {
        primaryVideo?.length() ?: primaryPhoto?.length() ?: state.totalBytes
    }
    var jobSettings by remember(state.items, sizingBytes, hasVideo) {
        val sizeMb = sizingBytes / (1024f * 1024f).coerceAtLeast(0.01f)
        val ratio = if (hasVideo) {
            settings.defaultVideoConfig.defaultSizeRatio
        } else {
            0.4f
        }
        mutableStateOf(
            CompressJobSettings.DEFAULT.copy(
                targetSizeMb = initialCompressTargetMb(sizingBytes, hasVideo, ratio),
            ),
        )
    }
    val videoMeta = remember(primaryVideo) { primaryVideo?.let { VideoMetadataProbe.probe(it) } }
    val lossless = settings.compressionMode == CompressionMode.LOSSLESS_ONLY
    val estimated = remember(pairs, jobSettings, lossless, primaryVideo, primaryPhoto) {
        CompressSettingsEstimator.estimatedBytesAfter(
            pairs,
            settings.compressionMode,
            jobSettings,
            primaryVideo,
            primaryPhoto,
        )
    }
    val uiState = remember(state, jobSettings, videoMeta, primaryVideo, estimated, settings.showBitrate) {
        CompressFlowUiState.build(
            context = context,
            previewTotalBytes = sizingBytes,
            fileCount = state.items.size,
            batchLabel = state.locationPath,
            settings = jobSettings,
            meta = videoMeta,
            videoFile = primaryVideo,
            estimatedBytes = estimated,
            supportedCodecs = listOf(MimeTypes.VIDEO_H265, MimeTypes.VIDEO_H264),
            showBitrate = settings.showBitrate,
        )
    }
    val actions = remember(videoMeta, sizingBytes) {
        CompressFlowActions(
            appSettings = settings,
            originalSizeBytes = sizingBytes,
            current = { jobSettings },
            onChange = { jobSettings = it },
            meta = videoMeta,
        )
    }

    val pickTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            onDestinationPicked(uri)
            destinationPath = CompressionPathResolver.destinationDisplayPath(
                context,
                state.items.firstOrNull()?.file ?: state.selectedRoots.first(),
            )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Compress") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(onClick = { outputMenuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Output options")
                    }
                    DropdownMenu(expanded = outputMenuExpanded, onDismissRequest = { outputMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("Output", style = MaterialTheme.typography.labelMedium)
                                    Text(destinationPath, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                                    Text(state.storageLabel, style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            onClick = { },
                            enabled = false,
                        )
                        DropdownMenuItem(
                            text = { Text("Change folder") },
                            onClick = {
                                outputMenuExpanded = false
                                pickTree.launch(null)
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                "Original dates, EXIF, and camera metadata are preserved when possible.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .basicMarquee(),
            )
            if (lossless) {
                Text(
                    "Lossless mode — files are copied without re-encoding.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                androidx.compose.material3.Button(
                    onClick = { onStart(jobSettings) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .testTag("compress_start"),
                ) {
                    Text("Start compression")
                }
            } else {
                AtticusConfigScreen(
                    ui = uiState,
                    settings = jobSettings,
                    actions = actions,
                    hasVideo = hasVideo,
                    videoFile = primaryVideo,
                    onStart = { onStart(jobSettings) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

fun strengthFromJob(job: CompressJobSettings): CompressionStrength = when (job.presetTier) {
    PresetTier.HIGH -> CompressionStrength.FAST
    PresetTier.MEDIUM -> CompressionStrength.BALANCED
    PresetTier.LOW -> CompressionStrength.SMALL
}
