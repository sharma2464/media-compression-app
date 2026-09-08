package com.sharma2464.mediacompression.ui

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sharma2464.mediacompression.data.FileEntry
import com.sharma2464.mediacompression.data.FileKind

private val DEFAULT_PREVIEW_MODIFIER = Modifier.fillMaxWidth().height(320.dp)

/** Dispatches to the right preview widget per [FileEntry.kind]. */
@Composable
fun FilePreview(entry: FileEntry, modifier: Modifier = DEFAULT_PREVIEW_MODIFIER) {
    TypedPreview(Uri.parse(entry.uri), entry.kind, entry.displayName, entry.mimeType, modifier)
}

/**
 * The same per-kind dispatch as [FilePreview], but keyed off a raw [uri] instead of a
 * [FileEntry] — lets the before/after compare view render an arbitrary backup file
 * (which has no DB row of its own) with the exact same widgets.
 */
@Composable
fun TypedPreview(uri: Uri, kind: FileKind, displayName: String, mimeType: String, modifier: Modifier = DEFAULT_PREVIEW_MODIFIER) {
    when (kind) {
        FileKind.PHOTO, FileKind.LIVE_PHOTO -> AsyncImage(model = uri, contentDescription = displayName, modifier = modifier)
        FileKind.VIDEO -> VideoPreview(uri, modifier)
        FileKind.PDF -> PdfPreview(uri, modifier)
        FileKind.TEXT -> TextPreview(uri, modifier)
        FileKind.DOCUMENT, FileKind.OTHER -> Text("No inline preview for $mimeType", modifier = modifier)
    }
}
