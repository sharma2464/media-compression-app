// UI adapted from Josh Atticus Compressor ResultScreen (MIT).
package com.sharma2464.mediacompression.ui.compress.atticus

import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sharma2464.mediacompression.compress.CompressionFinishedSummary
import com.sharma2464.mediacompression.ui.VideoPreview
import java.io.File

@Composable
fun AtticusCompleteScreen(
    summary: CompressionFinishedSummary,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val totalOriginal = summary.items.sumOf { it.originalBytes }
    val totalCompressed = summary.items.sumOf { it.compressedBytes }
    val previewItem = summary.items.firstOrNull { it.isVideo } ?: summary.items.firstOrNull()
    val reduction = if (totalOriginal > 0) {
        ((totalOriginal - totalCompressed).toFloat() / totalOriginal * 100).toInt()
    } else {
        0
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .widthIn(max = 600.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            "Compression complete",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "${Formatter.formatShortFileSize(context, totalOriginal)} → ${Formatter.formatShortFileSize(context, totalCompressed)}" +
                if (reduction > 0) " (−$reduction%)" else "",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (summary.items.size > 1) {
            Text(
                "${summary.items.size} files saved",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        previewItem?.let { item ->
            Spacer(Modifier.height(20.dp))
            if (item.isVideo && File(item.outputPath).isFile) {
                VideoPreview(
                    uri = Uri.fromFile(File(item.outputPath)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                )
            }
            Text(
                item.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
        ) {
            Text("Back to files", fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            shape = RoundedCornerShape(24.dp),
        ) {
            Text("Compress more")
        }
    }
}
