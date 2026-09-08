package com.sharma2464.mediacompression.ui

import android.content.Context
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.documentfile.provider.DocumentFile
import com.sharma2464.mediacompression.data.FileEntry
import com.sharma2464.mediacompression.scan.FolderScanner
import com.sharma2464.mediacompression.scan.findByRelativePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Full-screen before/after comparison for a compressed [entry]: a drag-to-reveal slider
 * (original on the left, compressed on the right) over the two files rendered with the
 * same [TypedPreview] widgets used elsewhere — so video keeps its own play/pause
 * transport controls and PDFs/photos zoom, for free, without any format-specific code
 * here. Pinch-to-zoom/pan applies to both layers together via one shared graphicsLayer.
 */
private sealed interface BackupLoad {
    data object Loading : BackupLoad
    data object NotFound : BackupLoad
    data class Found(val uri: Uri) : BackupLoad
}

@Composable
fun CompareScreen(entry: FileEntry, rootTreeUri: Uri, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val load by produceState<BackupLoad>(initialValue = BackupLoad.Loading, entry.id) {
        value = withContext(Dispatchers.IO) {
            resolveBackupUri(context, rootTreeUri, entry)?.let { BackupLoad.Found(it) } ?: BackupLoad.NotFound
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            when (val state = load) {
                is BackupLoad.Found -> CompareContent(entry, state.uri, onDismiss)
                BackupLoad.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading original…")
                }
                BackupLoad.NotFound -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No backup found for this file.")
                }
            }
        }
    }
}

@Composable
private fun CompareContent(entry: FileEntry, originalUri: Uri, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var sliderFraction by remember { mutableStateOf(0.5f) }
    var scale by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    Column(Modifier.fillMaxSize()) {
        SizeHeaderRow(entry, context, onDismiss)

        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        panOffset += pan
                    }
                },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = panOffset.x
                        translationY = panOffset.y
                    },
            ) {
                // Compressed file: full-size background layer.
                TypedPreview(Uri.parse(entry.uri), entry.kind, entry.displayName, entry.mimeType, Modifier.fillMaxSize())

                // Original file: drawn full-size, then clipped to the left `sliderFraction`
                // of the width — this reveals rather than resizes, so what's visible on
                // each side is a pixel-for-pixel crop, not a squished re-fit.
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            clipRect(right = size.width * sliderFraction) { this@drawWithContent.drawContent() }
                        },
                ) {
                    TypedPreview(originalUri, entry.kind, entry.displayName, entry.mimeType, Modifier.fillMaxSize())
                }
            }

            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = maxWidth * sliderFraction)
                    .fillMaxHeight()
                    .width(2.dp)
                    .background(Color.White),
            )
        }

        Slider(
            value = sliderFraction,
            onValueChange = { sliderFraction = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Text(
            "Original ← slide → Compressed",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun SizeHeaderRow(entry: FileEntry, context: Context, onDismiss: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(Formatter.formatShortFileSize(context, entry.sizeBytes), fontWeight = FontWeight.Bold)
        TextButton(onClick = onDismiss) { Text("✕ Close") }
        Text(Formatter.formatShortFileSize(context, entry.compressedSizeBytes ?: entry.sizeBytes), fontWeight = FontWeight.Bold)
    }
}

private fun resolveBackupUri(context: Context, rootTreeUri: Uri, entry: FileEntry): Uri? {
    val path = entry.backupRelativePath ?: return null
    val root = DocumentFile.fromTreeUri(context, rootTreeUri) ?: return null
    val backupRoot = root.findFile(FolderScanner.backupDirName(root)) ?: return null
    return findByRelativePath(backupRoot, path)?.uri
}
