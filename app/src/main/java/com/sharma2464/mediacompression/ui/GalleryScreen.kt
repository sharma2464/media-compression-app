package com.sharma2464.mediacompression.ui

import android.net.Uri
import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.sharma2464.mediacompression.data.Decision
import com.sharma2464.mediacompression.data.FileEntry
import com.sharma2464.mediacompression.data.FileKind

private enum class KindFilter(val label: String, val kinds: Set<FileKind>?) {
    ALL("All", null),
    PHOTOS("Photos", setOf(FileKind.PHOTO, FileKind.LIVE_PHOTO)),
    VIDEOS("Videos", setOf(FileKind.VIDEO)),
    PDFS("PDFs", setOf(FileKind.PDF)),
    MISC("Misc", setOf(FileKind.DOCUMENT, FileKind.TEXT, FileKind.OTHER)),
}

/**
 * Photos-app-style grid: kind tabs up top, square thumbnails with size/format/compressed
 * badges, tap to open full-screen (or the before/after [CompareScreen] once compressed),
 * long-press to multi-select with a floating bottom action bar. One grid covers browsing
 * and bulk compression both — no separate swipe or "completed" screens needed.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    entries: List<FileEntry>,
    rootTreeUri: Uri?,
    onCompress: (List<FileEntry>) -> Unit,
    onKeep: (List<FileEntry>) -> Unit,
    onPickAnotherFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var openEntry by remember { mutableStateOf<FileEntry?>(null) }
    val pagerState = rememberPagerState(pageCount = { KindFilter.entries.size })
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) { selected = emptySet() }

    val filtered = remember(entries, pagerState.currentPage) {
        KindFilter.entries[pagerState.currentPage].kinds?.let { kinds -> entries.filter { it.kind in kinds } } ?: entries
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = pagerState.currentPage, edgePadding = 12.dp) {
                KindFilter.entries.forEach { f ->
                    Tab(
                        selected = pagerState.currentPage == f.ordinal,
                        onClick = { scope.launch { pagerState.animateScrollToPage(f.ordinal) } },
                        text = { Text(f.label) },
                    )
                }
            }

            if (entries.isEmpty()) {
                EmptyGallery(
                    message = "No files reviewed yet. Pick a folder to get started.",
                    onPickAnotherFolder = onPickAnotherFolder,
                )
            } else {
                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                    val pageFiltered = remember(entries, page) {
                        KindFilter.entries[page].kinds?.let { kinds -> entries.filter { it.kind in kinds } } ?: entries
                    }
                    if (pageFiltered.isEmpty()) {
                        EmptyGallery(message = "Nothing here yet.", onPickAnotherFolder = null)
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(2.dp, 2.dp, 2.dp, 96.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(pageFiltered, key = { it.id }) { entry ->
                                GalleryCell(
                                    entry = entry,
                                    selected = entry.id in selected,
                                    selectionMode = selected.isNotEmpty(),
                                    onTap = {
                                        selected = when {
                                            selected.isEmpty() -> { openEntry = entry; selected }
                                            entry.id in selected -> selected - entry.id
                                            else -> selected + entry.id
                                        }
                                    },
                                    onLongPress = { selected = selected + entry.id },
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = selected.isNotEmpty(),
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            val selectedEntries = remember(selected, filtered) { filtered.filter { it.id in selected } }
            SelectionActionBar(
                count = selected.size,
                onCompress = { onCompress(selectedEntries); selected = emptySet() },
                onKeep = { onKeep(selectedEntries); selected = emptySet() },
                onCancel = { selected = emptySet() },
            )
        }

    }

    openEntry?.let { entry ->
        if (entry.decision == Decision.DONE && rootTreeUri != null) {
            CompareScreen(entry, rootTreeUri, onDismiss = { openEntry = null })
        } else {
            FullScreenPreview(entry, onDismiss = { openEntry = null })
        }
    }
}

@Composable
private fun EmptyGallery(message: String, onPickAnotherFolder: (() -> Unit)?) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
        onPickAnotherFolder?.let {
            OutlinedButton(onClick = it, modifier = Modifier.padding(top = 16.dp)) { Text("Choose a folder") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryCell(
    entry: FileEntry,
    selected: Boolean,
    selectionMode: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val context = LocalContext.current
    Box(
        Modifier
            .padding(1.dp)
            .aspectRatio(1f)
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
    ) {
        when (entry.kind) {
            FileKind.PHOTO, FileKind.LIVE_PHOTO -> AsyncImage(
                model = Uri.parse(entry.uri),
                contentDescription = entry.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            // Thumbnailing video frames or PDF pages for a grid cell needs a decoder this
            // app doesn't pull in yet (coil-video / PdfRenderer per cell) — glyph placeholder
            // until that's worth adding.
            else -> Column(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant).padding(6.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(kindGlyph(entry.kind), style = MaterialTheme.typography.headlineMedium)
                Text(entry.displayName, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(extensionOf(entry.displayName), color = Color.White, style = MaterialTheme.typography.labelSmall)
            Text(
                Formatter.formatShortFileSize(context, entry.compressedSizeBytes ?: entry.sizeBytes),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
            )
        }

        if (entry.decision == Decision.DONE) {
            Text(
                "✓",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF388E3C)),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
            )
        }

        if (selectionMode) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Text("✓", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun SelectionActionBar(count: Int, onCompress: () -> Unit, onKeep: () -> Unit, onCancel: () -> Unit) {
    Surface(
        tonalElevation = 8.dp,
        shadowElevation = 16.dp,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCancel, contentPadding = PaddingValues(8.dp)) { Text("✕") }
                Text("$count selected", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onKeep, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                    Text("Keep", maxLines = 1)
                }
                Button(onClick = onCompress, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                    Text("Compress", maxLines = 1)
                }
            }
        }
    }
}


@Composable
private fun FullScreenPreview(entry: FileEntry, onDismiss: () -> Unit) {
    var showDetails by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                TypedPreview(Uri.parse(entry.uri), entry.kind, entry.displayName, entry.mimeType, Modifier.fillMaxSize())

                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        entry.displayName,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(onClick = { showDetails = true }, shape = CircleShape, color = Color.White.copy(alpha = 0.15f)) {
                            Text("Info", color = Color.White, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp))
                        }
                        Surface(onClick = onDismiss, shape = CircleShape, color = Color.White, shadowElevation = 8.dp) {
                            Text(
                                "✕  Close",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
            }

            if (showDetails) DetailsSheet(entry, onDismiss = { showDetails = false })
        }
    }
}

private fun extensionOf(name: String): String = name.substringAfterLast('.', "").uppercase().ifEmpty { "FILE" }

private fun kindGlyph(kind: FileKind): String = when (kind) {
    FileKind.VIDEO -> "🎬"
    FileKind.PDF -> "📄"
    FileKind.DOCUMENT -> "📝"
    FileKind.TEXT -> "🗒️"
    else -> "📦"
}
