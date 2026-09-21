package com.sharma2464.mediacompression.ui.compress.atticus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sharma2464.mediacompression.settings.AppSettings
import com.sharma2464.mediacompression.settings.VideoEngine

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoEngineSelector(
    appSettings: AppSettings,
    modifier: Modifier = Modifier,
) {
    var engine by remember { mutableStateOf(appSettings.videoEngine) }
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(
            "Encoder",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SelectionChip(
                selected = engine == VideoEngine.MEDIA3,
                onClick = {
                    engine = VideoEngine.MEDIA3
                    appSettings.videoEngine = VideoEngine.MEDIA3
                },
                label = "Media3",
            )
            SelectionChip(
                selected = engine == VideoEngine.LIGHT_COMPRESSOR,
                onClick = {
                    engine = VideoEngine.LIGHT_COMPRESSOR
                    appSettings.videoEngine = VideoEngine.LIGHT_COMPRESSOR
                },
                label = "LightCompressor",
            )
        }
        Text(
            "Media3 uses target-size planning; LightCompressor uses bitrate (H.264/H.265).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
