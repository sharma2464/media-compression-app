package com.sharma2464.mediacompression.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sharma2464.mediacompression.compress.CompressionStatus
import com.sharma2464.mediacompression.compress.batchOverallFraction

@Composable
fun CompressionProgressFab(onOpenProgressDialog: () -> Unit) {
    val batch by CompressionStatus.batch.collectAsState()
    val currentBatch = batch ?: return

    FloatingActionButton(onClick = onOpenProgressDialog) {
        val overallFraction = remember(currentBatch) {
            batchOverallFraction(currentBatch)
        }
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(progress = { overallFraction }, modifier = Modifier.size(40.dp))
            Icon(Icons.Default.Refresh, contentDescription = "Compression in progress")
        }
    }
}
