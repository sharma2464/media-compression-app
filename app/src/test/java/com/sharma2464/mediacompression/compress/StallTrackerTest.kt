package com.sharma2464.mediacompression.compress

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StallTrackerTest {

    @Test
    fun `does not flag stall while progress keeps advancing`() {
        var clock = 0L
        val tracker = StallTracker(stallTimeoutMs = 1000, nowMs = { clock })

        for (progress in 0..99) {
            clock += 100 // less than the timeout between each advancing step
            assertFalse(tracker.onProgress(progress))
        }
    }

    @Test
    fun `flags stall once progress stops changing past the timeout`() {
        var clock = 0L
        val tracker = StallTracker(stallTimeoutMs = 1000, nowMs = { clock })

        assertFalse(tracker.onProgress(99))
        clock += 999
        assertFalse(tracker.onProgress(99)) // still within the timeout window
        clock += 2
        assertTrue(tracker.onProgress(99)) // now past it
    }

    @Test
    fun `a late progress change resets the stall clock`() {
        var clock = 0L
        val tracker = StallTracker(stallTimeoutMs = 1000, nowMs = { clock })

        assertFalse(tracker.onProgress(50))
        clock += 900
        assertFalse(tracker.onProgress(51)) // changed just before the timeout would fire
        clock += 900
        assertFalse(tracker.onProgress(51)) // clock reset by the change above
    }

    @Test
    fun `lastSeenProgress reflects the most recent reading`() {
        val tracker = StallTracker(stallTimeoutMs = 1000, nowMs = { 0L })
        tracker.onProgress(42)
        assertTrue(tracker.lastSeenProgress == 42)
    }
}
