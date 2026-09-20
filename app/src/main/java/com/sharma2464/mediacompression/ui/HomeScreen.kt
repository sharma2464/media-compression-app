package com.sharma2464.mediacompression.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sharma2464.mediacompression.compress.CompressionForegroundService
import com.sharma2464.mediacompression.data.AppDatabase
import com.sharma2464.mediacompression.data.Decision
import com.sharma2464.mediacompression.data.FileEntry
import com.sharma2464.mediacompression.data.FileKind
import com.sharma2464.mediacompression.scan.BrowsableVolume
import com.sharma2464.mediacompression.scan.guessMimeType
import com.sharma2464.mediacompression.scan.listBrowsableVolumes
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val viewModel: FileBrowserViewModel = viewModel {
        FileBrowserViewModel(context)
    }
    val volumes = remember { listBrowsableVolumes(context) }
    val pagerState = rememberPagerState(pageCount = { volumes.size })
    val scope = rememberCoroutineScope()

    val entries by viewModel.entries.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val currentPath by viewModel.currentPath.collectAsState()
    val sortField by viewModel.sortField.collectAsState()
    val sortAscending by viewModel.sortAscending.collectAsState()
    val showDotFiles by viewModel.showDotFiles.collectAsState()
    val showEmptyDirs by viewModel.showEmptyDirs.collectAsState()

    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }

    val currentVolume = volumes.getOrNull(pagerState.currentPage)
    val currentDir = currentVolume?.let { currentPath[it.label] ?: it.rootDir }

    LaunchedEffect(pagerState.currentPage) {
        if (volumes.isNotEmpty()) {
            viewModel.loadVolume(volumes, pagerState.currentPage)
            viewModel.clearSelected()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Top app bar
        TopAppBar(
            title = {
                Text(
                    currentDir?.name ?: currentVolume?.label ?: "Files",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            actions = {
                Box {
                    IconButton(onClick = { showFilterMenu = !showFilterMenu }) {
                        Icon(Icons.Default.Menu, contentDescription = "View options")
                    }
                    DropdownMenu(expanded = showFilterMenu, onDismissRequest = { showFilterMenu = false }) {
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
                val volLabel = currentVolume?.label
                val volRoot = currentVolume?.rootDir
                IconButton(onClick = {
                    if (volLabel != null && volRoot != null && currentDir != volRoot) {
                        viewModel.navigateUp(volLabel, volRoot)
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Navigate up")
                }
                Box {
                    IconButton(onClick = { showSortMenu = !showSortMenu }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Sort options")
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        SortField.entries.forEach { field ->
                            DropdownMenuItem(
                                text = { Text(field.name) },
                                onClick = {
                                    viewModel.setSortField(field)
                                    showSortMenu = false
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        if (sortAscending) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text(if (sortAscending) "Ascending" else "Descending")
                                }
                            },
                            onClick = {
                                viewModel.toggleSortDirection()
                            },
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

        // File list pager
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { pageIndex ->
            val vol = volumes.getOrNull(pageIndex) ?: return@HorizontalPager
            val dir = currentPath[vol.label] ?: vol.rootDir

            Column(Modifier.fillMaxSize()) {
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

                LazyColumn(Modifier.weight(1f)) {
                    items(entries, key = { it.file.absolutePath }) { entry ->
                        BrowserRow(
                            entry = entry,
                            isSelected = selected.contains(entry.file),
                            onToggleSelect = { viewModel.toggleSelected(entry.file) },
                            onNavigateIn = { viewModel.navigateInto(entry.file, vol.label) },
                        )
                    }
                }

                if (selected.isNotEmpty()) {
                    SelectionBar(
                        context = context,
                        selected = selected,
                        onCompress = { selected ->
                            scope.launch {
                                compressSelected(context, selected)
                                viewModel.clearSelected()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowserRow(
    entry: BrowserEntry,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onNavigateIn: () -> Unit,
) {
    val context = LocalContext.current
    val icon = if (entry.isDirectory) "📁" else kindIcon(entry.kind)

    val metadataText = if (entry.isDirectory) {
        val dirSize = Formatter.formatShortFileSize(context, entry.dirTotalSizeBytes)
        val fileCount = entry.dirFileCount
        "$dirSize • $fileCount ${if (fileCount == 1) "file" else "files"}"
    } else {
        val fileSize = Formatter.formatShortFileSize(context, entry.sizeBytes)
        val fileType = entry.kind?.name ?: "FILE"
        val dateText = formatTimeFromNow(entry.dateTakenMs)
        listOfNotNull(fileSize, fileType, dateText.takeIf { it.isNotEmpty() }).joinToString(" • ")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = entry.isDirectory) { onNavigateIn() }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Checkbox(checked = isSelected, onCheckedChange = { onToggleSelect() })
        Text(icon, modifier = Modifier.padding(4.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(metadataText, style = MaterialTheme.typography.labelSmall)
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

@Composable
private fun SelectionBar(
    context: Context,
    selected: Set<File>,
    onCompress: (Set<File>) -> Unit,
) {
    val totalSize = remember(selected) {
        selected.fold(0L) { acc, file ->
            acc + (if (file.isDirectory) {
                file.walk().filter { it.isFile }.sumOf { it.length() }
            } else {
                file.length()
            })
        }
    }
    val totalSizeStr = Formatter.formatShortFileSize(context, totalSize)

    val shareIntent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Selected: ${selected.size} items, $totalSizeStr", style = MaterialTheme.typography.labelMedium)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        val uris = selected.map { Uri.fromFile(it) }.toTypedArray()
                        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                            type = "*/*"
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris.toList()))
                        }
                        shareIntent.launch(Intent.createChooser(intent, "Share files"))
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Share")
                }
                Button(
                    onClick = { onCompress(selected) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Compress")
                }
            }
        }
    }
}

private suspend fun compressSelected(context: Context, selected: Set<File>) {
    val db = AppDatabase.get(context)
    val dao = db.fileEntryDao()
    val entries = mutableListOf<FileEntry>()

    selected.forEach { file ->
        if (file.isDirectory) {
            file.walk().filter { it.isFile }.forEach { f ->
                entries += makeFileEntry(f)
            }
        } else {
            entries += makeFileEntry(file)
        }
    }

    dao.insertAll(entries)
    CompressionForegroundService.start(context)
}

private fun makeFileEntry(file: File): FileEntry {
    val mime = guessMimeType(file.name)
    val kind = com.sharma2464.mediacompression.scan.classifyFile(mime)
    return FileEntry(
        uri = Uri.fromFile(file).toString(),
        relativePath = file.absolutePath,
        displayName = file.name,
        mimeType = mime,
        kind = kind,
        sizeBytes = file.length(),
        lastModified = file.lastModified(),
        decision = Decision.COMPRESS,
    )
}
