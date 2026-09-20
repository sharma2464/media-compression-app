package com.sharma2464.mediacompression.compress

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sharma2464.mediacompression.settings.CompressionMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MetadataPreservationDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun photoCompression_preserves_exif_datetime_and_camera_fields() = runBlocking {
        val workDir = File(context.cacheDir, "metadata_photo").apply { mkdirs() }
        val source = File(workDir, "source.jpg")
        Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).compress(
            CompressFormat.JPEG,
            95,
            source.outputStream(),
        )
        ExifInterface(source).apply {
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, EXIF_DATETIME_2010)
            setAttribute(ExifInterface.TAG_ARTIST, "Metadata Test Author")
            setAttribute(ExifInterface.TAG_MAKE, "Test Camera Co")
            setAttribute(ExifInterface.TAG_MODEL, "Model X")
            setAttribute(ExifInterface.TAG_LENS_MODEL, "Lens 24mm")
            saveAttributes()
        }

        val profile = CompressionProfile.resolve(CompressionMode.ADAPTIVE, CompressionStrength.BALANCED)
        val result = PhotoCompressor().compress(source, profile, workDir)
        assertTrue(result.outputFile.exists())

        val outExif = ExifInterface(result.outputFile)
        assertEquals(EXIF_DATETIME_2010, outExif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
        assertEquals("Metadata Test Author", outExif.getAttribute(ExifInterface.TAG_ARTIST))
        assertEquals("Test Camera Co", outExif.getAttribute(ExifInterface.TAG_MAKE))
        assertEquals("Model X", outExif.getAttribute(ExifInterface.TAG_MODEL))
        assertEquals("Lens 24mm", outExif.getAttribute(ExifInterface.TAG_LENS_MODEL))
    }

    private companion object {
        const val EXIF_DATETIME_2010 = "2010:06:15 12:00:00"
    }
}
