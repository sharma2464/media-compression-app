@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.sharma2464.mediacompression.settings.atticus

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sharma2464.mediacompression.compress.VideoCodecMime
import com.sharma2464.mediacompression.compress.VideoCodecMime.labelForMime
import com.sharma2464.mediacompression.settings.AppSettings
import com.sharma2464.mediacompression.settings.DefaultAudioConfig
import com.sharma2464.mediacompression.settings.DefaultVideoConfig
import com.sharma2464.mediacompression.settings.VideoEngine
import com.sharma2464.mediacompression.settings.FilenameBuilder
import com.sharma2464.mediacompression.settings.FilenameSegment
import com.sharma2464.mediacompression.settings.QualityPresetConfig
import com.sharma2464.mediacompression.settings.TargetSizePreset
import com.sharma2464.mediacompression.ui.compress.atticus.SelectionChip

@Composable
fun AtticusFilenameBuilderSection(settings: AppSettings) {
    var segments by remember { mutableStateOf(settings.filenameSegments) }
    fun persist(next: List<FilenameSegment>) {
        val normalized = FilenameSegment.normalize(next)
        segments = normalized
        settings.filenameSegments = normalized
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Filename builder", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Tap chips to add tokens. Tap × on a chip to remove. Edit prefix/suffix text inline.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 1.dp,
        ) {
            FlowRow(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                segments.forEachIndexed { index, segment ->
                    when (segment) {
                        is FilenameSegment.Text -> {
                            OutlinedTextField(
                                value = segment.value,
                                onValueChange = { text ->
                                    val updated = segments.toMutableList()
                                    updated[index] = FilenameSegment.Text(text)
                                    persist(updated)
                                },
                                placeholder = {
                                    Text(
                                        when {
                                            index == 0 -> "prefix…"
                                            index == segments.lastIndex -> "suffix…"
                                            else -> ""
                                        },
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.width(120.dp),
                            )
                        }
                        is FilenameSegment.Token -> {
                            val label = FilenameSegment.availableTokens
                                .firstOrNull { it.first == segment.key }?.second ?: segment.key
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                ) {
                                    Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    IconButton(
                                        onClick = {
                                            persist(segments.toMutableList().also { it.removeAt(index) })
                                        },
                                        modifier = Modifier.width(32.dp).height(32.dp),
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.padding(0.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Available chips", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        val used = segments.filterIsInstance<FilenameSegment.Token>().map { it.key }.toSet()
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            FilenameSegment.availableTokens.filterNot { used.contains(it.first) }.forEach { (key, label) ->
                SelectionChip(
                    selected = false,
                    onClick = {
                        persist(segments + FilenameSegment.Token(key))
                    },
                    label = label,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Preview: ${FilenameBuilder.preview(segments)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedButton(
            onClick = {
                settings.resetFilenameSegments()
                segments = settings.filenameSegments
            },
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Reset filename format") }
    }
}

@Composable
fun AtticusPresetsSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember(context) { AppSettings(context) }
    var best by remember { mutableStateOf(settings.bestQualityPreset) }
    var high by remember { mutableStateOf(settings.highQualityPreset) }
    var medium by remember { mutableStateOf(settings.mediumQualityPreset) }
    var low by remember { mutableStateOf(settings.lowQualityPreset) }
    var sizePresets by remember { mutableStateOf(settings.targetSizePresets) }
    var editingQuality by remember { mutableStateOf<String?>(null) }
    var editingSize by remember { mutableStateOf<TargetSizePreset?>(null) }
    var showAddSize by remember { mutableStateOf(false) }

    SettingsScaffold("Presets", onBack) {
        Text(
            "Quality presets",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
        )
        QualityPresetRow("Best", presetSummary(best)) { editingQuality = "Best" }
        QualityPresetRow("High", presetSummary(high)) { editingQuality = "High" }
        QualityPresetRow("Medium", presetSummary(medium)) { editingQuality = "Medium" }
        QualityPresetRow("Low", presetSummary(low)) { editingQuality = "Low" }
        OutlinedButton(
            onClick = {
                settings.resetQualityPresets()
                best = settings.bestQualityPreset
                high = settings.highQualityPreset
                medium = settings.mediumQualityPreset
                low = settings.lowQualityPreset
            },
            modifier = Modifier.padding(16.dp),
        ) { Text("Reset quality presets") }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Target size presets", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            IconButton(onClick = { showAddSize = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
        sizePresets.forEach { preset ->
            ListItem(
                headlineContent = { Text(preset.label) },
                supportingContent = { Text("${preset.sizeMb} MB") },
                trailingContent = {
                    Row {
                        IconButton(onClick = { editingSize = preset }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = {
                            settings.deleteTargetSizePreset(preset.id)
                            sizePresets = settings.targetSizePresets
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                },
            )
        }
        OutlinedButton(
            onClick = {
                settings.resetTargetSizePresets()
                sizePresets = settings.targetSizePresets
            },
            modifier = Modifier.padding(16.dp),
        ) { Text("Reset platform size presets") }
    }

    editingQuality?.let { name ->
        val config = when (name) {
            "Best" -> best
            "High" -> high
            "Medium" -> medium
            else -> low
        }
        EditQualityPresetDialog(
            title = name,
            config = config,
            onDismiss = { editingQuality = null },
            onSave = { updated ->
                when (name) {
                    "Best" -> { best = updated; settings.bestQualityPreset = updated }
                    "High" -> { high = updated; settings.highQualityPreset = updated }
                    "Medium" -> { medium = updated; settings.mediumQualityPreset = updated }
                    else -> { low = updated; settings.lowQualityPreset = updated }
                }
                editingQuality = null
            },
        )
    }
    if (showAddSize) {
        TargetSizePresetDialog(
            preset = null,
            onDismiss = { showAddSize = false },
            onSave = { label, mb ->
                settings.addTargetSizePreset(label, mb)
                sizePresets = settings.targetSizePresets
                showAddSize = false
            },
        )
    }
    editingSize?.let { preset ->
        TargetSizePresetDialog(
            preset = preset,
            onDismiss = { editingSize = null },
            onSave = { label, mb ->
                settings.updateTargetSizePreset(preset.id, label, mb)
                sizePresets = settings.targetSizePresets
                editingSize = null
            },
        )
    }
}

@Composable
fun AtticusVideoSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember(context) { AppSettings(context) }
    var cfg by remember { mutableStateOf(settings.defaultVideoConfig) }
    var engine by remember { mutableStateOf(settings.videoEngine) }
    val codecs = remember(settings.allCodecsEnabled) {
        VideoCodecMime.supportedCodecs(settings.allCodecsEnabled)
    }
    SettingsScaffold("Video", onBack) {
        Text(
            "Default video options",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(16.dp),
        )
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("Video encoder engine", fontWeight = FontWeight.Bold)
            Text(
                "Media3 keeps target-size MB planning. LightCompressor uses bitrate/resizer (H.264/H.265 only).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsChipRow(
                listOf(
                    VideoEngine.MEDIA3 to "Media3 (target size)",
                    VideoEngine.LIGHT_COMPRESSOR to "LightCompressor",
                ),
                selected = engine,
                onSelect = {
                    engine = it
                    settings.videoEngine = it
                },
            )
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("Default codec", fontWeight = FontWeight.Bold)
            SettingsChipRow(
                codecs.map { it to labelForMime(it) },
                selected = cfg.defaultCodecMime,
                onSelect = {
                    cfg = cfg.copy(defaultCodecMime = it)
                    settings.defaultVideoConfig = cfg
                },
            )
        }
        Column(Modifier.padding(16.dp)) {
            Text("Default resolution", fontWeight = FontWeight.Bold)
            SettingsChipRow(
                listOf(0 to "Original", 1080 to "1080p", 720 to "720p", 480 to "480p"),
                selected = cfg.defaultResolutionShortSide,
                onSelect = {
                    cfg = cfg.copy(defaultResolutionShortSide = it)
                    settings.defaultVideoConfig = cfg
                },
            )
        }
        Column(Modifier.padding(16.dp)) {
            Text("Default framerate", fontWeight = FontWeight.Bold)
            SettingsChipRow(
                listOf(0 to "Original", 60 to "60fps", 30 to "30fps"),
                selected = cfg.defaultFps,
                onSelect = {
                    cfg = cfg.copy(defaultFps = it)
                    settings.defaultVideoConfig = cfg
                },
            )
        }
        Column(Modifier.padding(16.dp)) {
            Text("Default target size ratio: ${"%.0f".format(cfg.defaultSizeRatio * 100)}%", fontWeight = FontWeight.Bold)
            Slider(
                value = cfg.defaultSizeRatio,
                onValueChange = {
                    cfg = cfg.copy(defaultSizeRatio = it)
                    settings.defaultVideoConfig = cfg
                },
                valueRange = 0.1f..0.9f,
            )
        }
        OutlinedButton(
            onClick = {
                settings.resetDefaultVideoConfig()
                cfg = settings.defaultVideoConfig
            },
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Reset video defaults")
        }
    }
}

@Composable
fun AtticusAudioSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember(context) { AppSettings(context) }
    var cfg by remember { mutableStateOf(settings.defaultAudioConfig) }
    SettingsScaffold("Audio", onBack) {
        Text(
            "Default audio options",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(16.dp),
        )
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("Default audio bitrate", fontWeight = FontWeight.Bold)
            SettingsChipRow(
                listOf(320_000 to "320k", 256_000 to "256k", 192_000 to "192k", 128_000 to "128k", 96_000 to "96k"),
                selected = cfg.defaultAudioBitrate,
                onSelect = {
                    cfg = cfg.copy(defaultAudioBitrate = it)
                    settings.defaultAudioConfig = cfg
                },
            )
        }
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
            Text("Default volume: ${cfg.defaultVolumePercent}%", fontWeight = FontWeight.Bold)
            Slider(
                value = cfg.defaultVolumePercent.toFloat(),
                onValueChange = {
                    cfg = cfg.copy(defaultVolumePercent = it.toInt())
                    settings.defaultAudioConfig = cfg
                },
                valueRange = 0f..200f,
            )
        }
        OutlinedButton(
            onClick = {
                settings.resetDefaultAudioConfig()
                cfg = settings.defaultAudioConfig
            },
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Text("Reset audio defaults")
        }
    }
}

private fun presetSummary(config: QualityPresetConfig): String {
    val res = if (config.resolutionShortSide == 0) "Original" else "${config.resolutionShortSide}p"
    val fps = if (config.targetFps == 0) "Original" else "${config.targetFps}fps"
    val ratio = "${(config.sizeRatio * 100).toInt()}%"
    val audio = "${config.audioBitrate / 1000}k"
    val name = config.label?.let { "$it · " } ?: ""
    return "${name}$res · $fps · $ratio · $audio"
}

@Composable
private fun QualityPresetRow(name: String, subtitle: String, onEdit: () -> Unit) {
    ListItem(
        modifier = Modifier.fillMaxWidth(),
        headlineContent = { Text(name, fontWeight = FontWeight.Bold) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
        },
    )
}

@Composable
private fun <T> SettingsChipRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            SelectionChip(selected = value == selected, onClick = { onSelect(value) }, label = label)
        }
    }
}

@Composable
private fun EditQualityPresetDialog(
    title: String,
    config: QualityPresetConfig,
    onDismiss: () -> Unit,
    onSave: (QualityPresetConfig) -> Unit,
) {
    var label by remember { mutableStateOf(config.label ?: title) }
    var res by remember { mutableIntStateOf(config.resolutionShortSide) }
    var fps by remember { mutableIntStateOf(config.targetFps) }
    var ratio by remember { mutableFloatStateOf(config.sizeRatio) }
    var audio by remember { mutableIntStateOf(config.audioBitrate) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$title preset", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(label, { label = it }, label = { Text("Label") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Text("Resolution", fontWeight = FontWeight.Bold)
                SettingsChipRow(listOf(0 to "Original", 1080 to "1080p", 720 to "720p", 480 to "480p"), res) { res = it }
                Text("Framerate", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                SettingsChipRow(listOf(0 to "Original", 60 to "60fps", 30 to "30fps"), fps) { fps = it }
                Text("Size ratio: ${(ratio * 100).toInt()}%", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                Slider(ratio, { ratio = it }, valueRange = 0.1f..0.9f)
                Text("Audio bitrate", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                SettingsChipRow(listOf(320_000 to "320k", 192_000 to "192k", 128_000 to "128k", 96_000 to "96k"), audio) { audio = it }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(config.copy(resolutionShortSide = res, targetFps = fps, sizeRatio = ratio, audioBitrate = audio, label = label.trim())) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TargetSizePresetDialog(
    preset: TargetSizePreset?,
    onDismiss: () -> Unit,
    onSave: (label: String, sizeMb: Float) -> Unit,
) {
    var label by remember { mutableStateOf(preset?.label ?: "") }
    var sizeText by remember { mutableStateOf(preset?.sizeMb?.toString() ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (preset == null) "Add size preset" else "Edit size preset") },
        text = {
            Column {
                OutlinedTextField(label, { label = it }, label = { Text("Label") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    sizeText,
                    { sizeText = it },
                    label = { Text("Size (MB)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val mb = sizeText.toFloatOrNull() ?: return@TextButton
                    if (label.isNotBlank()) onSave(label, mb)
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun SettingsScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        androidx.compose.material3.Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) { content() }
    }
}
