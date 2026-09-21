@file:OptIn(ExperimentalMaterial3Api::class)

package com.sharma2464.mediacompression.settings.atticus

import android.content.Intent
import android.media.MediaCodecList
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.sharma2464.mediacompression.compress.VideoCodecMime
import com.sharma2464.mediacompression.settings.AppSettings
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
        SettingsDest.Presets -> AtticusPresetsSettingsScreen(onBack = { dest = SettingsDest.Hub })
        SettingsDest.Video -> AtticusVideoSettingsScreen(onBack = { dest = SettingsDest.Hub })
        SettingsDest.Audio -> AtticusAudioSettingsScreen(onBack = { dest = SettingsDest.Hub })
        SettingsDest.About -> AtticusAboutSettings(onBack = { dest = SettingsDest.Hub })
        SettingsDest.Legacy -> SettingsScreen(onThemeModeChange = onThemeModeChange)
    }
}

@Composable
private fun AtticusSettingsHub(
    onNavigate: (SettingsDest) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
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
            item { NavRow("Display", "Bitrate, chips, filename builder") { onNavigate(SettingsDest.Display) } }
            item { NavRow("Presets", "Quality & platform target sizes") { onNavigate(SettingsDest.Presets) } }
            item { NavRow("Video", "Default codec, resolution, FPS, size ratio") { onNavigate(SettingsDest.Video) } }
            item { NavRow("Audio", "Default bitrate, mute, volume") { onNavigate(SettingsDest.Audio) } }
            item { NavRow("About & codecs", "Device info, developer codecs") { onNavigate(SettingsDest.About) } }
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
        HorizontalDivider()
        AtticusFilenameBuilderSection(settings)
    }
}

@Composable
private fun AtticusAboutSettings(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    var codecTaps by remember { mutableIntStateOf(0) }
    var showUnlock by remember { mutableStateOf(false) }
    var checks by remember { mutableStateOf(List(5) { false }) }
    var allCodecsOn by remember { mutableStateOf(settings.allCodecsEnabled) }
    val codecs = remember(allCodecsOn) {
        if (allCodecsOn) VideoCodecMime.deviceVideoEncoders() else listDeviceVideoEncoders()
    }
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
                supportingContent = { Text("Developer mode — exposes every hardware video encoder in the compress UI") },
                trailingContent = {
                    Switch(allCodecsOn, onCheckedChange = {
                        allCodecsOn = it
                        settings.allCodecsEnabled = it
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
                            allCodecsOn = true
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
