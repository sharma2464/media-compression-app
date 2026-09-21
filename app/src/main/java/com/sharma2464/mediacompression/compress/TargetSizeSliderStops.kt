package com.sharma2464.mediacompression.compress

import com.sharma2464.mediacompression.settings.TargetSizePreset
import kotlin.math.abs

object TargetSizeSliderStops {
    fun build(
        originalMb: Float,
        minimumMb: Float,
        currentMb: Float,
        extraPresets: List<TargetSizePreset> = emptyList(),
    ): List<Float> {
        if (originalMb <= 0f) return listOf(currentMb.coerceAtLeast(0.5f))
        val floor = maxOf(minimumMb, 0.5f).coerceAtMost(originalMb)
        val candidates = mutableSetOf<Float>()
        candidates += floor
        candidates += currentMb.coerceIn(floor, originalMb)
        defaultCompressTargetSizePresets.forEach { candidates += it.sizeMb }
        extraPresets.forEach { candidates += it.sizeMb }
        candidates += originalMb
        return candidates
            .filter { it >= floor - 0.01f && it <= originalMb + 0.01f }
            .sorted()
            .distinctBy { roundForCompare(it) }
    }

    fun indexForMb(stops: List<Float>, mb: Float): Int {
        if (stops.isEmpty()) return 0
        var best = 0
        var bestDelta = Float.MAX_VALUE
        stops.forEachIndexed { i, stop ->
            val d = abs(stop - mb)
            if (d < bestDelta) {
                bestDelta = d
                best = i
            }
        }
        return best
    }

    fun mbAtIndex(stops: List<Float>, index: Int): Float =
        stops.getOrElse(index.coerceIn(0, stops.lastIndex.coerceAtLeast(0))) { stops.first() }

    private fun roundForCompare(mb: Float): Int = (mb * 100).toInt()
}
