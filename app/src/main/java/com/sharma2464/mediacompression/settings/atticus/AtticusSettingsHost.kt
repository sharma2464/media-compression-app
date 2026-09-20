@file:OptIn(ExperimentalMaterial3Api::class)

package com.sharma2464.mediacompression.settings.atticus

import android.content.Intent
import android.media.MediaCodecList
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.mutableFloatStateOf
import com.sharma2464.mediacompression.settings.AppSettings
import com.sharma2464.mediacompression.settings.QualityPresetConfig
import com.sharma2464.mediacompression.settings.SettingsScreen
import com.sharma2464.mediacompression.settings.ThemeMode

private enum class SettingsDest {
    Hub, Display, Presets, Video, Audio, About, Legacy,
}

@Composable
fun AtticusSettingsHost(onThemeModeChange: (ThemeMode) -> Unit) {
    var dest by remember { mutableStateOf(SettingsDest.Hub) }
    BackHandler(enabled = dest != SettingsDest.Hub) { dest = SettingsDest.Hub }
    when (dest) {
        SettingsDest.Hub -> AtticusSettingsHub(onNavigate = { dest = it }, onThemeModeChange = onThemeModeChange)
        SettingsDest.Display -> AtticusDisplaySettings(onBack = { dest = SettingsDest.Hub })
        SettingsDest.Presets -> AtticusPresetsSettings(onBack = { dest = SettingsDest.Hub })
        SettingsDest.Video -> AtticusVideoDefaultsSettings(onBack = { dest = SettingsDest.Hub })
        SettingsDest.Audio -> AtticusAudioDefaultsSettings(onBack = { dest = SettingsDest.Hub })
        SettingsDest.About -> AtticusAboutSettings(onBack = { dest = SettingsDest.Hub })
        SettingsDest.Legacy -> SettingsScreen(onThemeModeChange = onThemeModeChange)
    }
}

@Composable
private fun AtticusSettingsHub(
    onNavigate: (SettingsDest) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clickable { onNavigate(SettingsDest.About) },
                    tonalElevation = 2.dp,
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Media Compression", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Compressor-style defaults & display", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { SectionTitle("Compressor") }
            item { NavRow("Display", "Bitrate labels, target-size chips") { onNavigate(SettingsDest.Display) } }
            item { NavRow("Presets", "High / Medium / Low & platform limits") { onNavigate(SettingsDest.Presets) } }
            item { NavRow("Video defaults", "Codec, resolution, FPS, size ratio") { onNavigate(SettingsDest.Video) } }
            item { NavRow("Audio defaults", "Bitrate, mute, volume") { onNavigate(SettingsDest.Audio) } }
            item { NavRow("About & codecs", "Device info, enable all codecs") { onNavigate(SettingsDest.About) } }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionTitle("App") }
            item { NavRow("Storage & compression", "Mode, output folder, file types") { onNavigate(SettingsDest.Legacy) } }
        }
    }
}

@Composable
private fun AtticusDisplaySettings(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember(context) { AppSettings(context) }
    var showBitrate by remember { mutableStateOf(settings.showBitrate) }
    var useMbps by remember { mutableStateOf(settings.useMbpsForBitrate) }
    var showChips by remember { mutableStateOf(settings.showTargetSizePresets) }
    SettingsScaffold("Display", onBack) {
        ListItem(
            headlineContent = { Text("Show bitrate") },
            trailingContent = {
                Switch(showBitrate, onCheckedChange = {
                    showBitrate = it
                    settings.showBitrate = it
                })
            },
        )
        if (showBitrate) {
            ListItem(
                headlineContent = { Text("Use Mbps") },
                trailingContent = {
                    Switch(useMbps, onCheckedChange = {
                        useMbps = it
                        settings.useMbpsForBitrate = it
                    })
                },
            )
        }
        ListItem(
            headlineContent = { Text("Show target-size chips") },
            supportingContent = { Text("Platform limits on the Presets tab") },
            trailingContent = {
                Switch(showChips, onCheckedChange = {
                    showChips = it
                    settings.showTargetSizePresets = it
                })
            },
        )
    }
}

@Composable
private fun AtticusPresetsSettings(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember(context) { AppSettings(context) }
    var high by remember { mutableStateOf(settings.highQualityPreset) }
    var medium by remember { mutableStateOf(settings.mediumQualityPreset) }
    var low by remember { mutableStateOf(settings.lowQualityPreset) }
    SettingsScaffold("Presets", onBack) {
        PresetEditor("High", high) {
            high = it
            settings.highQualityPreset = it
        }
        PresetEditor("Medium", medium) {
            medium = it
            settings.mediumQualityPreset = it
        }
        PresetEditor("Low", low) {
            low = it
            settings.lowQualityPreset = it
        }
        OutlinedButton(
            onClick = {
                settings.resetQualityPresets()
                high = settings.highQualityPreset
                medium = settings.mediumQualityPreset
                low = settings.lowQualityPreset
            },
            modifier = Modifier.padding(16.dp),
        ) { Text("Reset quality presets") }
        Text(
            "Platform target sizes: ${settings.targetSizePresets.size} presets (GitHub, Discord, …)",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(
            onClick = { settings.resetTargetSizePresets() },
            modifier = Modifier.padding(horizontal = 16.dp),
        ) { Text("Reset platform size presets") }
    }
}

@Composable
private fun PresetEditor(label: String, config: QualityPresetConfig, onChange: (QualityPresetConfig) -> Unit) {
    var ratio by remember(config) { mutableFloatStateOf(config.sizeRatio) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label, fontWeight = FontWeight.Bold)
        Text("Size ratio: ${"%.0f".format(ratio * 100)}% of original", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = ratio,
            onValueChange = {
                ratio = it
                onChange(config.copy(sizeRatio = it))
            },
            valueRange = 0.1f..0.9f,
        )
    }
}

