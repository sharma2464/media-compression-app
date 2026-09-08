package com.sharma2464.tindercompression.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** First-page thumbnail via the platform's native PdfRenderer — no library needed. */
@Composable
fun PdfPreview(uri: Uri, modifier: Modifier = Modifier.fillMaxWidth().height(320.dp)) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    remember(uri) {
        runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd: ParcelFileDescriptor ->
                PdfRenderer(pfd).use { renderer ->
                    renderer.openPage(0).use { page ->
                        val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap = bmp
                    }
                }
            }
        }
        Unit
    }
    bitmap?.let { Image(it.asImageBitmap(), contentDescription = "PDF page 1", modifier = modifier) }
        ?: Text("Rendering PDF…")
}
