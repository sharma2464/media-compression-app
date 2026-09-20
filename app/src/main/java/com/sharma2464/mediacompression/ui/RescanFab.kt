package com.sharma2464.mediacompression.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RescanFab(
    isScanning: Boolean,
    onRescan: () -> Unit,
) {
    SmallFloatingActionButton(
        onClick = { if (!isScanning) onRescan() },
    ) {
        if (isScanning) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = "Rescan this folder")
        }
    }
}
