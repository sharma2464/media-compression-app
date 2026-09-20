package com.sharma2464.mediacompression.ui.compress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sharma2464.mediacompression.compress.CompressJobSettings

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompressAudioTab(
    settings: CompressJobSettings,
    hasVideo: Boolean,
    onChange: (CompressJobSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (!hasVideo) {
            Text(
                "No video in selection — audio options apply to videos only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Remove audio", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = settings.removeAudio,
                onCheckedChange = { onChange(settings.copy(removeAudio = it)) },
            )
        }

        Text("Bitrate", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = settings.audioBitrateKbps == null && !settings.removeAudio,
                onClick = { onChange(settings.copy(audioBitrateKbps = null, removeAudio = false)) },
                label = { Text("Original") },
                enabled = !settings.removeAudio,
            )
            listOf(96, 64, 48).forEach { kbps ->
                FilterChip(
                    selected = settings.audioBitrateKbps == kbps,
                    onClick = { onChange(settings.copy(audioBitrateKbps = kbps, removeAudio = false)) },
                    label = { Text("${kbps}k") },
                    enabled = !settings.removeAudio,
                )
            }
        }

        Text("Volume", style = MaterialTheme.typography.titleSmall)
        Slider(
            value = settings.volumePercent.toFloat(),
            onValueChange = { onChange(settings.copy(volumePercent = it.toInt())) },
            valueRange = 0f..100f,
            enabled = !settings.removeAudio,
            modifier = Modifier.fillMaxWidth(),
        )
        if (settings.volumePercent != 100 && !settings.removeAudio) {
            Text(
                "Volume adjustment uses software encoding when needed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
