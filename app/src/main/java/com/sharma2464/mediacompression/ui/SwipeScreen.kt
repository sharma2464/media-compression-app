package com.sharma2464.mediacompression.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharma2464.mediacompression.data.FileEntry
import com.sharma2464.mediacompression.settings.CompressionMode
import kotlin.math.abs
import kotlin.math.max

enum class SwipeDirection { LEFT, RIGHT, UP, DOWN }

private const val SWIPE_THRESHOLD_PX = 300f
// Lower bar than the commit threshold so the hint appears early in the drag, not just at release.
private const val HINT_START_PX = 24f

/**
 * A single-card swipe surface. Left/right/up trigger [onSwiped] and the
 * caller advances to the next entry; down opens details without consuming the card.
 */
@Composable
fun SwipeScreen(
    entry: FileEntry?,
    preview: @Composable (FileEntry) -> Unit,
    onSwiped: (SwipeDirection) -> Unit,
    onShowDetails: (FileEntry) -> Unit,
    onPickAnotherFolder: () -> Unit = {},
    compressionMode: CompressionMode = CompressionMode.ADAPTIVE,
    completed: List<FileEntry> = emptyList(),
    onOpenCompare: (FileEntry) -> Unit = {},
) {
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (entry == null) {
                    EmptyQueueMessage(onPickAnotherFolder)
                } else {
                    var offset by remember(entry.id) { mutableStateOf(Offset.Zero) }
                    val animatedX by animateFloatAsState(offset.x, spring(stiffness = Spring.StiffnessMedium))
                    val animatedY by animateFloatAsState(offset.y, spring(stiffness = Spring.StiffnessMedium))

                    Card(
                        modifier = Modifier
                            .padding(24.dp)
                            .graphicsLayer { translationX = animatedX; translationY = animatedY }
                            .pointerInput(entry.id) {
                                detectDragGestures(
                                    onDrag = { change, drag -> change.consume(); offset += drag },
                                    onDragEnd = {
                                        val direction = resolveSwipe(offset)
                                        if (direction != null) {
                                            if (direction == SwipeDirection.DOWN) {
                                                onShowDetails(entry)
                                            } else {
                                                onSwiped(direction)
                                            }
                                        }
                                        offset = Offset.Zero
                                    },
                                )
                            },
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(entry.displayName, style = MaterialTheme.typography.titleMedium)
                            preview(entry)
                        }
                    }

                    hintDirection(offset)?.let { direction ->
                        val alpha = (max(abs(offset.x), abs(offset.y)) / SWIPE_THRESHOLD_PX).coerceIn(0f, 1f)
                        SwipeHint(direction, compressionMode, alpha)
                    }
                }
            }

            HorizontalDivider()
            Text(
                "Compressed (${completed.size})",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
            CompletedGallery(completed, onOpenCompare, modifier = Modifier.weight(0.8f).fillMaxWidth())
        }
    }
}

@Composable
private fun BoxScope.SwipeHint(direction: SwipeDirection, compressionMode: CompressionMode, alpha: Float) {
    val (label, color, alignment) = when (direction) {
        SwipeDirection.LEFT -> Triple("KEEP · not compressing", Color(0xFFD32F2F), Alignment.CenterStart)
        SwipeDirection.RIGHT -> Triple(
            if (compressionMode == CompressionMode.LOSSLESS_ONLY) "COMPRESS · lossless" else "COMPRESS · ~90% smaller",
            Color(0xFF388E3C),
            Alignment.CenterEnd,
        )
        SwipeDirection.UP -> Triple("LATER · back of queue", Color(0xFF1976D2), Alignment.TopCenter)
        SwipeDirection.DOWN -> Triple("DETAILS · file info", Color(0xFF7B1FA2), Alignment.BottomCenter)
    }
    Text(
        label,
        modifier = Modifier
            .align(alignment)
            .padding(32.dp)
            .graphicsLayer { this.alpha = alpha }
            .background(color, MaterialTheme.shapes.medium)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = Color.White,
        fontWeight = FontWeight.Bold,
    )
}

/** Same axis-dominance logic as [resolveSwipe] but with a much lower bar, so the hint shows early in the drag. */
private fun hintDirection(offset: Offset): SwipeDirection? {
    val (x, y) = offset
    if (abs(x) < HINT_START_PX && abs(y) < HINT_START_PX) return null
    return if (abs(x) > abs(y)) {
        if (x > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT
    } else {
        if (y > 0) SwipeDirection.DOWN else SwipeDirection.UP
    }
}

@Composable
private fun EmptyQueueMessage(onPickAnotherFolder: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("🎉", fontSize = 56.sp)
        Text(
            "All caught up!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "There's nothing left to review in this folder. Swipe-up files come back once you've been through everything else.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(onClick = onPickAnotherFolder, modifier = Modifier.padding(top = 12.dp)) {
            Text("Review another folder")
        }
    }
}

private fun resolveSwipe(offset: Offset): SwipeDirection? {
    val (x, y) = offset
    if (abs(x) < SWIPE_THRESHOLD_PX && abs(y) < SWIPE_THRESHOLD_PX) return null
    return if (abs(x) > abs(y)) {
        if (x > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT
    } else {
        if (y > 0) SwipeDirection.DOWN else SwipeDirection.UP
    }
}
