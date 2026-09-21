package com.sharma2464.mediacompression.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sharma2464.mediacompression.compress.CompressFlowUiState
import com.sharma2464.mediacompression.compress.CompressJobSettings
import com.sharma2464.mediacompression.compress.CompressSettingsEstimator
import com.sharma2464.mediacompression.compress.PresetTier
import com.sharma2464.mediacompression.compress.VideoCodecMime
import com.sharma2464.mediacompression.data.FileKind
import com.sharma2464.mediacompression.settings.CompressionMode
import com.sharma2464.mediacompression.ui.compress.atticus.AtticusInfoCard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CompressEstimateUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun hero_shows_formatted_estimate_matching_estimator() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val tmp = File(context.cacheDir, "estimate_ui.bin")
        tmp.writeBytes(ByteArray(2_000_000))
        val settings = CompressJobSettings(targetSizeMb = 8f, presetTier = PresetTier.MEDIUM)
        val estimated = CompressSettingsEstimator.estimatedBytesAfter(
            listOf(tmp to FileKind.VIDEO),
            CompressionMode.ADAPTIVE,
            settings,
            primaryVideo = tmp,
        )
        val ui = CompressFlowUiState.build(
            context = context,
            previewTotalBytes = tmp.length(),
            fileCount = 1,
            batchLabel = "Test",
            settings = settings,
            meta = null,
            videoFile = tmp,
            estimatedBytes = estimated,
            supportedCodecs = VideoCodecMime.supportedCodecs(false),
        )
        composeRule.setContent {
            AtticusInfoCard(ui)
        }
        composeRule.onNodeWithText(ui.formattedEstimatedSize, substring = true).assertIsDisplayed()
        tmp.delete()
    }
}
