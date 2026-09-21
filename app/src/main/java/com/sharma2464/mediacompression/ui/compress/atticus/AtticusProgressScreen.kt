// UI adapted from Josh Atticus Compressor (MIT): https://github.com/JoshAtticus/Compressor
package com.sharma2464.mediacompression.ui.compress.atticus

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharma2464.mediacompression.compress.CompressJobSettings
import com.sharma2464.mediacompression.compress.CompressionPreviewFrames
import com.sharma2464.mediacompression.compress.VideoMetadata
import com.sharma2464.mediacompression.data.FileKind

@Composable
fun AtticusProgressScreen(
    title: String,
    progress: Float,
    modifier: Modifier = Modifier,
    fileUri: String? = null,
    sourceUri: String? = null,
    fileKind: FileKind? = null,
    durationMs: Long? = null,
    encodeOutputPath: String? = null,
    finishedOutputPath: String? = null,
    jobSettings: CompressJobSettings? = null,
    videoMeta: VideoMetadata? = null,
    settingsLines: List<String> = emptyList(),
    currentPercent: Int = 0,
    showCancelConfirmation: Boolean = false,
    cancelInProgress: Boolean = false,
    cancelResultMessage: String? = null,
    previewActive: Boolean = true,
    onRequestCancel: () -> Unit = {},
    onConfirmCancel: () -> Unit = {},
    onDeclineCancel: () -> Unit = {},
    onDismissCancelResult: () -> Unit = {},
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "progress",
    )
    val frameLabel = CompressionPreviewFrames.positionLabel(currentPercent, durationMs)

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .widthIn(max = 600.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        CompressionComparePreview(
            sourceUri = sourceUri ?: fileUri,
            fileKind = fileKind,
            livePercent = currentPercent,
            durationMs = durationMs,
            encodeOutputPath = encodeOutputPath,
            finishedOutputPath = finishedOutputPath,
            jobSettings = jobSettings,
            videoMeta = videoMeta,
            previewActive = previewActive,
        )
        if (frameLabel != null) {
            Text(
                frameLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    "Compressing…",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { animated },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    strokeCap = StrokeCap.Round,
                )
                Text(
                    "${(animated * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        if (settingsLines.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Settings for this compression",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    settingsLines.forEach { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        when {
            cancelResultMessage != null -> {
                Text(
                    cancelResultMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                Button(
                    onClick = onDismissCancelResult,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                ) {
                    Text("OK", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            cancelInProgress -> {
                Row(
                    Modifier.fillMaxWidth().height(56.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                    Text("Stopping compression…", fontWeight = FontWeight.Medium)
                }
            }
            showCancelConfirmation -> {
                Text(
                    "Stop compression? Files already finished are kept.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onDeclineCancel,
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                    ) {
                        Text("No", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onConfirmCancel,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .testTag("compress_cancel_yes"),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Text("Yes", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            else -> {
                Button(
                    onClick = onRequestCancel,
                    modifier = Modifier.fillMaxWidth().height(56.dp).testTag("compress_cancel"),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Text("Cancel", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
