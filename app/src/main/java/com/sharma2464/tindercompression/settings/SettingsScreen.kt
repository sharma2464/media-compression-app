package com.sharma2464.tindercompression.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    var mode by remember { mutableStateOf(settings.compressionMode) }

    Column(Modifier.padding(24.dp)) {
        Text("Compression mode")
        CompressionMode.entries.forEach { option ->
            Row(
                Modifier.selectable(selected = mode == option) {
                    mode = option
                    settings.compressionMode = option
                },
            ) {
                RadioButton(selected = mode == option, onClick = { mode = option; settings.compressionMode = option })
                Text(
                    when (option) {
                        CompressionMode.LOSSLESS_ONLY -> "Lossless only (smaller, honest savings)"
                        CompressionMode.ADAPTIVE -> "Adaptive (targets ~90% smaller, may re-encode lossy)"
                    },
                )
            }
        }
    }
}
