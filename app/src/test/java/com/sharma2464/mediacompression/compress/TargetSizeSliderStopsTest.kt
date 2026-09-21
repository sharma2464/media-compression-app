package com.sharma2464.mediacompression.compress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetSizeSliderStopsTest {

    @Test
    fun stops_are_sorted_and_include_bounds() {
        val stops = TargetSizeSliderStops.build(
            originalMb = 100f,
            minimumMb = 5f,
            currentMb = 25f,
        )
        assertTrue(stops.isNotEmpty())
        assertTrue(stops.first() >= 5f)
        assertEquals(100f, stops.last(), 0.01f)
        for (i in 1 until stops.size) {
            assertTrue(stops[i] >= stops[i - 1])
        }
    }

    @Test
    fun index_round_trips() {
        val stops = listOf(1f, 10f, 50f, 100f)
        assertEquals(1, TargetSizeSliderStops.indexForMb(stops, 9f))
        assertEquals(10f, TargetSizeSliderStops.mbAtIndex(stops, 1))
    }
}
