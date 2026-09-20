package com.sharma2464.mediacompression.ui.compress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sharma2464.mediacompression.compress.CompressJobSettings
import com.sharma2464.mediacompression.compress.FrameRateChoice
import com.sharma2464.mediacompression.compress.ResolutionChoice
import com.sharma2464.mediacompression.compress.VideoCodec
import com.sharma2464.mediacompression.compress.VideoMetadata

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompressVideoTab(
    settings: CompressJobSettings,
    videoMeta: VideoMetadata?,
    onChange: (CompressJobSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Quality", style = MaterialTheme.typography.titleSmall)
        RowLabels(left = "Less space", right = "Higher quality")
        Slider(
            value = settings.qualitySlider.toFloat(),
            onValueChange = { onChange(settings.copy(qualitySlider = it.toInt())) },
            valueRange = 0f..100f,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Encoding", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = settings.videoCodec == VideoCodec.H264,
                onClick = { onChange(settings.copy(videoCodec = VideoCodec.H264)) },
                label = { Text("H.264 (Compatible)") },
            )
            FilterChip(
                selected = settings.videoCodec == VideoCodec.H265,
                onClick = { onChange(settings.copy(videoCodec = VideoCodec.H265)) },
                label = { Text("H.265 (Efficient)") },
            )
        }

        if (videoMeta != null) {
            Text("Resolution", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                resolutionChip("Original (${videoMeta.width}×${videoMeta.height})", ResolutionChoice.ORIGINAL, settings, onChange)
                resolutionChip("1080p", ResolutionChoice.P1080, settings, onChange)
                resolutionChip("3/4", ResolutionChoice.THREE_QUARTERS, settings, onChange)
                resolutionChip("720p", ResolutionChoice.P720, settings, onChange)
                resolutionChip("540p", ResolutionChoice.P540, settings, onChange)
                resolutionChip("480p", ResolutionChoice.P480, settings, onChange)
                resolutionChip("1/4", ResolutionChoice.QUARTER, settings, onChange)
            }

            Text("Framerate", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val fpsLabel = videoMeta.frameRate?.let { "Original (${it.toInt()} fps)" } ?: "Original"
                fpsChip(fpsLabel, FrameRateChoice.ORIGINAL, settings, onChange)
                if ((videoMeta.frameRate ?: 0f) >= 55f) {
                    fpsChip("60", FrameRateChoice.FPS_60, settings, onChange)
                }
                fpsChip("30", FrameRateChoice.FPS_30, settings, onChange)
                fpsChip("24", FrameRateChoice.FPS_24, settings, onChange)
                fpsChip("15", FrameRateChoice.FPS_15, settings, onChange)
            }
        } else {
            Text(
                "No video in selection — video options apply when you compress videos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RowLabels(left: String, right: String) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(left, style = MaterialTheme.typography.labelSmall)
        Text(right, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun resolutionChip(
    label: String,
    choice: ResolutionChoice,
    settings: CompressJobSettings,
    onChange: (CompressJobSettings) -> Unit,
) {
    FilterChip(
        selected = settings.resolution == choice,
        onClick = { onChange(settings.copy(resolution = choice)) },
        label = { Text(label) },
    )
}

@Composable
private fun fpsChip(
    label: String,
    choice: FrameRateChoice,
    settings: CompressJobSettings,
    onChange: (CompressJobSettings) -> Unit,
) {
    FilterChip(
        selected = settings.frameRate == choice,
        onClick = { onChange(settings.copy(frameRate = choice)) },
        label = { Text(label) },
    )
}
