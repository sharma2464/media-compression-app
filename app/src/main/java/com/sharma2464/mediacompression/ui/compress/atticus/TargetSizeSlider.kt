package com.sharma2464.mediacompression.ui.compress.atticus

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sharma2464.mediacompression.compress.TargetSizeSliderStops
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetSizeSlider(
    stopsMb: List<Float>,
    selectedMb: Float,
    onPreview: (Float) -> Unit,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    if (stopsMb.isEmpty()) return
    val lastIndex = (stopsMb.size - 1).coerceAtLeast(0)
    var index by remember(stopsMb, selectedMb) {
        mutableIntStateOf(TargetSizeSliderStops.indexForMb(stopsMb, selectedMb))
    }
    var sliderPos by remember(index) { mutableFloatStateOf(index.toFloat()) }
    val interactionSource = remember { MutableInteractionSource() }
    val mb = TargetSizeSliderStops.mbAtIndex(stopsMb, index)
    val label = formatTargetMb(mb)

    Column(modifier.fillMaxWidth()) {
        Text(
            "Target size",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Slider(
            value = sliderPos,
            onValueChange = { v ->
                val i = v.roundToInt().coerceIn(0, lastIndex)
                sliderPos = i.toFloat()
                if (i != index) {
                    index = i
                    onPreview(TargetSizeSliderStops.mbAtIndex(stopsMb, i))
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            },
            onValueChangeFinished = {
                onCommit(TargetSizeSliderStops.mbAtIndex(stopsMb, index))
            },
            valueRange = 0f..lastIndex.toFloat(),
            steps = (lastIndex - 1).coerceAtLeast(0),
            interactionSource = interactionSource,
            thumb = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                    SliderDefaults.Thumb(interactionSource = interactionSource)
                }
            },
        )
        if (stopsMb.size >= 2) {
            Text(
                "${formatTargetMb(stopsMb.first())} → ${formatTargetMb(stopsMb.last())}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

fun formatTargetMb(mb: Float): String = when {
    mb >= 1024f -> String.format(Locale.US, "%.2f GB", mb / 1024f)
    mb < 1f -> String.format(Locale.US, "%.0f KB", mb * 1024f)
    else -> String.format(Locale.US, "%.1f MB", mb)
}
