package com.sharma2464.mediacompression.compress

import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformPresetsTest {

    @Test
    fun discord_cap_is_reasonable() {
        assertTrue(PlatformPreset.DISCORD.maxBytes in 5L * 1024 * 1024..30L * 1024 * 1024)
    }

    @Test
    fun all_presets_have_positive_caps() {
        PlatformPreset.entries.forEach { assertTrue(it.maxBytes > 0) }
    }
}
