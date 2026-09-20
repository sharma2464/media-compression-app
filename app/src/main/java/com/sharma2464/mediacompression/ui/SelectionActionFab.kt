package com.sharma2464.mediacompression.ui

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun SelectionActionFab(
    selected: Set<File>,
    onCompressClick: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete selected?") },
            text = { Text("This removes ${selected.size} item(s) from your device. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDeleteConfirmed()
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    val authority = "${context.packageName}.fileprovider"
                    val files = selected.flatMap { entry: File ->
                        if (entry.isDirectory) {
                            entry.walk().filter { f -> f.isFile }.toList()
                        } else {
                            listOf(entry)
                        }
                    }.filter { f -> f.isFile && f.canRead() }
                    if (files.isEmpty()) return@IconButton
                    val uris = files.map { FileProvider.getUriForFile(context, authority, it) }
                    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "*/*"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        clipData = ClipData.newUri(context.contentResolver, "shared", uris.first()).apply {
                            uris.drop(1).forEach { addItem(ClipData.Item(it)) }
                        }
                    }
                    shareLauncher.launch(Intent.createChooser(intent, "Share files"))
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Share, contentDescription = "Share selected files")
            }
            VerticalDivider(
                modifier = Modifier.height(28.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            TextButton(
                onClick = onCompressClick,
                modifier = Modifier
                    .weight(1f)
                    .testTag("selection_compress"),
            ) {
                Text("Compress")
            }
            VerticalDivider(
                modifier = Modifier.height(28.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            IconButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete selected files")
            }
        }
    }
}
