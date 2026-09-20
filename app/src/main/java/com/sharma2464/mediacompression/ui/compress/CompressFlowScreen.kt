package com.sharma2464.mediacompression.ui.compress

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sharma2464.mediacompression.compress.CompressJobSettings
import com.sharma2464.mediacompression.compress.CompressSettingsEstimator
import com.sharma2464.mediacompression.compress.CompressionPathResolver
import com.sharma2464.mediacompression.compress.CompressionStrength
import com.sharma2464.mediacompression.compress.VideoMetadataProbe
import com.sharma2464.mediacompression.data.FileKind
import com.sharma2464.mediacompression.settings.AppSettings
import com.sharma2464.mediacompression.settings.CompressionMode
import com.sharma2464.mediacompression.ui.CompressPreviewState

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
    var jobSettings by remember { mutableStateOf(CompressJobSettings.DEFAULT) }
    var destinationPath by remember(state) { mutableStateOf(state.destinationPath) }
    var outputMenuExpanded by remember { mutableStateOf(false) }

    val pairs = remember(state.items) { state.items.map { it.file to it.kind } }
    val primaryVideo = remember(state.items) {
        state.items.firstOrNull { it.kind == FileKind.VIDEO }?.file
    }
    val videoMeta = remember(primaryVideo) { primaryVideo?.let { VideoMetadataProbe.probe(it) } }
    val hasVideo = state.items.any { it.kind == FileKind.VIDEO }
    val lossless = settings.compressionMode == CompressionMode.LOSSLESS_ONLY
    val estimated = remember(pairs, jobSettings, lossless) {
        CompressSettingsEstimator.estimatedBytesAfter(
            pairs,
            settings.compressionMode,
            jobSettings,
            primaryVideo,
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
        bottomBar = {
            Button(
                onClick = { onStart(jobSettings) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("compress_start"),
            ) {
                Text("Start Compression")
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                "${state.items.size} files · ${Formatter.formatShortFileSize(context, state.totalBytes)}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Text(
                state.locationPath,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .basicMarquee(),
            )
            Text(
                "Original dates, location, and camera metadata are kept on compressed files.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            CompressSizeHero(
                originalBytes = state.totalBytes,
                estimatedBytes = estimated,
                lossless = lossless,
            )
            if (!lossless) {
                CompressSettingsTabs(
                    settings = jobSettings,
                    videoMeta = videoMeta,
                    hasVideo = hasVideo,
                    lossless = false,
                    onChange = { jobSettings = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

fun strengthFromJob(job: CompressJobSettings): CompressionStrength = when (job.presetTier) {
    com.sharma2464.mediacompression.compress.PresetTier.HIGH -> CompressionStrength.FAST
    com.sharma2464.mediacompression.compress.PresetTier.MEDIUM -> CompressionStrength.BALANCED
    com.sharma2464.mediacompression.compress.PresetTier.LOW -> CompressionStrength.SMALL
}
