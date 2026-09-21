package com.sharma2464.mediacompression.compress

import com.sharma2464.mediacompression.data.FileKind
import com.sharma2464.mediacompression.settings.VideoEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class FileCompressionState { QUEUED, IN_PROGRESS, DONE, FAILED, CANCELLED }

data class FileProgress(
    val fileName: String,
    val sizeBytes: Long,
    val state: FileCompressionState,
    val percent: Int = 0,
    val sourceUri: String? = null,
    val fileKind: FileKind? = null,
    val durationMs: Long? = null,
    val encodeOutputPath: String? = null,
    val finishedOutputPath: String? = null,
)

data class CompressionBatch(
    val files: List<FileProgress>,
    val currentIndex: Int,
    val modeLabel: String,
    val rateBytesPerSec: Long = 0L,
    val jobSettings: CompressJobSettings = CompressJobSettings.DEFAULT,
    val currentFileUri: String? = null,
    val currentFileKind: FileKind? = null,
    val currentDurationMs: Long? = null,
    val batchComplete: Boolean = false,
    val videoEngine: VideoEngine = VideoEngine.MEDIA3,
)

data class CompressionFinishedItem(
    val displayName: String,
    val outputPath: String,
    val originalBytes: Long,
    val compressedBytes: Long,
    val isVideo: Boolean,
    val sourceUri: String? = null,
    val fileKind: FileKind? = null,
)

data class CompressionFinishedSummary(
    val items: List<CompressionFinishedItem>,
    val modeLabel: String,
)

object CompressionStatus {
    private val _batch = MutableStateFlow<CompressionBatch?>(null)
    val batch: StateFlow<CompressionBatch?> = _batch

    private val _finished = MutableStateFlow<CompressionFinishedSummary?>(null)
    val finished: StateFlow<CompressionFinishedSummary?> = _finished

    private val _cancelRequested = MutableStateFlow(false)
    val cancelRequested: StateFlow<Boolean> = _cancelRequested

    fun startBatch(
        fileNames: List<Pair<String, Long>>,
        modeLabel: String,
        jobSettings: CompressJobSettings = CompressJobSettings.DEFAULT,
        videoEngine: VideoEngine = VideoEngine.MEDIA3,
    ) {
        _cancelRequested.value = false
        _batch.value = CompressionBatch(
            files = fileNames.map { (name, size) -> FileProgress(name, size, FileCompressionState.QUEUED) },
            currentIndex = -1,
            modeLabel = modeLabel,
            jobSettings = jobSettings,
            videoEngine = videoEngine,
        )
    }

    fun updateCurrentFile(
        index: Int,
        percent: Int,
        rateBytesPerSec: Long,
        fileUri: String? = null,
        fileKind: FileKind? = null,
        durationMs: Long? = null,
    ) {
        val current = _batch.value ?: return
        val files = current.files.toMutableList()
        if (index !in files.indices) return
        val prev = files[index]
        files[index] = prev.copy(
            state = FileCompressionState.IN_PROGRESS,
            percent = percent,
            sourceUri = fileUri ?: prev.sourceUri,
            fileKind = fileKind ?: prev.fileKind,
            durationMs = durationMs ?: prev.durationMs,
        )
        _batch.value = current.copy(
            files = files,
            currentIndex = index,
            rateBytesPerSec = rateBytesPerSec,
            currentFileUri = fileUri ?: current.currentFileUri,
            currentFileKind = fileKind ?: current.currentFileKind,
            currentDurationMs = durationMs ?: current.currentDurationMs,
        )
    }

    fun setEncodeOutputPath(index: Int, path: String) {
        val current = _batch.value ?: return
        val files = current.files.toMutableList()
        if (index !in files.indices) return
        files[index] = files[index].copy(encodeOutputPath = path)
        _batch.value = current.copy(files = files)
    }

    fun setFinishedOutputPath(index: Int, path: String) {
        val current = _batch.value ?: return
        val files = current.files.toMutableList()
        if (index !in files.indices) return
        files[index] = files[index].copy(finishedOutputPath = path)
        _batch.value = current.copy(files = files)
    }

    fun markDone(index: Int) = markState(index, FileCompressionState.DONE)

    fun markFailed(index: Int) = markState(index, FileCompressionState.FAILED)

    fun markCancelled(index: Int) = markState(index, FileCompressionState.CANCELLED)

    private fun markState(index: Int, state: FileCompressionState) {
        val current = _batch.value ?: return
        val files = current.files.toMutableList()
        if (index !in files.indices) return
        files[index] = files[index].copy(
            state = state,
            percent = if (state == FileCompressionState.DONE) 100 else files[index].percent,
        )
        _batch.value = current.copy(files = files)
    }

    fun requestCancel() {
        _cancelRequested.value = true
    }

    fun complete(summary: CompressionFinishedSummary) {
        val current = _batch.value
        if (current != null) {
            val files = current.files.toMutableList()
            summary.items.forEachIndexed { i, item ->
                if (i in files.indices) {
                    files[i] = files[i].copy(
                        state = FileCompressionState.DONE,
                        percent = 100,
                        finishedOutputPath = item.outputPath,
                        sourceUri = item.sourceUri ?: files[i].sourceUri,
                        fileKind = item.fileKind ?: files[i].fileKind,
                    )
                }
            }
            _batch.value = current.copy(files = files, batchComplete = true)
        }
        _finished.value = summary
        _cancelRequested.value = false
    }

    fun dismissFinished() {
        _finished.value = null
        _batch.value = null
    }

    fun clear() {
        _batch.value = null
        _finished.value = null
        _cancelRequested.value = false
    }
}
