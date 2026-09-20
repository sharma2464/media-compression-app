// UI adapted from Josh Atticus Compressor (MIT): https://github.com/JoshAtticus/Compressor
@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.sharma2464.mediacompression.ui.compress.atticus

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharma2464.mediacompression.compress.CompressFlowActions
import com.sharma2464.mediacompression.compress.CompressFlowUiState
import com.sharma2464.mediacompression.compress.CompressJobSettings
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AtticusConfigScreen(
    ui: CompressFlowUiState,
    settings: CompressJobSettings,
    actions: CompressFlowActions,
    hasVideo: Boolean,
    videoFile: File?,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pageCount = if (hasVideo) 3 else 1
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val tabs = listOf("Presets", "Video", "Audio")
    val originalMb = ui.originalSize / (1024f * 1024f)
    val actualEst = maxOf(ui.targetSizeMb, ui.minimumSizeMb)
    val isLarger = originalMb > 0 && actualEst > (originalMb + 0.01f)
    val largeFont = LocalDensity.current.fontScale >= 1.3f

    BoxWithConstraints(modifier.fillMaxSize()) {
        val split = maxWidth >= 600.dp && hasVideo
        if (split) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(Modifier.padding(top = 24.dp)) {
                    Spacer(Modifier.weight(1f))
                    NavigationRailItem(
                        selected = pagerState.currentPage == 0,
                        onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                        icon = { Icon(Icons.Outlined.BookmarkBorder, null) },
                        label = { Text("Presets", fontWeight = FontWeight.Bold) },
                        modifier = Modifier.testTag("compress_tab_presets"),
                    )
                    NavigationRailItem(
                        selected = pagerState.currentPage == 1,
                        onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                        icon = { Icon(Icons.Default.Movie, null) },
                        label = { Text("Video", fontWeight = FontWeight.Bold) },
                    )
                    NavigationRailItem(
                        selected = pagerState.currentPage == 2,
                        onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                        icon = { Icon(Icons.Default.MusicNote, null) },
                        label = { Text("Audio", fontWeight = FontWeight.Bold) },
                    )
                    Spacer(Modifier.weight(1f))
                }
                VerticalDivider(Modifier.fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    AtticusConfigBody(ui, settings, actions, hasVideo, videoFile, pagerState, isLarger, haptics, onStart)
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                AtticusInfoCard(ui, Modifier.padding(horizontal = if (largeFont) 16.dp else 24.dp, vertical = 12.dp))
                if (hasVideo) {
                    AtticusTabBar(tabs, pagerState, scope, haptics, largeFont)
                }
                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f), userScrollEnabled = hasVideo) { page ->
                    when (page) {
                        0 -> AtticusPresetsTab(ui, settings, actions)
                        1 -> if (hasVideo) AtticusVideoOptionsTab(ui, settings, actions) else AtticusPresetsTab(ui, settings, actions)
                        else -> AtticusAudioOptionsTab(ui, settings, actions)
                    }
                }
                AtticusStartSection(ui, settings, actions, videoFile, isLarger, haptics, onStart)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AtticusConfigBody(
    ui: CompressFlowUiState,
    settings: CompressJobSettings,
    actions: CompressFlowActions,
    hasVideo: Boolean,
    videoFile: File?,
    pagerState: androidx.compose.foundation.pager.PagerState,
    isLarger: Boolean,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onStart: () -> Unit,
) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        AtticusInfoCard(ui, Modifier.padding(horizontal = 24.dp, vertical = 24.dp))
        Box(Modifier.weight(1f)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), userScrollEnabled = false) { page ->
                when (page) {
                    0 -> AtticusPresetsTab(ui, settings, actions)
                    1 -> AtticusVideoOptionsTab(ui, settings, actions)
                    else -> AtticusAudioOptionsTab(ui, settings, actions)
                }
            }
        }
        AtticusStartSection(ui, settings, actions, videoFile, isLarger, haptics, onStart)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AtticusTabBar(
    tabs: List<String>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    scope: kotlinx.coroutines.CoroutineScope,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    cramped: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = if (cramped) RoundedCornerShape(50) else CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(0.4f)),
        shadowElevation = 1.dp,
    ) {
        Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            tabs.forEachIndexed { index, title ->
                val selected = pagerState.currentPage == index
                val icon = when (index) {
                    0 -> Icons.Outlined.BookmarkBorder
                    1 -> Icons.Default.Movie
                    else -> Icons.Default.MusicNote
                }
                Surface(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch { pagerState.animateScrollToPage(index) }
                    },
                    modifier = Modifier.weight(1f).then(if (index == 0) Modifier.testTag("compress_tab_presets") else Modifier),
                    shape = CircleShape,
                    color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, title, Modifier.size(18.dp))
                        if (!cramped) {
                            Spacer(Modifier.width(6.dp))
                            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AtticusStartSection(
    ui: CompressFlowUiState,
    settings: CompressJobSettings,
    actions: CompressFlowActions,
    videoFile: File?,
    isLarger: Boolean,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onStart: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (ui.targetSizeWarning) {
            AtticusTargetSizeWarning(ui, settings, actions, videoFile)
        }
        val interactionSource = remember { MutableInteractionSource() }
        Button(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onStart()
            },
            enabled = !isLarger,
            interactionSource = interactionSource,
            modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth().heightIn(min = 56.dp).expressiveScale(interactionSource).testTag("compress_start"),
            shape = RoundedCornerShape(24.dp),
        ) {
            Text("Start compression", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
