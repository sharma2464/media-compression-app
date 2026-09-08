package com.sharma2464.tindercompression.ui

import android.net.Uri
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private const val PREVIEW_CHAR_LIMIT = 4000

@Composable
fun TextPreview(uri: Uri, modifier: Modifier = Modifier.height(320.dp)) {
    val context = LocalContext.current
    var text by remember(uri) { mutableStateOf("Loading…") }
    remember(uri) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                text = input.bufferedReader().readText().take(PREVIEW_CHAR_LIMIT)
            }
        }
        Unit
    }
    Text(text, modifier = modifier.verticalScroll(rememberScrollState()))
}
