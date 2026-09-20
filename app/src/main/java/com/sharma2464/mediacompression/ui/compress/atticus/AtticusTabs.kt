// UI adapted from Josh Atticus Compressor (MIT): https://github.com/JoshAtticus/Compressor
package com.sharma2464.mediacompression.ui.compress.atticus

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.MimeTypes
import com.sharma2464.mediacompression.compress.CompressFlowActions
import com.sharma2464.mediacompression.compress.CompressFlowUiState
import com.sharma2464.mediacompression.compress.CompressJobSettings
import com.sharma2464.mediacompression.compress.PresetTier
import com.sharma2464.mediacompression.settings.TargetSizePreset
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AtticusPresetsTab(
    ui: CompressFlowUiState,
    settings: CompressJobSettings,
    actions: CompressFlowActions,
    showTargetSizeChips: Boolean = true,
    targetSizePresets: List<TargetSizePreset> = TargetSizePreset.defaults,
) {
    val scrollState = rememberScrollState()
    val haptics = LocalHapticFeedback.current
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Text("Quality preset", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
        val presets = listOf(
            Triple(PresetTier.HIGH, "High", "Best quality, larger files"),
            Triple(PresetTier.MEDIUM, "Medium", "Balanced size and quality"),
            Triple(PresetTier.LOW, "Low", "Smallest files"),
        )
        presets.forEach { (tier, title, sub) ->
            val selected = settings.presetTier == tier
            val enabled = when (tier) {
                PresetTier.MEDIUM -> ui.originalHeight >= 1080 || ui.originalHeight == 0
                PresetTier.LOW -> ui.originalHeight >= 720 || ui.originalHeight == 0
                else -> true
            }
            val selectionScale by animateFloatAsState(if (selected) 1.02f else 1f, animationSpec = ExpressiveSpatialSpring, label = "sel")
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val pressScale by animateFloatAsState(if (isPressed) 0.96f else 1f, animationSpec = ExpressiveSpatialSpring, label = "press")
            OutlinedCard(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    actions.applyPreset(tier)
                },
                enabled = enabled,
                shape = RoundedCornerShape(24.dp),
                interactionSource = interactionSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .graphicsLayer { scaleX = selectionScale * pressScale; scaleY = selectionScale * pressScale }
                    .then(if (tier == PresetTier.MEDIUM) Modifier.testTag("compress_tab_presets") else Modifier),
                colors = if (selected) {
                    CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                } else {
                    CardDefaults.outlinedCardColors(
                        containerColor = if (enabled) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow.copy(0.3f),
                    )
                },
                border = if (selected) BorderStroke(0.dp, Color.Transparent) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (selected) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        val originalMb = ui.originalSize / (1024f * 1024f)
        val sizePresets = targetSizePresets.filter { it.sizeMb < originalMb || originalMb <= 0f }
        if (showTargetSizeChips && sizePresets.isNotEmpty()) {
            Text("Target size limits", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sizePresets.forEach { preset ->
                    val interactionSource = remember { MutableInteractionSource() }
                    FilterChip(
                        selected = settings.targetSizeMb == preset.sizeMb,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            actions.setTargetSize(preset.sizeMb)
                        },
                        interactionSource = interactionSource,
                        label = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val formatted = when {
                                    preset.sizeMb >= 1024f -> "${(preset.sizeMb / 1024f).toInt()} GB"
                                    preset.sizeMb < 1f -> "${(preset.sizeMb * 1024f).toInt()} KB"
                                    else -> "${preset.sizeMb.toInt()} MB"
                                }
                                Text(formatted, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Text(preset.label, style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        modifier = Modifier.heightIn(min = 48.dp).expressiveScale(interactionSource),
                    )
                }
            }
        }
    }
}

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AtticusVideoOptionsTab(
    ui: CompressFlowUiState,
    settings: CompressJobSettings,
    actions: CompressFlowActions,
) {
    val scrollState = rememberScrollState()
    val haptics = LocalHapticFeedback.current
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Text("Advanced options", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
        var sliderValue by remember { mutableFloatStateOf(settings.targetSizeMb) }
        var isUserInteracting by remember { mutableStateOf(false) }
        LaunchedEffect(settings.targetSizeMb) {
            if (!isUserInteracting) sliderValue = settings.targetSizeMb
        }
        LaunchedEffect(sliderValue) {
            if (isUserInteracting) {
                delay(150)
                actions.setTargetSizePreview(sliderValue)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Target size", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(
                if (sliderValue >= 1024) String.format(Locale.US, "%.2f GB", sliderValue / 1024f)
                else String.format(Locale.US, "%.1f MB", sliderValue),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        val originalMb = if (ui.originalSize > 0) ui.originalSize / (1024f * 1024f) else 100f
        val minSize = 0.5f
        val maxSize = maxOf(originalMb, sliderValue, settings.targetSizeMb, 1f)
        val sliderFraction = if (maxSize > minSize && sliderValue > 0f) {
            (kotlin.math.ln(sliderValue.coerceAtLeast(minSize) / minSize) / kotlin.math.ln(maxSize / minSize)).toFloat().coerceIn(0f, 1f)
        } else {
            0.5f
        }
        Slider(
            value = sliderFraction,
            onValueChange = { fraction ->
                isUserInteracting = true
                val calculated = minSize * kotlin.math.exp(fraction * kotlin.math.ln(maxSize / minSize)).toFloat()
                val rounded = when {
                    fraction >= 0.995f -> maxSize
                    calculated < 2.5f -> kotlin.math.round(calculated * 10f) / 10f
                    calculated < 10f -> kotlin.math.round(calculated * 2f) / 2f
                    calculated < 50f -> kotlin.math.round(calculated)
                    calculated < 200f -> kotlin.math.round(calculated / 5f) * 5f
                    else -> kotlin.math.round(calculated / 10f) * 10f
                }.coerceIn(minSize, maxSize)
                if (sliderValue != rounded) {
                    sliderValue = rounded
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            },
            onValueChangeFinished = {
                isUserInteracting = false
                actions.setTargetSize(sliderValue)
            },
            valueRange = 0f..1f,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Less space", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            Text("Balanced", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            Text("High quality", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        }
        Spacer(Modifier.height(16.dp))
        Text("Encoding", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ui.supportedCodecs.forEach { codec ->
                val label = when (codec) {
                    MimeTypes.VIDEO_H265 -> "H.265 (efficient)"
                    MimeTypes.VIDEO_H264 -> "H.264 (compatible)"
                    else -> codec.substringAfter("/").uppercase()
                }
                val selected = when (codec) {
                    MimeTypes.VIDEO_H264 -> settings.videoCodec == com.sharma2464.mediacompression.compress.VideoCodec.H264
                    else -> settings.videoCodec == com.sharma2464.mediacompression.compress.VideoCodec.H265
                }
                SelectionChip(selected = selected, onClick = { actions.setVideoCodec(codec) }, label = label)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Resolution", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        val isVertical = ui.originalHeight > ui.originalWidth
        val originalShort = min(ui.originalWidth, ui.originalHeight).coerceAtLeast(1)
        val currentShort = if (isVertical && ui.targetResolutionHeight > 0 && ui.originalHeight > 0) {
            (ui.targetResolutionHeight.toLong() * ui.originalWidth / ui.originalHeight).toInt()
        } else if (ui.targetResolutionHeight > 0) {
            ui.targetResolutionHeight
        } else {
            originalShort
        }
        val resOptions = remember(ui.originalWidth, ui.originalHeight) {
            val shortSide = min(ui.originalWidth, ui.originalHeight)
            val standard = listOf(2160, 1440, 1080, 720, 540, 480).filter { it <= shortSide }.map { it to "${it}p" }
            val fractions = listOf(
                (shortSide * 0.75).roundToInt() to "¾",
                (shortSide * 0.5).roundToInt() to "½",
                (shortSide * 0.25).roundToInt() to "¼",
            )
            (standard + fractions).filter { it.first > 0 }.sortedByDescending { it.first }.distinctBy { it.first }
        }
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectionChip(
                selected = currentShort >= originalShort,
                onClick = { actions.setResolution(originalShort) },
                label = "Original • ${originalShort}p",
            )
            resOptions.forEach { (res, label) ->
                SelectionChip(
                    selected = currentShort == res,
                    onClick = { actions.setResolution(res) },
                    label = label,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Framerate", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectionChip(selected = settings.frameRate == com.sharma2464.mediacompression.compress.FrameRateChoice.ORIGINAL, onClick = { actions.setFps(0) }, label = "Original • ${ui.originalFps.toInt()}")
            SelectionChip(selected = settings.frameRate == com.sharma2464.mediacompression.compress.FrameRateChoice.FPS_60, onClick = { actions.setFps(60) }, label = "60 fps", enabled = ui.originalFps >= 50f)
            SelectionChip(selected = settings.frameRate == com.sharma2464.mediacompression.compress.FrameRateChoice.FPS_30, onClick = { actions.setFps(30) }, label = "30 fps")
        }
    }
}

@Composable
fun AtticusAudioOptionsTab(
    ui: CompressFlowUiState,
    settings: CompressJobSettings,
    actions: CompressFlowActions,
) {
    val haptics = LocalHapticFeedback.current
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Text("Audio options", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Remove audio", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Switch(
                checked = settings.removeAudio,
                onCheckedChange = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    actions.toggleRemoveAudio()
                },
            )
        }
        AnimatedVisibility(visible = !settings.removeAudio) {
            Column(Modifier.padding(top = 16.dp)) {
                Text("Audio bitrate", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val effective = settings.audioBitrateKbps?.let { it * 1000 } ?: ui.originalAudioBitrate
                    SelectionChip(
                        selected = settings.audioBitrateKbps == null,
                        onClick = { actions.setAudioBitrate(0) },
                        label = "Original • ${ui.originalAudioBitrate / 1000}k",
                    )
                    listOf(320_000, 192_000, 128_000, 96_000, 64_000).forEach { rate ->
                        if (ui.originalAudioBitrate == 0 || rate <= ui.originalAudioBitrate) {
                            SelectionChip(
                                selected = effective == rate,
                                onClick = { actions.setAudioBitrate(rate) },
                                label = "${rate / 1000}k",
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Volume", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("${settings.volumePercent}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                var slider by remember(settings.volumePercent) { mutableFloatStateOf(settings.volumePercent / 100f) }
                Slider(
                    value = slider,
                    onValueChange = {
                        slider = it
                        actions.setAudioVolume(it)
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    valueRange = 0f..2f,
                    steps = 19,
                )
            }
        }
    }
}
