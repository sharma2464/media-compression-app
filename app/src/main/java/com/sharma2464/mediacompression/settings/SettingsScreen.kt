package com.sharma2464.mediacompression.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { AppSettings(context) }
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("Compression mode", style = MaterialTheme.typography.titleMedium)
        CompressionMode.entries.forEach { option ->
            Row(
                Modifier.fillMaxWidth().selectable(selected = mode == option) {
                    mode = option
                    settings.compressionMode = option
                },
            ) {
                RadioButton(selected = mode == option, onClick = { mode = option; settings.compressionMode = option })
                Text(
                    when (option) {
                        CompressionMode.LOSSLESS_ONLY -> "Lossless only (smaller, honest savings)"
                        CompressionMode.ADAPTIVE -> "Adaptive (targets ~90% smaller, may re-encode lossy)"
                    },
                )
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        Text("Where compressed files go", style = MaterialTheme.typography.titleMedium)
        StorageMode.entries.forEach { option ->
            Row(
                Modifier.fillMaxWidth().selectable(selected = storageMode == option) {
                    storageMode = option
                    settings.storageMode = option
                },
            ) {
                RadioButton(selected = storageMode == option, onClick = { storageMode = option; settings.storageMode = option })
                Text(
                    when (option) {
                        StorageMode.COMPRESSED_COPY -> "Keep originals — compressed files go in a COMPRESSED/ folder"
                        StorageMode.REPLACE_IN_PLACE -> "Replace originals in place — originals move to an ORIGINALS/ folder"
                    },
                )
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        Text("File types to review", style = MaterialTheme.typography.titleMedium)
        Text(
            "Turn off a type to skip it entirely — it won't be scanned, shown to swipe on, or compressed.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        FeatureGroup.entries.forEach { group ->
            val enabled = enabledKinds.containsAll(group.kinds)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(group.label, modifier = Modifier.padding(top = 12.dp))
                Switch(
                    checked = enabled,
                    onCheckedChange = { checked ->
                        enabledKinds = if (checked) enabledKinds + group.kinds else enabledKinds - group.kinds
                        settings.enabledKinds = enabledKinds
                    },
                )
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        Text("Destination folder", style = MaterialTheme.typography.titleMedium)
        Text(
            destinationUri?.let { DocumentFile.fromTreeUri(context, Uri.parse(it))?.name ?: it }
                ?: "Default: COMPRESSED/ folder next to your source folder",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickDestination.launch(null) }) { Text("Choose folder") }
            if (destinationUri != null) {
                TextButton(onClick = { settings.destinationTreeUri = null; destinationUri = null }) { Text("Reset to default") }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        Text("Rescan", style = MaterialTheme.typography.titleMedium)
        Text(
            "Re-walks your source folder to pick up files added or changed since the last scan.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 8.dp),
        )
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
        ) { Text(if (rescanning) "Rescanning…" else "Rescan now") }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        Text("Support this app", style = MaterialTheme.typography.titleMedium)
        Text(
            "Media Compression is free with no ads or tracking. If it's useful, you can buy me a coffee.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        OutlinedButton(
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/sobergiraffe")))
            },
        ) { Text("☕  Buy me a coffee") }
    }
}
