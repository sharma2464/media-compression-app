package com.sharma2464.tindercompression.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharma2464.tindercompression.data.FileEntry
import kotlin.math.abs

enum class SwipeDirection { LEFT, RIGHT, UP, DOWN }

private const val SWIPE_THRESHOLD_PX = 300f

/**
 * A single-card Tinder-style swipe surface. Left/right/up trigger [onSwiped] and the
 * caller advances to the next entry; down opens details without consuming the card.
 */
@Composable
fun SwipeScreen(
    entry: FileEntry?,
    preview: @Composable (FileEntry) -> Unit,
    onSwiped: (SwipeDirection) -> Unit,
    onShowDetails: (FileEntry) -> Unit,
    onPickAnotherFolder: () -> Unit = {},
) {
    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            if (entry == null) {
                EmptyQueueMessage(onPickAnotherFolder)
                return@Box
            }
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
                    Text(entry.displayName, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    preview(entry)
                }
            }
        }
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
