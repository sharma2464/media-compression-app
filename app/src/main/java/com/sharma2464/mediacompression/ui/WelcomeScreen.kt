package com.sharma2464.mediacompression.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** First-run screen shown before a folder has been picked for review. */
@Composable
fun WelcomeScreen(
    onChooseFolder: () -> Unit,
    hasFullStorageAccess: Boolean = true,
    onGrantFullStorageAccess: () -> Unit = {},
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("🗜️", fontSize = 56.sp)
            Text(
                "Media Compression",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                "Swipe right to compress, left to keep, up to decide later, down for details. " +
                    "Originals are always backed up before anything changes.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            if (!hasFullStorageAccess) {
                Text(
                    "Grant full storage access so backups and timestamps stay reliable.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
                OutlinedButton(onClick = onGrantFullStorageAccess) {
                    Text("Grant full storage access")
                }
            }
            Button(onClick = onChooseFolder, modifier = Modifier.padding(top = 12.dp)) {
                Text("Choose a folder to review")
            }
        }
    }
}
