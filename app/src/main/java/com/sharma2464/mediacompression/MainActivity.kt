package com.sharma2464.mediacompression

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sharma2464.mediacompression.compress.CompressionStatus
import com.sharma2464.mediacompression.scan.compressibleFilesInSelection
import com.sharma2464.mediacompression.scan.hasFullStorageAccess
import com.sharma2464.mediacompression.settings.AppSettings
import com.sharma2464.mediacompression.settings.SettingsScreen
import com.sharma2464.mediacompression.ui.BrowserLoadState
import com.sharma2464.mediacompression.ui.CompressDialogStage
import com.sharma2464.mediacompression.ui.CompressionBatchDialog
import com.sharma2464.mediacompression.ui.CompressionProgressFab
import com.sharma2464.mediacompression.ui.FileBrowserViewModel
import com.sharma2464.mediacompression.ui.HomeScreen
import com.sharma2464.mediacompression.ui.RescanFab
import com.sharma2464.mediacompression.ui.SelectionActionFab
import com.sharma2464.mediacompression.ui.WelcomeScreen
import com.sharma2464.mediacompression.ui.buildCompressPreviewState
import com.sharma2464.mediacompression.ui.theme.MediaCompressionTheme

private enum class Tab(val label: String) {
    HOME("Files"),
    SETTINGS("Settings"),
}

class MainActivity : ComponentActivity() {

    companion object {
        /** Instrumentation / E2E: open this directory on launch (requires full storage access). */
        const val EXTRA_INITIAL_DIRECTORY = "com.sharma2464.mediacompression.extra.INITIAL_DIRECTORY"
    }

