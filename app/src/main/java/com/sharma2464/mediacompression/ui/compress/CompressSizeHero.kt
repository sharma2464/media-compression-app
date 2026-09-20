package com.sharma2464.mediacompression.ui.compress

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun CompressSizeHero(
    originalBytes: Long,
    estimatedBytes: Long,
    lossless: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val original = Formatter.formatShortFileSize(context, originalBytes)
    val estimated = if (lossless) original else Formatter.formatShortFileSize(context, estimatedBytes)
    val savings = if (lossless || originalBytes <= 0) {
        null
    } else {
        val pct = ((1.0 - estimatedBytes.toDouble() / originalBytes) * 100).toInt().coerceAtLeast(0)
        if (pct > 0) "−$pct%" else null
    }

    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .testTag("compress_hero"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Original", style = MaterialTheme.typography.labelMedium)
                Text(original, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("compress_hero_original"))
            }
            Text("vs", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (lossless) "After" else "Estimated", style = MaterialTheme.typography.labelMedium)
                Text(estimated, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("compress_hero_estimated"))
            }
        }
        if (lossless) {
            Text(
                "No size change (lossless)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else if (savings != null) {
            Text(
                savings,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
