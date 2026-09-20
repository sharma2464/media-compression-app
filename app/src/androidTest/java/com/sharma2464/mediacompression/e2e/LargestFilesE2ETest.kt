package com.sharma2464.mediacompression.e2e

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.sharma2464.mediacompression.MainActivity
import com.sharma2464.mediacompression.TestStorageAccess
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * End-to-end compress flow on device using [largest_files] test data:
 * select video → preview dialog → start → progress UI.
 */
@RunWith(AndroidJUnit4::class)
class LargestFilesE2ETest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun grantStorageAndLaunch() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        assumeTrue(
            "Enable All files access for the app on this device",
            TestStorageAccess.ensureAllFilesAccess(device),
        )
        val sample = File(TestStorageAccess.largestFilesDir, SMALLEST_VIDEO)
        assumeTrue("Copy largest_files to /sdcard/largest_files on the device", sample.canRead())

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(
                MainActivity.EXTRA_INITIAL_DIRECTORY,
                TestStorageAccess.largestFilesDir.absolutePath,
            )
        }
        scenario = ActivityScenario.launch(intent)
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) {
            scenario.close()
        }
    }

    @LargeTest
    @Test
    fun browse_largest_files_compress_preview_and_start_progress() {
        composeRule.waitUntil(timeoutMillis = 180_000) {
            runCatching {
                composeRule.onNodeWithTag("select_$SMALLEST_VIDEO").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag("select_$SMALLEST_VIDEO").performClick()
        composeRule.onNodeWithTag("selection_compress").performClick()
        composeRule.onNodeWithText("Compress files").assertIsDisplayed()
        composeRule.onNodeWithTag("compress_start").performClick()
        composeRule.waitUntil(timeoutMillis = 30_000) {
            runCatching {
                composeRule.onNodeWithText("Compressing files").assertExists()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithText("Compressing files").assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 60_000) {
            runCatching {
                composeRule.onNodeWithText("Total files:", substring = true).assertExists()
                true
            }.getOrDefault(false)
        }

        composeRule.waitUntil(timeoutMillis = 120_000) {
            runCatching {
                composeRule.onNodeWithText("%", substring = true).assertExists()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("Double tap to cancel").performClick()
        composeRule.onNodeWithText("Tap again to cancel").performClick()
    }

    companion object {
        private const val SMALLEST_VIDEO = "VID20260901164336.mp4"
    }
}
