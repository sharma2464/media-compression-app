package com.sharma2464.mediacompression.ui

import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sharma2464.mediacompression.data.FileEntry
import com.sharma2464.mediacompression.data.FileKind

/** Grid of already-compressed files; tapping one opens the before/after [CompareScreen]. */
@Composable
fun CompletedGallery(entries: List<FileEntry>, onOpenCompare: (FileEntry) -> Unit, modifier: Modifier = Modifier) {
    if (entries.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Compressed files will show up here", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)) {
        items(entries, key = { it.id }) { entry -> GalleryCell(entry, onOpenCompare) }
    }
}

@Composable
private fun GalleryCell(entry: FileEntry, onOpenCompare: (FileEntry) -> Unit) {
    val context = LocalContext.current
    Box(
        Modifier
            .padding(4.dp)
            .aspectRatio(1f)
            .clickable { onOpenCompare(entry) },
    ) {
        when (entry.kind) {
            FileKind.PHOTO, FileKind.LIVE_PHOTO -> AsyncImage(
                model = Uri.parse(entry.uri),
                contentDescription = entry.displayName,
                modifier = Modifier.fillMaxSize(),
            )
            // Rendering a live ExoPlayer/PdfRenderer per grid cell is wasteful for a thumbnail —
            // reuse the real preview widgets only in CompareScreen, where there's just one at a time.
            else -> Column(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant).padding(6.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(kindGlyph(entry.kind), style = MaterialTheme.typography.headlineMedium)
                Text(entry.displayName, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(
            Formatter.formatShortFileSize(context, entry.compressedSizeBytes ?: entry.sizeBytes),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(2.dp),
        )
    }
}

private fun kindGlyph(kind: FileKind): String = when (kind) {
    FileKind.VIDEO -> "🎬"
    FileKind.PDF -> "📄"
    FileKind.DOCUMENT -> "📝"
    FileKind.TEXT -> "🗒️"
    else -> "📦"
}
