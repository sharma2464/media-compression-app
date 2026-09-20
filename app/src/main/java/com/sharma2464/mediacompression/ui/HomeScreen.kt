package com.sharma2464.mediacompression.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sharma2464.mediacompression.data.FileKind
import com.sharma2464.mediacompression.scan.BrowsableVolume
import com.sharma2464.mediacompression.scan.listBrowsableVolumes
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SwapVert
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: FileBrowserViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val volumes = remember(context) { listBrowsableVolumes(context) }
    val pagerState = rememberPagerState(pageCount = { volumes.size })
    val scope = rememberCoroutineScope()

    val entries by viewModel.entries.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val currentPath by viewModel.currentPath.collectAsState()
    val sortField by viewModel.sortField.collectAsState()
    val sortAscending by viewModel.sortAscending.collectAsState()
    val showDotFiles by viewModel.showDotFiles.collectAsState()
    val showEmptyDirs by viewModel.showEmptyDirs.collectAsState()
    val loadState by viewModel.loadState.collectAsState()

    var showSortMenu by remember { mutableStateOf(false) }
    var showViewOptionsMenu by remember { mutableStateOf(false) }

    val currentVolume = volumes.getOrNull(pagerState.currentPage)
    val currentDir = currentVolume?.let { currentPath[it.label] ?: it.rootDir }
    val volLabel = currentVolume?.label
    val volRoot = currentVolume?.rootDir
    val canNavigateUp = volLabel != null && volRoot != null && currentDir != volRoot

    LaunchedEffect(pagerState.currentPage, currentDir?.absolutePath) {
        currentDir?.let {
            viewModel.loadDirectoryAt(it)
            viewModel.clearSelected()
        }
    }

    LaunchedEffect(currentVolume?.label, currentVolume?.rootDir) {
        val vol = currentVolume
        if (vol != null) {
            viewModel.setActiveVolume(vol.label, vol.rootDir)
        }
    }

    val listState = rememberLazyListState()

    Column(modifier = modifier.fillMaxSize()) {
        // Top app bar
        TopAppBar(
            navigationIcon = {
                if (canNavigateUp && volLabel != null && volRoot != null) {
                    IconButton(onClick = { viewModel.navigateUp(volLabel, volRoot) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Navigate up")
                    }
                }
            },
            title = {
                Column {
                    val titleText = when {
                        currentDir == null -> currentVolume?.label ?: "Files"
                        currentDir.name.isNotEmpty() -> currentDir.name
                        else -> currentVolume?.label ?: "Files"
                    }
                    Text(
                        titleText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (canNavigateUp && currentVolume != null) {
                        Text(
                            currentVolume.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            actions = {
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Default.SwapVert, contentDescription = "Sort")
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        SortField.entries.forEach { field ->
                            DropdownMenuItem(
                                text = { Text(field.displayLabel()) },
                                leadingIcon = {
                                    if (sortField == field) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                },
                                onClick = {
                                    viewModel.setSortField(field)
                                    showSortMenu = false
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Text(if (sortAscending) "Ascending" else "Descending")
                            },
                            leadingIcon = {
                                Icon(
                                    if (sortAscending) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                )
                            },
                            onClick = { viewModel.toggleSortDirection() },
                        )
                    }
                }
                Box {
                    IconButton(onClick = { showViewOptionsMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = showViewOptionsMenu, onDismissRequest = { showViewOptionsMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Show hidden files") },
                            leadingIcon = { if (showDotFiles) Icon(Icons.Default.Check, contentDescription = null) },
                            onClick = { viewModel.toggleShowDotFiles() },
                        )
                        DropdownMenuItem(
                            text = { Text("Show empty folders") },
                            leadingIcon = { if (showEmptyDirs) Icon(Icons.Default.Check, contentDescription = null) },
                            onClick = { viewModel.toggleShowEmptyDirs() },
                        )
                    }
                }
            },
        )

        // Storage tabs
        if (volumes.size > 1) {
            ScrollableTabRow(selectedTabIndex = pagerState.currentPage) {
                volumes.forEachIndexed { index, vol ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        text = { Text(vol.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        }

        // File list (single LazyColumn — avoids nested HorizontalPager scroll jank)
        val vol = currentVolume
        Column(Modifier.weight(1f)) {
            if (vol != null) {
                val dir = currentPath[vol.label] ?: vol.rootDir
                if (dir != vol.rootDir) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.navigateUp(vol.label, vol.rootDir) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("📁", modifier = Modifier.padding(4.dp))
                        Text("..", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                when (val state = loadState) {
                    is BrowserLoadState.Loading -> {
                        BrowserLoadingContent(
                            modifier = Modifier.weight(1f),
                            title = state.message,
                            subtitle = state.directoryName,
                        )
                    }
                    is BrowserLoadState.Scanning -> {
                        BrowserLoadingContent(
                            modifier = Modifier.weight(1f),
                            title = "Scanning ${state.directoryName}…",
                            subtitle = "${state.itemsFound} items",
                        )
                    }
                    else -> {
                        val selectedPaths = remember(selected) {
                            selected.mapTo(HashSet()) { it.absolutePath }
                        }
                        LazyColumn(Modifier.weight(1f), state = listState) {
                            items(
                                entries,
                                key = { it.file.absolutePath },
                                contentType = { if (it.isDirectory) "dir" else "file" },
                            ) { entry ->
                                BrowserRow(
                                    entry = entry,
                                    isSelected = entry.file.absolutePath in selectedPaths,
                                    onToggleSelect = { viewModel.toggleSelected(entry.file) },
                                    onNavigateIn = { viewModel.navigateInto(entry.file, vol.label) },
                                    onOpenFile = { openFileWithDefaultApp(context, entry.file) },
                                )
                            }
                        }
                    }
                }

            }
        }
    }
}

@Composable
private fun BrowserLoadingContent(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(48.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun BrowserRow(
    entry: BrowserEntry,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onNavigateIn: () -> Unit,
    onOpenFile: () -> Unit,
) {
    val icon = if (entry.isDirectory) "📁" else kindIcon(entry.kind)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (entry.isDirectory) onNavigateIn() else onOpenFile()
            }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(3.dp))
                .testTag("select_${entry.name}")
                .clickable { onToggleSelect() },
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
        Text(icon, modifier = Modifier.padding(2.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.subtitle, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun kindIcon(kind: FileKind?): String = when (kind) {
    FileKind.PHOTO -> "🖼️"
    FileKind.VIDEO -> "🎥"
    FileKind.LIVE_PHOTO -> "🎞️"
    FileKind.PDF -> "📕"
    FileKind.DOCUMENT -> "📄"
    FileKind.TEXT -> "📝"
    FileKind.OTHER -> "📦"
    null -> "📄"
}