@Composable
private fun AtticusVideoDefaultsSettings(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember(context) { AppSettings(context) }
    var cfg by remember { mutableStateOf(settings.defaultVideoConfig) }
    SettingsScaffold("Video defaults", onBack) {
        Text(
            "Applied when you open compress on a new video.",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodySmall,
        )
        Column(Modifier.padding(16.dp)) {
            Text("Default size ratio: ${"%.0f".format(cfg.defaultSizeRatio * 100)}%")
            Slider(
                value = cfg.defaultSizeRatio,
                onValueChange = {
                    cfg = cfg.copy(defaultSizeRatio = it)
                    settings.defaultVideoConfig = cfg
                },
                valueRange = 0.1f..0.9f,
            )
        }
    }
}

@Composable
private fun AtticusAudioDefaultsSettings(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember(context) { AppSettings(context) }
    var cfg by remember { mutableStateOf(settings.defaultAudioConfig) }
    SettingsScaffold("Audio defaults", onBack) {
        ListItem(
            headlineContent = { Text("Mute audio by default") },
            trailingContent = {
                Switch(cfg.defaultRemoveAudio, onCheckedChange = {
                    cfg = cfg.copy(defaultRemoveAudio = it)
                    settings.defaultAudioConfig = cfg
                })
            },
        )
        Column(Modifier.padding(16.dp)) {
            Text("Default volume: ${cfg.defaultVolumePercent}%")
            Slider(
                value = cfg.defaultVolumePercent.toFloat(),
                onValueChange = {
                    cfg = cfg.copy(defaultVolumePercent = it.toInt())
                    settings.defaultAudioConfig = cfg
                },
                valueRange = 0f..200f,
            )
        }
    }
}

@Composable
private fun AtticusAboutSettings(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    var codecTaps by remember { mutableIntStateOf(0) }
    var showUnlock by remember { mutableStateOf(false) }
    var checks by remember { mutableStateOf(List(5) { false }) }
    val codecs = remember { listDeviceVideoEncoders() }
    SettingsScaffold("About", onBack) {
        ListItem(
            headlineContent = { Text("Device") },
            supportingContent = {
                Text("${Build.MANUFACTURER} ${Build.MODEL} · API ${Build.VERSION.SDK_INT}")
            },
        )
        ListItem(
            modifier = Modifier.clickable {
                if (!settings.allCodecsUnlocked) codecTaps++
            },
            headlineContent = { Text("Supported codecs") },
            supportingContent = { Text(codecs.joinToString()) },
        )
        if (settings.allCodecsUnlocked) {
            ListItem(
                headlineContent = { Text("Enable all codecs") },
                supportingContent = { Text("Developer mode — may be unstable") },
                trailingContent = {
                    Switch(settings.allCodecsEnabled, onCheckedChange = {
                        if (it) settings.enableAllCodecsFeature() else settings.disableAllCodecsFeature()
                    })
                },
            )
        }
        ListItem(
            modifier = Modifier.clickable {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/JoshAtticus/Compressor")))
            },
            headlineContent = { Text("Josh Atticus Compressor (MIT)") },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
        )
    }
    if (codecTaps >= 7 && !settings.allCodecsUnlocked) {
        showUnlock = true
        codecTaps = 0
    }
    if (showUnlock) {
        AlertDialog(
            onDismissRequest = { showUnlock = false },
            title = { Text("Enable all codecs?") },
            text = {
                Column {
                    listOf(
                        "May drain battery faster",
                        "Device may heat up",
                        "Encoding may take longer",
                        "May crash on unsupported codecs",
                        "No bug reports for this mode",
                    ).forEachIndexed { i, label ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checks[i], onCheckedChange = { c ->
                                checks = checks.toMutableList().also { it[i] = c }
                            })
                            Text(label, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (checks.all { it }) {
                            settings.enableAllCodecsFeature()
                            showUnlock = false
                        }
                    },
                    enabled = checks.all { it },
                ) { Text("Enable") }
            },
            dismissButton = { TextButton(onClick = { showUnlock = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingsScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) { content() }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
    )
}

private fun listDeviceVideoEncoders(): List<String> {
    val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
    return list.codecInfos
        .filter { it.isEncoder }
        .flatMap { info -> info.supportedTypes.filter { it.startsWith("video/") } }
        .distinct()
        .sorted()
}
