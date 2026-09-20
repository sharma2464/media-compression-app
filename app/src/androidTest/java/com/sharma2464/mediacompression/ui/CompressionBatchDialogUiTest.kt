package com.sharma2464.mediacompression.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CompressionBatchDialogUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun previewStage_shows_summary_and_start() {
        val preview = CompressPreviewState(
            items = listOf(
                CompressPreviewItem(File("/sdcard/largest_files/v.mp4"), "v.mp4", null, 1000L),
            ),
            totalBytes = 1000L,
            locationPath = "/sdcard/largest_files",
            destinationPath = "/sdcard/largest_files/COMPRESSED",
            estimatedAfterBytes = 550L,
            modeLabel = "Adaptive",
            storageLabel = "Compressed copy",
            selectedRoots = setOf(File("/sdcard/largest_files/v.mp4")),
        )

        composeRule.setContent {
            CompressionBatchDialog(
                visible = true,
                stage = CompressDialogStage.Preview,
                previewState = preview,
                onDismissPreview = {},
                onMinimizeProgress = {},
                onStageChange = {},
                onCloseAfterBatch = {},
            )
        }

        composeRule.onNodeWithText("Compress files").assertIsDisplayed()
        composeRule.onNodeWithText("Start").assertIsDisplayed()
        composeRule.onNodeWithTag("compress_start").assertIsDisplayed()
    }

    @Test
    fun previewStart_clickable() {
        var started = false
        val preview = CompressPreviewState(
            items = listOf(
                CompressPreviewItem(File("/sdcard/x.mp4"), "x.mp4", null, 1L),
            ),
            totalBytes = 1L,
            locationPath = "/sdcard",
            destinationPath = "/sdcard/COMPRESSED",
            estimatedAfterBytes = 1L,
            modeLabel = "Adaptive",
            storageLabel = "Compressed copy",
            selectedRoots = setOf(File("/sdcard/x.mp4")),
        )

        composeRule.setContent {
            CompressionBatchDialog(
                visible = true,
                stage = CompressDialogStage.Preview,
                previewState = preview,
                onDismissPreview = {},
                onMinimizeProgress = {},
                onStageChange = { if (it == CompressDialogStage.Progress) started = true },
                onCloseAfterBatch = {},
            )
        }

        composeRule.onNodeWithTag("compress_start").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { started }
    }
}
