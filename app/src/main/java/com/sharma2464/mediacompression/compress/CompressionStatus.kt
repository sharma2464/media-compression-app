package com.sharma2464.mediacompression.compress

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class FileCompressionState { QUEUED, IN_PROGRESS, DONE, FAILED, CANCELLED }

data class FileProgress(
    val fileName: String,
    val sizeBytes: Long,
    val state: FileCompressionState,
    val percent: Int = 0, // 0-100, meaningful only while IN_PROGRESS
)

data class CompressionBatch(
    val files: List<FileProgress>,
    val currentIndex: Int,
    val modeLabel: String,
    val rateBytesPerSec: Long = 0L,
)

data class CompressionFinishedItem(
    val displayName: String,
    val outputPath: String,
    val originalBytes: Long,
    val compressedBytes: Long,
    val isVideo: Boolean,
)

data class CompressionFinishedSummary(
    val items: List<CompressionFinishedItem>,
    val modeLabel: String,
)

/** In-process broadcast of compression progress, shared by the foreground service's
 *  notification and the in-app FAB/dialog so they always agree. */
object CompressionStatus {
    private val _batch = MutableStateFlow<CompressionBatch?>(null)
    val batch: StateFlow<CompressionBatch?> = _batch

    private val _finished = MutableStateFlow<CompressionFinishedSummary?>(null)
    val finished: StateFlow<CompressionFinishedSummary?> = _finished

    private val _cancelRequested = MutableStateFlow(false)
    val cancelRequested: StateFlow<Boolean> = _cancelRequested

    fun startBatch(fileNames: List<Pair<String, Long>>, modeLabel: String) {
        _cancelRequested.value = false
        _batch.value = CompressionBatch(
            files = fileNames.map { (name, size) -> FileProgress(name, size, FileCompressionState.QUEUED) },
            currentIndex = -1,
            modeLabel = modeLabel,
        )
    }

    fun updateCurrentFile(index: Int, percent: Int, rateBytesPerSec: Long) {
        val current = _batch.value ?: return
        val files = current.files.toMutableList()
        if (index !in files.indices) return
        files[index] = files[index].copy(state = FileCompressionState.IN_PROGRESS, percent = percent)
        _batch.value = current.copy(files = files, currentIndex = index, rateBytesPerSec = rateBytesPerSec)
    }

    fun markDone(index: Int) = markState(index, FileCompressionState.DONE)
    fun markFailed(index: Int) = markState(index, FileCompressionState.FAILED)
    fun markCancelled(index: Int) = markState(index, FileCompressionState.CANCELLED)

    private fun markState(index: Int, state: FileCompressionState) {
        val current = _batch.value ?: return
        val files = current.files.toMutableList()
        if (index !in files.indices) return
        files[index] = files[index].copy(state = state, percent = if (state == FileCompressionState.DONE) 100 else files[index].percent)
        _batch.value = current.copy(files = files)
    }

    fun requestCancel() {
        _cancelRequested.value = true
    }

    fun complete(summary: CompressionFinishedSummary) {
        _batch.value = null
        _finished.value = summary
        _cancelRequested.value = false
    }

    fun dismissFinished() {
        _finished.value = null
    }

    fun clear() {
        _batch.value = null
        _finished.value = null
        _cancelRequested.value = false
    }
}
