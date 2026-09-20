package com.sharma2464.mediacompression.ui

import android.content.Context
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharma2464.mediacompression.data.AppDatabase
import com.sharma2464.mediacompression.data.BrowserCache
import com.sharma2464.mediacompression.data.FileKind
import com.sharma2464.mediacompression.scan.BrowsableVolume
import com.sharma2464.mediacompression.scan.classifyFile
import com.sharma2464.mediacompression.scan.guessMimeType
import com.sharma2464.mediacompression.scan.isMotionPhotoFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

data class BrowserEntry(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val kind: FileKind? = null,
    val dirTotalSizeBytes: Long = 0L,
    val dirFileCount: Int = 0,
    val dateTakenMs: Long? = null,
    /** Precomputed list subtitle — avoids Formatter work during scroll. */
    val subtitle: String = "",
)

enum class SortField { DATE_TAKEN, DATE_MODIFIED, SIZE, NAME, TYPE }

fun SortField.displayLabel(): String = when (this) {
    SortField.DATE_TAKEN -> "Date taken"
    SortField.DATE_MODIFIED -> "Date modified"
    SortField.SIZE -> "Size"
    SortField.NAME -> "Name"
    SortField.TYPE -> "Type"
}

enum class BackAction { ClearSelection, NavigatedUp, GoToHomeTab, None }

sealed class BrowserLoadState {
    data object Idle : BrowserLoadState()
    data class Loading(val directoryName: String, val message: String) : BrowserLoadState()
    data class Scanning(val directoryName: String, val itemsFound: Int) : BrowserLoadState()
    data class Ready(val fromCache: Boolean) : BrowserLoadState()
}

class FileBrowserViewModel(context: Context) : ViewModel() {
    private val appContext = context.applicationContext
    private val cache = BrowserCache(AppDatabase.get(context).browserCacheDao())

    private val _entries = MutableStateFlow<List<BrowserEntry>>(emptyList())
    val entries: StateFlow<List<BrowserEntry>> = _entries

    private val _loadState = MutableStateFlow<BrowserLoadState>(BrowserLoadState.Idle)
    val loadState: StateFlow<BrowserLoadState> = _loadState

    private val _currentPath = MutableStateFlow<Map<String, File>>(emptyMap())
    val currentPath: StateFlow<Map<String, File>> = _currentPath

    private val _selected = MutableStateFlow<Set<File>>(emptySet())
    val selected: StateFlow<Set<File>> = _selected

    private val _sortField = MutableStateFlow(SortField.SIZE)
    val sortField: StateFlow<SortField> = _sortField

    private val _sortAscending = MutableStateFlow(false)
    val sortAscending: StateFlow<Boolean> = _sortAscending

    private val _showDotFiles = MutableStateFlow(false)
    val showDotFiles: StateFlow<Boolean> = _showDotFiles

    private val _showEmptyDirs = MutableStateFlow(false)
    val showEmptyDirs: StateFlow<Boolean> = _showEmptyDirs

    private var allEntries: List<BrowserEntry> = emptyList()
    private var activeDirectory: File? = null

    private val _activeVolumeLabel = MutableStateFlow<String?>(null)
    val activeVolumeLabel: StateFlow<String?> = _activeVolumeLabel

    private val _activeVolumeRoot = MutableStateFlow<File?>(null)
    val activeVolumeRoot: StateFlow<File?> = _activeVolumeRoot

    val isScanning: Boolean
        get() = _loadState.value is BrowserLoadState.Scanning

    fun loadDirectoryAt(dir: File) {
        loadDirectory(dir, forceRescan = false)
    }

    fun rescanCurrentDirectory() {
        val dir = activeDirectory ?: return
        if (_loadState.value is BrowserLoadState.Scanning) return
        loadDirectory(dir, forceRescan = true)
    }

    fun loadVolume(volumes: List<BrowsableVolume>, volumeIndex: Int) {
        if (volumeIndex !in volumes.indices) return
        val volume = volumes[volumeIndex]
        val dir = _currentPath.value[volume.label] ?: volume.rootDir
        loadDirectory(dir, forceRescan = false)
    }

    private fun loadDirectory(dir: File, forceRescan: Boolean) {
        activeDirectory = dir
        viewModelScope.launch(Dispatchers.IO) {
            _loadState.value = BrowserLoadState.Loading(
                directoryName = dir.name.ifEmpty { dir.absolutePath },
                message = if (forceRescan) "Rescanning…" else "Loading cached files…",
            )
            if (!forceRescan) {
                val cached = cache.loadChildren(dir)
                if (cached != null) {
                    publishEntries(cached, fromCache = true)
                    return@launch
                }
            }
            scanAndCacheDirectory(dir)
        }
    }

