package com.sharma2464.mediacompression.ui.compress.atticus

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun CompareWipeCanvas(
    before: Bitmap,
    after: Bitmap,
    wipeFraction: Float,
    modifier: Modifier = Modifier,
) {
    val beforeImg = remember(before) { before.asImageBitmap() }
    val afterImg = remember(after) { after.asImageBitmap() }
    val wipe = wipeFraction.coerceIn(0.05f, 0.95f)
    Canvas(modifier) {
        drawCoverImage(afterImg)
        if (before !== after) {
            clipRect(right = size.width * wipe) {
                drawCoverImage(beforeImg)
            }
        }
        val dividerX = size.width * wipe
        drawLine(
            color = androidx.compose.ui.graphics.Color(0xFFB388FF),
            start = androidx.compose.ui.geometry.Offset(dividerX, 0f),
            end = androidx.compose.ui.geometry.Offset(dividerX, size.height),
            strokeWidth = 4f,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCoverImage(image: ImageBitmap) {
    val scale = max(size.width / image.width, size.height / image.height)
    val w = image.width * scale
    val h = image.height * scale
    val x = (size.width - w) / 2f
    val y = (size.height - h) / 2f
    drawImage(
        image,
        dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
    )
}
