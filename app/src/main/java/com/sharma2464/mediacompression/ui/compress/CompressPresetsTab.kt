package com.sharma2464.mediacompression.ui.compress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sharma2464.mediacompression.compress.CompressJobSettings
import com.sharma2464.mediacompression.compress.PlatformPreset
import com.sharma2464.mediacompression.compress.PresetTier

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompressPresetsTab(
    settings: CompressJobSettings,
    onChange: (CompressJobSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Quality", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PresetTier.entries.forEach { tier ->
                FilterChip(
                    selected = settings.presetTier == tier,
                    onClick = {
                        val slider = when (tier) {
                            PresetTier.HIGH -> 75
                            PresetTier.MEDIUM -> 50
                            PresetTier.LOW -> 25
                        }
                        onChange(settings.copy(presetTier = tier, qualitySlider = slider))
                    },
                    label = {
                        Text(
                            when (tier) {
                                PresetTier.HIGH -> "High"
                                PresetTier.MEDIUM -> "Medium"
                                PresetTier.LOW -> "Low"
                            },
                        )
                    },
                    modifier = Modifier.testTag("compress_preset_${tier.name.lowercase()}"),
                )
            }
        }

        Text("Target size (estimate)", style = MaterialTheme.typography.titleSmall)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = settings.platformTarget == null,
                onClick = { onChange(settings.copy(platformTarget = null)) },
                label = { Text("None") },
            )
            PlatformPreset.entries.forEach { platform ->
                FilterChip(
                    selected = settings.platformTarget == platform,
                    onClick = {
                        onChange(
                            settings.copy(
                                platformTarget = platform,
                                qualitySlider = minOf(settings.qualitySlider, 35),
                            ),
                        )
                    },
                    label = { Text("${platform.label} (${platform.hint})") },
                    modifier = Modifier.testTag("compress_platform_${platform.name.lowercase()}"),
                )
            }
        }
        Text(
            "Platform limits are approximate; output is an estimate.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
