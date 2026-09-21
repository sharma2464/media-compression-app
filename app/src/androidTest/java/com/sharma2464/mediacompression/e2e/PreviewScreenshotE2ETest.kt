package com.sharma2464.mediacompression.e2e

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
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

/**
 * Captures timed screenshots during compress progress for visual / agent analysis.
 * Pull from device: adb pull /sdcard/Android/data/com.sharma2464.mediacompression/files/preview_captures
 */
@RunWith(AndroidJUnit4::class)
class PreviewScreenshotE2ETest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var device: UiDevice
    private lateinit var screenshotDir: File
    private var mainActivity: MainActivity? = null

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        assumeTrue(TestStorageAccess.ensureAllFilesAccess(device))
        assumeTrue(TestStorageAccess.canReadLargestFiles())
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        screenshotDir = File(context.getExternalFilesDir(null), "preview_captures").apply {
            deleteRecursively()
            mkdirs()
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(MainActivity.EXTRA_INITIAL_DIRECTORY, TestStorageAccess.largestFilesDir.absolutePath)
        }
        scenario = ActivityScenario.launch(intent)
        scenario.onActivity { mainActivity = it }
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
    }

    @LargeTest
    @Test
    fun capture_compress_preview_timeline() {
        composeRule.waitUntil(timeoutMillis = 180_000) {
            runCatching {
                composeRule.onNodeWithTag("select_$SMALLEST_VIDEO").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("select_$SMALLEST_VIDEO").performClick()
        composeRule.onNodeWithTag("selection_compress").performClick()
        composeRule.waitUntil(timeoutMillis = 120_000) {
            runCatching {
                composeRule.onNodeWithTag("compress_start").assertExists()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("compress_start").assertIsDisplayed()
        composeRule.onNodeWithTag("compress_start").performClick()
        composeRule.waitUntil(timeoutMillis = 120_000) {
            runCatching {
                composeRule.onNodeWithTag("compress_compare_preview").assertExists()
                true
            }.getOrDefault(false)
        }

        val previewBounds = previewRectOnScreen()
        capture("01_progress_start", previewBounds)

        repeat(12) { i ->
            Thread.sleep(3_500)
            if (i == 3 || i == 7) {
                dragDivider(previewBounds, if (i == 3) 0.25f else 0.75f)
                Thread.sleep(400)
            }
            capture("step_${(i + 2).toString().padStart(2, '0')}", previewBounds)
        }
    }

    private fun previewRectOnScreen(): Rect {
        val act = mainActivity ?: error("MainActivity not ready")
        val node = composeRule.onNodeWithTag("compress_compare_preview").fetchSemanticsNode()
        val b = node.boundsInRoot
        val loc = IntArray(2)
        act.window.decorView.getLocationOnScreen(loc)
        return Rect(
            b.left.toInt() + loc[0],
            b.top.toInt() + loc[1],
            b.right.toInt() + loc[0],
            b.bottom.toInt() + loc[1],
        )
    }

    private fun capture(name: String, previewBounds: Rect) {
        val file = File(screenshotDir, "$name.png")
        check(device.takeScreenshot(file)) { "takeScreenshot failed for $name" }
        cropPreviewHalfMetrics(file, previewBounds, name)
    }

    /** Writes a tiny metrics sidecar for automated diff (left vs right half of preview). */
    private fun cropPreviewHalfMetrics(full: File, bounds: Rect, name: String) {
        val bmp = android.graphics.BitmapFactory.decodeFile(full.absolutePath) ?: return
        val w = bounds.width().coerceIn(1, bmp.width)
        val h = bounds.height().coerceIn(1, bmp.height)
        val left = bounds.left.coerceIn(0, bmp.width - 1)
        val top = bounds.top.coerceIn(0, bmp.height - 1)
        if (left + w > bmp.width || top + h > bmp.height) return
        val crop = Bitmap.createBitmap(bmp, left, top, w, h)
        val mid = w / 2
        val leftCrop = Bitmap.createBitmap(crop, 0, 0, mid, h)
        val rightCrop = Bitmap.createBitmap(crop, mid, 0, w - mid, h)
        val leftHash = avgRgb(leftCrop)
        val rightHash = avgRgb(rightCrop)
        val metrics = File(screenshotDir, "$name.metrics.txt")
        metrics.writeText(
            buildString {
                appendLine("previewWxH=${w}x$h")
                appendLine("leftAvg=$leftHash")
                appendLine("rightAvg=$rightHash")
                appendLine("lumaDiff=${kotlin.math.abs(leftHash - rightHash)}")
            },
        )
        leftCrop.recycle()
        rightCrop.recycle()
        crop.recycle()
        bmp.recycle()
    }

    private fun avgRgb(b: Bitmap): Int {
        var r = 0L
        var g = 0L
        var bl = 0L
        val step = 8
        var n = 0
        for (y in 0 until b.height step step) {
            for (x in 0 until b.width step step) {
                val c = b.getPixel(x, y)
                r += android.graphics.Color.red(c)
                g += android.graphics.Color.green(c)
                bl += android.graphics.Color.blue(c)
                n++
            }
        }
        if (n == 0) return 0
        return ((r / n) + (g / n) + (bl / n)).toInt() / 3
    }

    private fun dragDivider(bounds: Rect, targetFraction: Float) {
        val y = bounds.centerY()
        val startX = bounds.left + (bounds.width() * 0.5f).toInt()
        val endX = bounds.left + (bounds.width() * targetFraction).toInt()
        device.swipe(startX, y, endX, y, 24)
    }

    companion object {
        private const val SMALLEST_VIDEO = "VID20260901164336.mp4"
    }
}
