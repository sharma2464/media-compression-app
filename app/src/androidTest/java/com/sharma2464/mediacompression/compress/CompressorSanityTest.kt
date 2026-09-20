package com.sharma2464.mediacompression.compress

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sharma2464.mediacompression.settings.CompressionMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CompressorSanityTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun photoCompressor_producesValidWebp() = runBlocking {
        val input = File(context.cacheDir, "test.webp").also {
            android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it.outputStream())
        }
        val profile = CompressionProfile.resolve(CompressionMode.LOSSLESS_ONLY, CompressionStrength.BALANCED)
        val result = PhotoCompressor().compress(input, profile, context.cacheDir)
        assertTrue(result.outputFile.exists() && result.outputFile.length() > 0)
        assertTrue(result.wasLossless)
    }
}
