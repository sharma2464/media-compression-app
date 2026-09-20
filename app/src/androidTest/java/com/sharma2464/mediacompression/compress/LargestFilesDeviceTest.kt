package com.sharma2464.mediacompression.compress

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.sharma2464.mediacompression.TestStorageAccess
import org.junit.Before
import com.sharma2464.mediacompression.settings.CompressionMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import androidx.test.filters.LargeTest
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Verifies [largest_files] test data on device and runs a short transcode smoke test on the smallest video. */
@RunWith(AndroidJUnit4::class)
class LargestFilesDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun grantFullStorageIfPossible() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        assumeTrue(
            "Grant All files access so /sdcard/largest_files is readable",
            TestStorageAccess.ensureAllFilesAccess(device),
        )
    }

    @Test
    fun largest_files_directory_exists_with_expected_videos() {
        val smallest = File(TestStorageAccess.largestFilesDir, SMALLEST_VIDEO)
        assumeTrue("largest_files test video missing on device", smallest.isFile && smallest.canRead())
        assertTrue(smallest.length() > 0)
        EXPECTED_VIDEOS.forEach { name ->
            val f = File(TestStorageAccess.largestFilesDir, name)
            assertTrue("$name should exist under largest_files", f.isFile && f.canRead())
        }
    }

    @LargeTest
    @Test
    fun smallest_largest_file_compresses_to_non_empty_output() = runBlocking {
        val input = File(TestStorageAccess.largestFilesDir, SMALLEST_VIDEO)
        assumeTrue("Smallest test video missing", input.isFile && input.length() > 0)

        val workDir = File(context.cacheDir, "largest_files_test").apply { mkdirs() }
        val copy = File(workDir, input.name)
        input.inputStream().use { inp -> copy.outputStream().use { inp.copyTo(it) } }

        val result = VideoCompressor(context).compress(
            copy,
            CompressionMode.ADAPTIVE,
            workDir,
        ) { /* progress */ }

        assertTrue(result.outputFile.exists())
        assertTrue(result.outputFile.length() > 0)
        assertTrue(result.outputFile.length() < input.length())
    }

    companion object {
        private const val SMALLEST_VIDEO = "VID20260901164336.mp4"
        private val EXPECTED_VIDEOS = listOf(
            "VID20260715210637.mp4",
            "VID20260901160202.mp4",
            "VID20260901161314.mp4",
            "VID20260901161800.mp4",
            SMALLEST_VIDEO,
        )
    }
}
