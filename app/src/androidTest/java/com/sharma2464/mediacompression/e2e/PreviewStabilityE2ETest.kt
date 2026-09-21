package com.sharma2464.mediacompression.e2e

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
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

/** Holds compress progress ~40s so preview stability logs can be collected via logcat DBG_D66E0. */
@RunWith(AndroidJUnit4::class)
class PreviewStabilityE2ETest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("am force-stop com.sharma2464.mediacompression")
        assumeTrue(TestStorageAccess.ensureAllFilesAccess(device))
        val sample = File(TestStorageAccess.largestFilesDir, SMALLEST_VIDEO)
        assumeTrue(sample.canRead())
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(MainActivity.EXTRA_INITIAL_DIRECTORY, TestStorageAccess.largestFilesDir.absolutePath)
        }
        scenario = ActivityScenario.launch(intent)
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
    }

    @LargeTest
    @Test
    fun compress_preview_runs_without_cancel() {
        composeRule.waitUntil(timeoutMillis = 180_000) {
            runCatching {
                composeRule.onNodeWithTag("select_$SMALLEST_VIDEO").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("select_$SMALLEST_VIDEO").performClick()
        composeRule.onNodeWithTag("selection_compress").performClick()
        composeRule.waitUntil(timeoutMillis = 60_000) {
            runCatching {
                composeRule.onNodeWithTag("compress_hero").assertExists()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("compress_start").assertIsDisplayed()
        composeRule.onNodeWithTag("compress_start").performClick()
        composeRule.waitUntil(timeoutMillis = 120_000) {
            runCatching {
                composeRule.onNodeWithTag("compress_cancel").assertExists()
                true
            }.getOrDefault(false)
        }
        Thread.sleep(45_000)
    }

    companion object {
        private const val SMALLEST_VIDEO = "VID20260901164336.mp4"
    }
}