    private suspend fun scanAndCacheDirectory(dir: File) {
        val dirName = dir.name.ifEmpty { dir.absolutePath }
        _loadState.value = BrowserLoadState.Scanning(dirName, 0)
        val entries = mutableListOf<BrowserEntry>()
        val children = dir.listFiles() ?: emptyArray()
        var processed = 0
        for (file in children) {
            entries += buildEntry(file)
            processed++
            if (processed % 50 == 0) {
                _loadState.value = BrowserLoadState.Scanning(dirName, processed)
            }
        }
        cache.saveListing(dir, entries)
        publishEntries(entries, fromCache = false)
    }

    private suspend fun publishEntries(entries: List<BrowserEntry>, fromCache: Boolean) {
        allEntries = entries
        emitEntries(filterAndSort(entries))
        _loadState.value = BrowserLoadState.Ready(fromCache)
    }

    private fun emitEntries(list: List<BrowserEntry>) {
        _entries.value = list.map { entry -> enrichForDisplay(entry) }
    }

    private fun buildEntry(file: File): BrowserEntry {
        if (file.isDirectory) {
            val (totalSize, fileCount) = computeDirMetadata(file)
            return BrowserEntry(
                file = file,
                name = file.name,
                isDirectory = true,
                sizeBytes = 0L,
                lastModified = file.lastModified(),
                kind = null,
                dirTotalSizeBytes = totalSize,
                dirFileCount = fileCount,
            )
        }
        val mime = guessMimeType(file.name)
        val baseKind = classifyFile(mime)
        val kind = if (baseKind == FileKind.PHOTO && isMotionPhotoFile(file)) {
            FileKind.LIVE_PHOTO
        } else {
            baseKind
        }
        return BrowserEntry(
            file = file,
            name = file.name,
            isDirectory = false,
            sizeBytes = file.length(),
            lastModified = file.lastModified(),
            kind = kind,
            dateTakenMs = null,
        )
    }

    private fun fillExifDatesIfNeeded() {
        val needsExif = allEntries.any {
            !it.isDirectory &&
                (it.kind == FileKind.PHOTO || it.kind == FileKind.LIVE_PHOTO) &&
                it.dateTakenMs == null
        }
        if (!needsExif) return
        viewModelScope.launch(Dispatchers.IO) {
            var anyChanged = false
            val updated = allEntries.map { entry ->
                if (entry.isDirectory || entry.dateTakenMs != null) return@map entry
                val kind = entry.kind
                if (kind != FileKind.PHOTO && kind != FileKind.LIVE_PHOTO) return@map entry
                val taken = getDateTakenMs(entry.file, kind) ?: return@map entry
                anyChanged = true
                entry.copy(dateTakenMs = taken)
            }
            if (!anyChanged) return@launch
            allEntries = updated
            val withDates = updated.map { enrichForDisplay(it) }
            allEntries = withDates
            emitEntries(filterAndSort(withDates))
            activeDirectory?.let { dir -> cache.saveListing(dir, allEntries) }
        }
    }

    private fun filterAndSort(entries: List<BrowserEntry>): List<BrowserEntry> {
        val filtered = entries.filter { entry ->
            (_showDotFiles.value || !entry.name.startsWith(".")) &&
                (_showEmptyDirs.value || !entry.isDirectory || entry.dirFileCount > 0)
        }
        return sortEntries(filtered)
    }

    fun toggleShowDotFiles() {
        _showDotFiles.value = !_showDotFiles.value
        emitEntries(filterAndSort(allEntries))
    }

    fun toggleShowEmptyDirs() {
        _showEmptyDirs.value = !_showEmptyDirs.value
        emitEntries(filterAndSort(allEntries))
    }

    private fun computeDirMetadata(dir: File): Pair<Long, Int> {
        var totalSize = 0L
        var fileCount = 0
        dir.walk().forEach { f ->
            if (f.isFile) {
                totalSize += f.length()
                fileCount++
            }
        }
        return totalSize to fileCount
    }