    private val fileBrowserViewModel: FileBrowserViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                FileBrowserViewModel(applicationContext) as T
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appSettings = AppSettings(this)
        setContent {
            var themeMode by remember { mutableStateOf(appSettings.themeMode) }
            MediaCompressionTheme(themeMode = themeMode) {
                Surface(Modifier.semantics { testTagsAsResourceId = true }) {
                    val context = LocalContext.current
                    var hasFullAccess by remember { mutableStateOf(hasFullStorageAccess()) }
                    val requestFullAccess = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                        hasFullAccess = hasFullStorageAccess()
                    }
                    var tab by remember { mutableStateOf(Tab.HOME) }
                    val loadState by fileBrowserViewModel.loadState.collectAsState()
                    val isScanning = loadState is BrowserLoadState.Scanning
                    val selected by fileBrowserViewModel.selected.collectAsState()
                    val compressionBatch by CompressionStatus.batch.collectAsState()
                    val compressionFinished by CompressionStatus.finished.collectAsState()
                    val compressibleCount = remember(selected) { compressibleFilesInSelection(selected).size }

                    var compressDialogVisible by remember { mutableStateOf(false) }
                    var compressDialogStage by remember { mutableStateOf(CompressDialogStage.Preview) }
                    var compressPreviewState by remember {
                        mutableStateOf<com.sharma2464.mediacompression.ui.CompressPreviewState?>(null)
                    }

                    val atVolumeRoot = fileBrowserViewModel.isAtVolumeRoot()
                    val backConsumes = selected.isNotEmpty() ||
                        tab == Tab.SETTINGS ||
                        (tab == Tab.HOME && !atVolumeRoot)

                    BackHandler(enabled = hasFullAccess && backConsumes && !compressDialogVisible) {
                        when {
                            selected.isNotEmpty() -> fileBrowserViewModel.clearSelected()
                            tab == Tab.SETTINGS -> tab = Tab.HOME
                            tab == Tab.HOME && !atVolumeRoot -> {
                                val label = fileBrowserViewModel.activeVolumeLabel.value
                                val root = fileBrowserViewModel.activeVolumeRoot.value
                                if (label != null && root != null) {
                                    fileBrowserViewModel.navigateUp(label, root)
                                }
                            }
                        }
                    }

                    LaunchedEffect(compressionFinished, hasFullAccess) {
                        val summary = compressionFinished
                        if (hasFullAccess && summary != null && summary.items.isNotEmpty()) {
                            compressDialogVisible = true
                            compressDialogStage = CompressDialogStage.Complete
                        }
                    }

                    BackHandler(enabled = hasFullAccess && compressDialogVisible) {
                        when (compressDialogStage) {
                            CompressDialogStage.Preview -> compressDialogVisible = false
                            CompressDialogStage.Progress -> compressDialogVisible = false
                            CompressDialogStage.Complete -> {
                                CompressionStatus.dismissFinished()
                                compressDialogVisible = false
                                compressDialogStage = CompressDialogStage.Preview
                                fileBrowserViewModel.rescanCurrentDirectory()
                            }
                        }
                    }

                    LaunchedEffect(Unit) {
                        hasFullAccess = hasFullStorageAccess()
                    }

                    val initialDirectory = remember {
                        intent.getStringExtra(EXTRA_INITIAL_DIRECTORY)?.let { java.io.File(it) }
                    }
                    LaunchedEffect(initialDirectory, hasFullAccess) {
                        val dir = initialDirectory
                        if (hasFullAccess && dir != null && dir.isDirectory) {
                            fileBrowserViewModel.openAtVolumePath(
                                volumeLabel = "Internal Storage",
                                volumeRoot = Environment.getExternalStorageDirectory(),
                                dir = dir,
                            )
                        }
                    }

                    if (!hasFullAccess) {
                        WelcomeScreen(
                            onContinue = {
                                requestFullAccess.launch(
                                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")),
                                )
                            },
                        )
                    } else {
                        Box(Modifier.fillMaxSize()) {
                        Scaffold(
                            bottomBar = {
                                NavigationBar {
                                    Tab.entries.forEach { t ->
                                        NavigationBarItem(
                                            selected = tab == t,
                                            onClick = { tab = t },
                                            icon = {
                                                Icon(
                                                    when (t) {
                                                        Tab.HOME -> Icons.Default.Folder
                                                        Tab.SETTINGS -> Icons.Default.Settings
                                                    },
                                                    contentDescription = t.label,
                                                )
                                            },
                                            label = { Text(t.label) },
                                        )
                                    }
                                }
                            },
                            floatingActionButton = {
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    if (compressionBatch != null) {
                                        CompressionProgressFab(
                                            onOpenProgressDialog = {
                                                compressDialogStage = CompressDialogStage.Progress
                                                compressDialogVisible = true
                                            },
                                        )
                                    }
                                    if (tab == Tab.HOME && selected.isEmpty()) {
                                        RescanFab(
                                            isScanning = isScanning,
                                            onRescan = { fileBrowserViewModel.rescanCurrentDirectory() },
                                        )
                                    }
                                }
                            },
                        ) { padding ->
                            Box(Modifier.padding(padding).fillMaxSize()) {
                                when (tab) {
                                    Tab.HOME -> HomeScreen(
                                        viewModel = fileBrowserViewModel,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    Tab.SETTINGS -> SettingsScreen(
                                        onThemeModeChange = { themeMode = it },
                                    )
                                }
                                if (tab == Tab.HOME && selected.isNotEmpty()) {
                                    SelectionActionFab(
                                        selected = selected,
                                        compressEnabled = compressibleCount > 0,
                                        onCompressClick = {
                                            if (compressibleCount == 0) return@SelectionActionFab
                                            compressPreviewState = buildCompressPreviewState(context, selected)
                                            compressDialogStage = CompressDialogStage.Preview
                                            compressDialogVisible = true
                                        },
                                        onDeleteConfirmed = { fileBrowserViewModel.deleteSelected() },
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                    )
                                }
                            }
                        }

                        CompressionBatchDialog(
                            visible = compressDialogVisible,
                            stage = compressDialogStage,
                            previewState = compressPreviewState,
                            onDismissPreview = { compressDialogVisible = false },
                            onMinimizeProgress = { compressDialogVisible = false },
                            onStageChange = {
                                compressDialogStage = it
                                if (it == CompressDialogStage.Progress) {
                                    fileBrowserViewModel.clearSelected()
                                }
                            },
                            onCloseAfterBatch = {
                                compressDialogVisible = false
                                compressDialogStage = CompressDialogStage.Preview
                                compressPreviewState = null
                                fileBrowserViewModel.rescanCurrentDirectory()
                            },
                        )
                        }
                    }
                }
            }
        }
    }
}
