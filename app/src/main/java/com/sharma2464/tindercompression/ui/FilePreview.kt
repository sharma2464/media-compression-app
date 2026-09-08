package com.sharma2464.tindercompression.ui

import android.net.Uri
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sharma2464.tindercompression.data.FileEntry
import com.sharma2464.tindercompression.data.FileKind

/** Dispatches to the right preview widget per [FileEntry.kind]. */
@Composable
fun FilePreview(entry: FileEntry) {
    when (entry.kind) {
        FileKind.PHOTO, FileKind.LIVE_PHOTO -> AsyncImage(
            model = Uri.parse(entry.uri),
            contentDescription = entry.displayName,
            modifier = Modifier.fillMaxWidth().height(320.dp),
        )
        FileKind.VIDEO -> VideoPreview(uri = Uri.parse(entry.uri))
        FileKind.PDF -> PdfPreview(uri = Uri.parse(entry.uri))
        FileKind.TEXT -> TextPreview(uri = Uri.parse(entry.uri))
        FileKind.DOCUMENT, FileKind.OTHER -> Text("No inline preview for ${entry.mimeType}")
    }
}
