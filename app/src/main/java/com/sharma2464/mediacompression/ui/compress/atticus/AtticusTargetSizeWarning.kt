// UI adapted from Josh Atticus Compressor (MIT): https://github.com/JoshAtticus/Compressor
package com.sharma2464.mediacompression.ui.compress.atticus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sharma2464.mediacompression.compress.CompressFlowActions
import com.sharma2464.mediacompression.compress.CompressFlowUiState
import com.sharma2464.mediacompression.compress.CompressJobSettings
import java.io.File

@Composable
fun AtticusTargetSizeWarning(
    ui: CompressFlowUiState,
    settings: CompressJobSettings,
    actions: CompressFlowActions,
    videoFile: File?,
) {
    var showDialog by remember { mutableStateOf(false) }
    val warningColor = Color(0xFFF2B233)
    OutlinedButton(
        onClick = { showDialog = true },
        modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp).padding(vertical = 4.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = warningColor.copy(0.14f), contentColor = warningColor),
        border = BorderStroke(1.dp, warningColor),
        shape = RoundedCornerShape(24.dp),
    ) {
        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = warningColor)
        Spacer(Modifier.width(8.dp))
        Text("Target may be too small for chosen quality", color = warningColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 2)
    }
    if (showDialog) {
        val suggested = ui.suggestedSettings(settings, com.sharma2464.mediacompression.compress.VideoMetadata(
            ui.originalWidth, ui.originalHeight, ui.durationMs, ui.originalBitrate, ui.originalFps,
        ), videoFile)
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Target size warning", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("The target size may be below the minimum for your current resolution, frame rate, or audio settings. Apply suggested adjustments?")
                    if (suggested.frameRate != settings.frameRate) {
                        Text("Frame rate → ${suggested.frameRate.name}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (suggested.resolution != settings.resolution) {
                        Text("Resolution → ${suggested.resolution.name}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    actions.acceptAllSuggestions(ui, videoFile)
                    showDialog = false
                }) { Text("Accept all") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDialog = false }) { Text("Close") }
            },
        )
    }
}
