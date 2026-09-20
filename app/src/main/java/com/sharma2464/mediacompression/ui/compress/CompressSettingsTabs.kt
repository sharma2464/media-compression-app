package com.sharma2464.mediacompression.ui.compress

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sharma2464.mediacompression.compress.CompressJobSettings
import com.sharma2464.mediacompression.compress.VideoMetadata

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressSettingsTabs(
    settings: CompressJobSettings,
    videoMeta: VideoMetadata?,
    hasVideo: Boolean,
    lossless: Boolean,
    onChange: (CompressJobSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (lossless) return

    var tabIndex by remember { mutableIntStateOf(0) }
    val videoTabIndex = 1
    val audioTabIndex = 2

    Column(modifier) {
        PrimaryTabRow(selectedTabIndex = tabIndex) {
            Tab(
                selected = tabIndex == 0,
                onClick = { tabIndex = 0 },
                modifier = Modifier.testTag("compress_tab_presets"),
                text = { Text("Presets") },
            )
            Tab(
                selected = tabIndex == videoTabIndex,
                onClick = { if (hasVideo) tabIndex = videoTabIndex },
                enabled = hasVideo,
                text = { Text("Video") },
            )
            Tab(
                selected = tabIndex == audioTabIndex,
                onClick = { if (hasVideo) tabIndex = audioTabIndex },
                enabled = hasVideo,
                text = { Text("Audio") },
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
        ) {
            when (tabIndex) {
                0 -> CompressPresetsTab(settings, onChange)
                videoTabIndex -> CompressVideoTab(settings, videoMeta, onChange)
                audioTabIndex -> CompressAudioTab(settings, hasVideo, onChange)
            }
        }
    }
}
