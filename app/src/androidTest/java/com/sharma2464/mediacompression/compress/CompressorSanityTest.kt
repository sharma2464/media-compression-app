package com.sharma2464.mediacompression.compress

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sharma2464.mediacompression.settings.CompressionMode
import com.sharma2464.mediacompression.compress.CompressionProfile
import com.sharma2464.mediacompression.compress.CompressionStrength
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** One runnable check per compressor: output exists, is non-empty, and honesty flag is set. */
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

    @Test
    fun textCompressor_preservesContentAndExtension() = runBlocking {
        val inputDir = File(context.cacheDir, "text_in").apply { mkdirs() }
        val workDir = File(context.cacheDir, "text_out").apply { mkdirs() }
        val input = File(inputDir, "test.txt").also { it.writeText("hello world") }
        val profile = CompressionProfile.resolve(CompressionMode.LOSSLESS_ONLY, CompressionStrength.BALANCED)
        val result = TextCompressor().compress(input, profile, workDir)
        assertTrue(result.outputFile.readText() == "hello world")
        assertTrue(result.outputFile.name == input.name)
    }

    @Test
    fun zipRecompressor_preservesEntries() = runBlocking {
        val workDir = File(context.cacheDir, "zip_work").apply { mkdirs() }
        val input = File(workDir, "test.docx")
        java.util.zip.ZipOutputStream(input.outputStream()).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("word/document.xml"))
            zip.write("<xml>content</xml>".toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.finish()
        }
        val outDir = File(workDir, "out").apply { mkdirs() }
        val profile = CompressionProfile.resolve(CompressionMode.LOSSLESS_ONLY, CompressionStrength.BALANCED)
        val result = ZipRecompressor().compress(input, profile, outDir)
        java.util.zip.ZipFile(result.outputFile).use { zip ->
            assertTrue(zip.getEntry("word/document.xml") != null)
        }
    }
}
