package com.sharma2464.tindercompression.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sharma2464.tindercompression.compress.CompressionForegroundService
import com.sharma2464.tindercompression.data.AppDatabase
import com.sharma2464.tindercompression.data.Decision
import com.sharma2464.tindercompression.data.FileEntry
import com.sharma2464.tindercompression.scan.FolderScanner
import com.sharma2464.tindercompression.scan.ScanResult
import com.sharma2464.tindercompression.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReviewViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).fileEntryDao()
    val settings = AppSettings(app)

    private val _current = MutableStateFlow<FileEntry?>(null)
    val current: StateFlow<FileEntry?> = _current

    val completed: StateFlow<List<FileEntry>> = dao.observeCompleted()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _rootPicked = MutableStateFlow(settings.rootTreeUri != null)
    val rootPicked: StateFlow<Boolean> = _rootPicked

    private val _rootRejectedMessage = MutableStateFlow<String?>(null)
    val rootRejectedMessage: StateFlow<String?> = _rootRejectedMessage

    init {
        settings.rootTreeUri?.let { loadNext() }
    }

    fun onRootPicked(uri: Uri) {
        getApplication<Application>().contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        viewModelScope.launch {
            when (FolderScanner(getApplication()).scan(uri)) {
                ScanResult.REJECTED_BACKUP_FOLDER -> {
                    _rootRejectedMessage.value =
                        "That's a backup folder — reviewing it would recompress your originals. Pick its parent folder instead."
                }
                ScanResult.ROOT_UNREADABLE -> {
                    _rootRejectedMessage.value = "Couldn't read that folder — try again."
                }
                ScanResult.SUCCESS -> {
                    settings.rootTreeUri = uri.toString()
                    _rootPicked.value = true
                    loadNext()
                }
            }
        }
    }

    fun clearRootRejectedMessage() {
        _rootRejectedMessage.value = null
    }

    fun onDecision(entry: FileEntry, decision: Decision) {
        viewModelScope.launch {
            dao.update(entry.copy(decision = decision, reviewedAt = System.currentTimeMillis()))
            if (decision == Decision.COMPRESS) {
                CompressionForegroundService.start(getApplication())
            }
            loadNext()
        }
    }

    private fun loadNext() {
        viewModelScope.launch { _current.value = dao.nextForReview() }
    }
}
