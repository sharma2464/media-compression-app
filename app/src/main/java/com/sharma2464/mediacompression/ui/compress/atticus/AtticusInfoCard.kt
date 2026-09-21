// UI adapted from Josh Atticus Compressor (MIT): https://github.com/JoshAtticus/Compressor
package com.sharma2464.mediacompression.ui.compress.atticus

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sharma2464.mediacompression.compress.CompressFlowUiState
import kotlin.math.max

@Composable
fun AtticusInfoCard(state: CompressFlowUiState, modifier: Modifier = Modifier) {
    val stackVertically = LocalDensity.current.fontScale >= 1.3f
    ElevatedCard(
        modifier = modifier.fillMaxWidth().testTag("compress_hero"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        if (stackVertically) {
            Column(Modifier.padding(20.dp)) {
                OriginalBlock(state, Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                Spacer(Modifier.height(16.dp))
                EstimatedBlock(state, Modifier.fillMaxWidth(), Alignment.Start)
            }
        } else {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                OriginalBlock(state, Modifier.weight(1f))
                Box(Modifier.height(40.dp).width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                EstimatedBlock(state, Modifier.weight(1f), Alignment.End)
            }
        }
        if (state.fileCount > 1) {
            Text(
                "${state.fileCount} files · ${state.batchLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp),
            )
        }
    }
}

@Composable
private fun OriginalBlock(state: CompressFlowUiState, modifier: Modifier) {
    Column(modifier) {
        Text("Original", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(state.formattedOriginalSize, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (state.originalWidth > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                "${state.originalWidth}×${state.originalHeight} • ${state.originalFps.toInt()}fps",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun EstimatedBlock(state: CompressFlowUiState, modifier: Modifier, align: Alignment.Horizontal) {
    Column(modifier, horizontalAlignment = align) {
        Text("Estimated", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        AnimatedContent(
            targetState = state.formattedEstimatedSize,
            transitionSpec = {
                slideInVertically { it / 2 } + fadeIn() togetherWith slideOutVertically { -it / 2 } + fadeOut()
            },
            label = "estimate",
        ) { text ->
            Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        val targetH = if (state.targetResolutionHeight > 0) state.targetResolutionHeight else state.originalHeight
        val targetW = if (state.originalHeight > 0) {
            (state.originalWidth.toFloat() / state.originalHeight * targetH).toInt()
        } else {
            0
        }
        val targetFps = if (state.targetFps > 0) state.targetFps else state.originalFps.toInt()
        if (targetW > 0) {
            Spacer(Modifier.height(4.dp))
            Text("${targetW}×${targetH} • ${targetFps}fps", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary.copy(0.8f))
        }
        val originalMb = state.originalSize / (1024f * 1024f)
        val estMb = state.estimatedSizeMb.coerceAtLeast(0.01f)
        if (originalMb > 0) {
            val pct = ((1f - estMb / originalMb) * 100f).toInt()
            Text(
                if (pct > 0) "−$pct%" else "+${-pct}%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (pct > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
    }
}