    private fun getDateTakenMs(file: File, kind: FileKind): Long? {
        if (kind != FileKind.PHOTO && kind != FileKind.LIVE_PHOTO) return null
        return runCatching {
            val exif = ExifInterface(file)
            exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?.let { dateStr ->
                    val parts = dateStr.split(" ")
                    if (parts.size == 2) {
                        val datePart = parts[0].replace(":", "-")
                        val timePart = parts[1]
                        val iso = "${datePart}T${timePart}Z"
                        try {
                            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").apply {
                                timeZone = java.util.TimeZone.getTimeZone("UTC")
                            }.parse(iso)?.time
                        } catch (_: Exception) {
                            null
                        }
                    } else {
                        null
                    }
                }
        }.getOrNull()
    }

    fun navigateInto(file: File, volumeLabel: String) {
        if (!file.isDirectory) return
        val updated = _currentPath.value.toMutableMap()
        updated[volumeLabel] = file
        _currentPath.value = updated
    }

    fun navigateUp(volumeLabel: String, volumeRootDir: File) {
        val current = _currentPath.value[volumeLabel] ?: volumeRootDir
        if (current == volumeRootDir) return
        val parent = current.parentFile ?: volumeRootDir
        navigateInto(parent, volumeLabel)
    }

    fun setActiveVolume(volumeLabel: String, volumeRootDir: File) {
        _activeVolumeLabel.value = volumeLabel
        _activeVolumeRoot.value = volumeRootDir
    }

    fun openAtVolumePath(volumeLabel: String, volumeRoot: File, dir: File) {
        setActiveVolume(volumeLabel, volumeRoot)
        val updated = _currentPath.value.toMutableMap()
        updated[volumeLabel] = dir
        _currentPath.value = updated
        loadDirectoryAt(dir)
    }

    fun isAtVolumeRoot(): Boolean {
        val label = _activeVolumeLabel.value ?: return true
        val root = _activeVolumeRoot.value ?: return true
        val current = _currentPath.value[label] ?: root
        return current == root
    }

    /** First back action for the Files tab when selection is already empty. */
    fun handleFilesTabBack(): BackAction {
        if (_selected.value.isNotEmpty()) return BackAction.ClearSelection
        if (!isAtVolumeRoot()) {
            val label = _activeVolumeLabel.value
            val root = _activeVolumeRoot.value
            if (label != null && root != null) navigateUp(label, root)
            return BackAction.NavigatedUp
        }
        return BackAction.None
    }

    fun deleteSelected() {
        val toDelete = _selected.value.toList()
        if (toDelete.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            toDelete.forEach { file ->
                if (file.isDirectory) file.deleteRecursively() else file.delete()
            }
            _selected.value = emptySet()
            val dir = activeDirectory
            if (dir != null) {
                loadDirectory(dir, forceRescan = true)
            }
        }
    }

    fun setSortField(field: SortField) {
        _sortField.value = field
        emitEntries(filterAndSort(allEntries))
        if (field == SortField.DATE_TAKEN) fillExifDatesIfNeeded()
    }

    fun toggleSortDirection() {
        _sortAscending.value = !_sortAscending.value
        emitEntries(filterAndSort(allEntries))
    }

    fun toggleSelected(file: File) {
        val updated = _selected.value.toMutableSet()
        if (updated.contains(file)) {
            updated.remove(file)
        } else {
            updated.add(file)
        }
        _selected.value = updated
    }

    fun toggleSelectedByPath(path: String) = toggleSelected(File(path))

    fun navigateIntoPath(path: String, volumeLabel: String) = navigateInto(File(path), volumeLabel)

    fun clearSelected() {
        _selected.value = emptySet()
    }

    private fun enrichForDisplay(entry: BrowserEntry): BrowserEntry {
        if (entry.subtitle.isNotEmpty()) return entry
        return entry.copy(subtitle = formatBrowserEntrySubtitle(appContext, entry))
    }

    private fun sortEntries(entries: List<BrowserEntry>): List<BrowserEntry> {
        val field = _sortField.value
        val asc = _sortAscending.value

        val (dirs, files) = entries.partition { it.isDirectory }

        val sortedDirs = dirs.sortedWith(compareBy { it.name })
        val sortedFiles = when (field) {
            SortField.DATE_TAKEN -> files.sortedBy { it.dateTakenMs ?: it.lastModified }
            SortField.DATE_MODIFIED -> files.sortedBy { it.lastModified }
            SortField.SIZE -> files.sortedBy { it.sizeBytes }
            SortField.NAME -> files.sortedBy { it.name }
            SortField.TYPE -> files.sortedBy { it.kind?.ordinal ?: -1 }
        }

        val orderedDirs = if (asc) sortedDirs else sortedDirs.reversed()
        val orderedFiles = if (asc) sortedFiles else sortedFiles.reversed()
        return orderedDirs + orderedFiles
    }
}
