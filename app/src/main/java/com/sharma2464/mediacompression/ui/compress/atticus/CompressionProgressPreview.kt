package com.sharma2464.mediacompression.ui.compress.atticus

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sharma2464.mediacompression.compress.CompressionPreviewFrames
import com.sharma2464.mediacompression.data.FileKind

@Composable
fun CompressionProgressPreview(
    fileUri: String?,
    fileKind: FileKind?,
    percent: Int,
    durationMs: Long?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var frameBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var lastLoadedPercent by remember { mutableStateOf(-1) }

    val isPhoto = CompressionPreviewFrames.isPhotoKind(fileKind)
    val previewUri = remember(fileUri) { CompressionPreviewFrames.uriForPreview(fileUri) }

    LaunchedEffect(fileUri, fileKind, percent, durationMs) {
        if (isPhoto || fileUri == null) {
            frameBitmap = null
            return@LaunchedEffect
        }
        val delta = kotlin.math.abs(percent - lastLoadedPercent)
        if (lastLoadedPercent >= 0 && delta < 2 && percent < 99) return@LaunchedEffect
        lastLoadedPercent = percent
        val bmp = CompressionPreviewFrames.loadVideoFrame(context, fileUri, percent, durationMs)
        frameBitmap = bmp
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                isPhoto && previewUri != null -> {
                    AsyncImage(
                        model = Uri.parse(previewUri),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                frameBitmap != null -> {
                    Image(
                        bitmap = frameBitmap!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                else -> {
                    Text(
                        "Preview…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
