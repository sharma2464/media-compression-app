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
fun CompressionProgressFab(
    onOpenProgressDialog: () -> Unit,
    indeterminate: Boolean = false,
) {
    val batch by CompressionStatus.batch.collectAsState()
    val overallFraction = batch?.let { batchOverallFraction(it) } ?: 0f

    FloatingActionButton(onClick = onOpenProgressDialog) {
        Box(contentAlignment = Alignment.Center) {
            if (indeterminate) {
                CircularProgressIndicator(modifier = Modifier.size(40.dp))
            } else {
                CircularProgressIndicator(progress = { overallFraction }, modifier = Modifier.size(40.dp))
            }
            Icon(Icons.Default.Refresh, contentDescription = "Open compression progress")
        }
    }
}
