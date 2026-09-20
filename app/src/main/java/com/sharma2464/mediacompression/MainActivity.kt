package com.sharma2464.mediacompression

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sharma2464.mediacompression.scan.hasFullStorageAccess
import com.sharma2464.mediacompression.settings.AppSettings
import com.sharma2464.mediacompression.settings.SettingsScreen
import com.sharma2464.mediacompression.ui.BrowserLoadState
import com.sharma2464.mediacompression.ui.CompressionProgressFab
import com.sharma2464.mediacompression.ui.FileBrowserViewModel
import com.sharma2464.mediacompression.ui.HomeScreen
import com.sharma2464.mediacompression.ui.RescanFab
import com.sharma2464.mediacompression.ui.WelcomeScreen
import com.sharma2464.mediacompression.ui.theme.MediaCompressionTheme

private enum class Tab(val label: String, val glyph: String) {
    HOME("Files", "📁"),
    SETTINGS("Settings", "⚙️"),
}

class MainActivity : ComponentActivity() {
    private val fileBrowserViewModel: FileBrowserViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                FileBrowserViewModel(applicationContext) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appSettings = AppSettings(this)
        setContent {
            var themeMode by remember { mutableStateOf(appSettings.themeMode) }
            MediaCompressionTheme(themeMode = themeMode) {
                Surface {
                    val context = LocalContext.current
                    var hasFullAccess by remember { mutableStateOf(hasFullStorageAccess()) }
                    val requestFullAccess = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                        hasFullAccess = hasFullStorageAccess()
                    }
                    var tab by remember { mutableStateOf(Tab.HOME) }
                    val loadState by fileBrowserViewModel.loadState.collectAsState()
                    val isScanning = loadState is BrowserLoadState.Scanning

                    LaunchedEffect(Unit) {
                        hasFullAccess = hasFullStorageAccess()
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
                        Scaffold(
                            bottomBar = {
                                NavigationBar {
                                    Tab.entries.forEach { t ->
                                        NavigationBarItem(
                                            selected = tab == t,
                                            onClick = { tab = t },
                                            icon = { Text(t.glyph) },
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
                                    CompressionProgressFab()
                                    if (tab == Tab.HOME) {
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
                            }
                        }
                    }
                }
            }
        }
    }
}
