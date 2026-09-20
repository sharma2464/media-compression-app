package com.sharma2464.mediacompression.ui

import android.content.Context
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val dirTotalSizeBytes: Long = 0L, // for directories: recursive size
    val dirFileCount: Int = 0, // for directories: total file count (recursive)
    val dateTakenMs: Long? = null, // for files: EXIF date taken, nullable
)

enum class SortField { DATE_TAKEN, DATE_MODIFIED, SIZE, NAME, TYPE }

class FileBrowserViewModel(private val context: Context) : ViewModel() {
    private val _entries = MutableStateFlow<List<BrowserEntry>>(emptyList())
    val entries: StateFlow<List<BrowserEntry>> = _entries

    private val _currentPath = MutableStateFlow<Map<String, File>>(emptyMap()) // per-volume index -> currentPath
    val currentPath: StateFlow<Map<String, File>> = _currentPath

    private val _selected = MutableStateFlow<Set<File>>(emptySet())
    val selected: StateFlow<Set<File>> = _selected

    private val _sortField = MutableStateFlow(SortField.SIZE)
    val sortField: StateFlow<SortField> = _sortField

    // Descending by default: browsing to find large files worth compressing is the common
    // case, so the biggest files/folders should be on top rather than the smallest.
    private val _sortAscending = MutableStateFlow(false)
    val sortAscending: StateFlow<Boolean> = _sortAscending

    private val _showDotFiles = MutableStateFlow(false)
    val showDotFiles: StateFlow<Boolean> = _showDotFiles

    private val _showEmptyDirs = MutableStateFlow(false)
    val showEmptyDirs: StateFlow<Boolean> = _showEmptyDirs

    private var allEntries: List<BrowserEntry> = emptyList()

    fun loadVolume(volumes: List<BrowsableVolume>, volumeIndex: Int) {
        if (volumeIndex !in volumes.indices) return
        val volume = volumes[volumeIndex]
        val currentPath = _currentPath.value[volume.label] ?: volume.rootDir
        loadDirectory(currentPath)
    }

    private fun loadDirectory(dir: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val entries = mutableListOf<BrowserEntry>()
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    val (totalSize, fileCount) = computeDirMetadata(file)
                    entries += BrowserEntry(
                        file = file,
                        name = file.name,
                        isDirectory = true,
                        sizeBytes = 0L,
                        lastModified = file.lastModified(),
                        kind = null,
                        dirTotalSizeBytes = totalSize,
                        dirFileCount = fileCount,
                    )
                } else {
                    val mime = guessMimeType(file.name)
                    val baseKind = classifyFile(mime)
                    val kind = if (baseKind == FileKind.PHOTO && isMotionPhotoFile(file)) {
                        FileKind.LIVE_PHOTO
                    } else {
                        baseKind
                    }
                    val dateTaken = getDateTakenMs(file, kind)
                    entries += BrowserEntry(
                        file = file,
                        name = file.name,
                        isDirectory = false,
                        sizeBytes = file.length(),
                        lastModified = file.lastModified(),
                        kind = kind,
                        dateTakenMs = dateTaken,
                    )
                }
            }
            allEntries = entries
            _entries.value = filterAndSort(entries)
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
        _entries.value = filterAndSort(allEntries)
    }

    fun toggleShowEmptyDirs() {
        _showEmptyDirs.value = !_showEmptyDirs.value
        _entries.value = filterAndSort(allEntries)
    }

    private fun computeDirMetadata(dir: File): Pair<Long, Int> {
        var totalSize = 0L
        var fileCount = 0
        dir.walk().forEach { file ->
            if (file.isFile) {
                totalSize += file.length()
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
                        } catch (e: Exception) {
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
        loadDirectory(file)
    }

    fun navigateUp(volumeLabel: String, volumeRootDir: File) {
        val current = _currentPath.value[volumeLabel] ?: volumeRootDir
        if (current == volumeRootDir) return
        val parent = current.parentFile ?: volumeRootDir
        navigateInto(parent, volumeLabel)
    }

    fun setSortField(field: SortField) {
        _sortField.value = field
        _entries.value = filterAndSort(allEntries)
    }

    fun toggleSortDirection() {
        _sortAscending.value = !_sortAscending.value
        _entries.value = filterAndSort(allEntries)
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

    fun clearSelected() {
        _selected.value = emptySet()
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

        // Reverse each group independently (not the concatenated list) so folders always stay
        // above files regardless of sort direction — reversing the whole list would push
        // folders below files whenever descending order is selected.
        val orderedDirs = if (asc) sortedDirs else sortedDirs.reversed()
        val orderedFiles = if (asc) sortedFiles else sortedFiles.reversed()
        return orderedDirs + orderedFiles
    }
}
