@file:OptIn(ExperimentalMaterial3Api::class)

package com.sharma2464.mediacompression.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.sharma2464.mediacompression.scan.FolderScanner
import com.sharma2464.mediacompression.scan.ScanResult
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onThemeModeChange: (ThemeMode) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { AppSettings(context) }
    var themeMode by remember { mutableStateOf(settings.themeMode) }
    var mode by remember { mutableStateOf(settings.compressionMode) }
    var storageMode by remember { mutableStateOf(settings.storageMode) }
    var enabledKinds by remember { mutableStateOf(settings.enabledKinds) }
    var destinationUri by remember { mutableStateOf(settings.destinationTreeUri) }
    var rescanning by remember { mutableStateOf(false) }
    val pickDestination = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            settings.destinationTreeUri = it.toString()
            destinationUri = it.toString()
        }
    }

    val destinationSummary = destinationUri?.let { DocumentFile.fromTreeUri(context, Uri.parse(it))?.name ?: it }
        ?: "Default: COMPRESSED/ folder next to source files"

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item { SettingsCategory("Appearance") }
            item {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    ThemeMode.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = themeMode == option,
                            onClick = {
                                themeMode = option
                                settings.themeMode = option
                                onThemeModeChange(option)
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
                        ) {
                            Text(
                                when (option) {
                                    ThemeMode.SYSTEM -> "Auto"
                                    ThemeMode.LIGHT -> "Light"
                                    ThemeMode.DARK -> "Dark"
                                },
                            )
                        }
                    }
                }
            }

            item { SettingsCategory("Compression") }
            item {
                SettingsRadioRow(
                    headline = "Lossless only",
                    supporting = "Smaller files with honest savings; no lossy re-encoding.",
                    selected = mode == CompressionMode.LOSSLESS_ONLY,
                    onClick = {
                        mode = CompressionMode.LOSSLESS_ONLY
                        settings.compressionMode = mode
                    },
                )
            }
            item {
                SettingsRadioRow(
                    headline = "Adaptive",
                    supporting = "Targets much smaller files; may re-encode with some quality loss.",
                    selected = mode == CompressionMode.ADAPTIVE,
                    onClick = {
                        mode = CompressionMode.ADAPTIVE
                        settings.compressionMode = mode
                    },
                )
            }

            item { SettingsCategory("Output location") }
            item {
                SettingsRadioRow(
                    headline = "Compressed copy",
                    supporting = "Keep originals; compressed files go in a COMPRESSED/ folder.",
                    selected = storageMode == StorageMode.COMPRESSED_COPY,
                    onClick = {
                        storageMode = StorageMode.COMPRESSED_COPY
                        settings.storageMode = storageMode
                    },
                )
            }
            item {
                SettingsRadioRow(
                    headline = "Replace in place",
                    supporting = "Overwrite originals; backups go in an ORIGINALS/ folder.",
                    selected = storageMode == StorageMode.REPLACE_IN_PLACE,
                    onClick = {
                        storageMode = StorageMode.REPLACE_IN_PLACE
                        settings.storageMode = storageMode
                    },
                )
            }

            item { SettingsCategory("File types") }
            item {
                Text(
                    "Turn off a type to skip it in scans and compression.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            FeatureGroup.entries.forEach { group ->
                val enabled = enabledKinds.containsAll(group.kinds)
                item {
                    ListItem(
                        headlineContent = { Text(group.label) },
                        trailingContent = {
                            Switch(
                                checked = enabled,
                                onCheckedChange = { checked ->
                                    enabledKinds = if (checked) enabledKinds + group.kinds else enabledKinds - group.kinds
                                    settings.enabledKinds = enabledKinds
                                },
                            )
                        },
                    )
                }
            }

            item { SettingsCategory("Destination folder") }
            item {
                ListItem(
                    headlineContent = { Text("Compressed output") },
                    supportingContent = { Text(destinationSummary) },
                )
            }
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { pickDestination.launch(null) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Choose folder")
                    }
                    if (destinationUri != null) {
                        TextButton(
                            onClick = {
                                settings.destinationTreeUri = null
                                destinationUri = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Reset to default")
                        }
                    }
                }
            }

            item { SettingsCategory("Library") }
            item {
                Text(
                    "Re-walk your source folder for files added or changed since the last scan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            item {
                OutlinedButton(
                    enabled = !rescanning,
                    onClick = {
                        val rootUri = settings.rootTreeUri?.let(Uri::parse)
                        if (rootUri == null) {
                            Toast.makeText(context, "Pick a source folder first.", Toast.LENGTH_SHORT).show()
                            return@OutlinedButton
                        }
                        rescanning = true
                        scope.launch {
                            val result = FolderScanner(context).scan(rootUri)
                            rescanning = false
                            Toast.makeText(
                                context,
                                if (result == ScanResult.SUCCESS) "Rescan complete." else "Rescan failed: couldn't read the source folder.",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(if (rescanning) "Rescanning…" else "Rescan now")
                }
            }

            item { HorizontalDivider(Modifier.padding(top = 8.dp)) }
            item { SettingsCategory("Support") }
            item {
                ListItem(
                    headlineContent = { Text("Video encoding") },
                    supportingContent = {
                        Text("Video compression uses hardware Media3 encoding (H.264/H.265 when supported).")
                    },
                )
            }
            item {
                ListItem(
                    modifier = Modifier.clickable {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/sobergiraffe")),
                        )
                    },
                    headlineContent = { Text("Buy me a coffee") },
                    supportingContent = { Text("Free app, no ads or tracking.") },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open link")
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsCategory(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsRadioRow(
    headline: String,
    supporting: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(headline) },
        supportingContent = { Text(supporting) },
        trailingContent = {
            RadioButton(selected = selected, onClick = onClick)
        },
    )
}
