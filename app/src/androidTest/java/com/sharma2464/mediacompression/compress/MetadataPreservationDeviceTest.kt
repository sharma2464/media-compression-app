package com.sharma2464.mediacompression.compress

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.media.MediaMetadataRetriever
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import com.sharma2464.mediacompression.settings.CompressionMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * On-device checks that compression keeps embedded dates and EXIF (gallery timeline / camera info).
 */
@RunWith(AndroidJUnit4::class)
class MetadataPreservationDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @LargeTest
    @Test
    fun videoCompression_preserves_creation_time_metadata() = runBlocking {
        val workDir = File(context.cacheDir, "metadata_video").apply { mkdirs() }
        val source = synthesizeMp4(workDir, CREATION_TIME_ISO)
        assertTrue(probeCreationTime(source)?.contains("2010") == true)

        val profile = CompressionProfile.resolve(CompressionMode.ADAPTIVE, CompressionStrength.BALANCED)
        val result = VideoCompressor(context).compress(source, profile, workDir) { }
        assertFalse(result.wasLossless)
        assertTrue(result.outputFile.length() > 0)

        val after = probeCreationTime(result.outputFile)
        assertTrue(
            "Embedded creation_time should remain 2010-06-15 after transcode, got: $after",
            after?.contains("2010-06-15") == true,
        )

        val retrieverDate = readRetrieverDate(result.outputFile)
        assertTrue(
            "MediaMetadataRetriever date should still reference 2010, got: $retrieverDate",
            retrieverDate?.contains("2010") == true,
        )
    }

    @Test
    fun videoCompression_restoreFilesystemTimestamps_matches_source() = runBlocking {
        val workDir = File(context.cacheDir, "metadata_mtime").apply { mkdirs() }
        val source = synthesizeMp4(workDir, CREATION_TIME_ISO)
        val expectedMtime = zoned2010().toInstant().toEpochMilli()
        source.setLastModified(expectedMtime)

        val profile = CompressionProfile.resolve(CompressionMode.ADAPTIVE, CompressionStrength.BALANCED)
        val result = VideoCompressor(context).compress(source, profile, workDir) { }
        MediaMetadataPreserver.restoreFilesystemTimestamps(source, result.outputFile)

        assertEquals(expectedMtime, result.outputFile.lastModified())
    }

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

    private fun synthesizeMp4(dir: File, creationTimeIso: String): File {
        val out = File(dir, "source_${System.nanoTime()}.mp4")
        out.delete()
        val command = arrayOf(
            "-y",
            "-f", "lavfi",
            "-i", "color=c=red:s=320x240:d=1",
            "-metadata", "creation_time=$creationTimeIso",
            "-c:v", "libx264",
            "-pix_fmt", "yuv420p",
            "-movflags", "+faststart",
            out.absolutePath,
        )
        val session = FFmpegKit.executeWithArguments(command)
        assertTrue(
            "Failed to synthesize test MP4: ${session.output}",
            ReturnCode.isSuccess(session.returnCode) && out.isFile && out.length() > 0,
        )
        return out
    }

    private fun probeCreationTime(mp4: File): String? {
        val session = FFprobeKit.execute(
            "-v error -show_entries format_tags=creation_time " +
                "-of default=noprint_wrappers=1:nokey=1 \"${mp4.absolutePath}\"",
        )
        return session.output?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun readRetrieverDate(mp4: File): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(mp4.path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
        } finally {
            retriever.release()
        }
    }

    private fun zoned2010() = ZonedDateTime.of(2010, 6, 15, 12, 0, 0, 0, ZoneOffset.UTC)

    private companion object {
        const val CREATION_TIME_ISO = "2010-06-15T12:00:00.000000Z"
        const val EXIF_DATETIME_2010 = "2010:06:15 12:00:00"
    }
}
