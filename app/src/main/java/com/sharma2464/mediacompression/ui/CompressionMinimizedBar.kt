package com.sharma2464.mediacompression.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sharma2464.mediacompression.compress.CompressionBatch

@Composable
fun CompressionMinimizedBar(
    batch: CompressionBatch?,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fraction = batch?.let { com.sharma2464.mediacompression.compress.batchOverallFraction(it) }
    val pct = ((fraction ?: 0f) * 100).toInt()
    val fileName = batch?.files?.getOrNull(batch.currentIndex)?.fileName
        ?: batch?.files?.firstOrNull { it.state == com.sharma2464.mediacompression.compress.FileCompressionState.IN_PROGRESS }?.fileName
    val subtitle = when {
        fileName != null -> fileName
        batch == null -> "Starting compression…"
        else -> "Preparing…"
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onExpand),
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Compressing",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    if (batch != null) "$pct%" else "…",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            )
            LinearProgressIndicator(
                progress = { fraction ?: 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Tap to open",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
