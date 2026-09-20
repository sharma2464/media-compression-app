package com.sharma2464.mediacompression

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.sharma2464.mediacompression.data.Decision
import com.sharma2464.mediacompression.scan.hasFullStorageAccess
import com.sharma2464.mediacompression.settings.SettingsScreen
import com.sharma2464.mediacompression.ui.CompressionProgressFab
import com.sharma2464.mediacompression.ui.GalleryScreen
import com.sharma2464.mediacompression.ui.HomeScreen
import com.sharma2464.mediacompression.ui.ReviewViewModel
import com.sharma2464.mediacompression.ui.WelcomeScreen

private enum class Tab(val label: String, val glyph: String) {
    HOME("Files", "📁"),
    SETTINGS("Settings", "⚙️"),
}

class MainActivity : ComponentActivity() {
    private val viewModel: ReviewViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    val context = LocalContext.current
                    var hasFullAccess by remember { mutableStateOf(hasFullStorageAccess()) }
                    val requestFullAccess = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                        hasFullAccess = hasFullStorageAccess()
                    }
                    var tab by remember { mutableStateOf(Tab.HOME) }

                    LaunchedEffect(Unit) {
                        // Re-check permission on resume in case user granted it in Settings
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
                            floatingActionButton = { CompressionProgressFab() },
                        ) { padding ->
                            Box(Modifier.padding(padding).fillMaxSize()) {
                                when (tab) {
                                    Tab.HOME -> HomeScreen(
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    Tab.SETTINGS -> SettingsScreen()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
