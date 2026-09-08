package com.sharma2464.tindercompression

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.sharma2464.tindercompression.data.Decision
import com.sharma2464.tindercompression.data.FileEntry
import com.sharma2464.tindercompression.scan.hasFullStorageAccess
import com.sharma2464.tindercompression.ui.CompareScreen
import com.sharma2464.tindercompression.ui.DetailsSheet
import com.sharma2464.tindercompression.ui.FilePreview
import com.sharma2464.tindercompression.ui.ReviewViewModel
import com.sharma2464.tindercompression.ui.SwipeDirection
import com.sharma2464.tindercompression.ui.SwipeScreen
import com.sharma2464.tindercompression.ui.WelcomeScreen

class MainActivity : ComponentActivity() {
    private val viewModel: ReviewViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    val context = LocalContext.current
                    val pickRoot = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                        uri?.let(viewModel::onRootPicked)
                    }
                    var hasFullAccess by remember { mutableStateOf(hasFullStorageAccess()) }
                    val requestFullAccess = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                        hasFullAccess = hasFullStorageAccess()
                    }
                    val entry by viewModel.current.collectAsState()
                    val completed by viewModel.completed.collectAsState()
                    val rootPicked by viewModel.rootPicked.collectAsState()
                    val rootRejectedMessage by viewModel.rootRejectedMessage.collectAsState()
                    var detailsEntry by remember { mutableStateOf<FileEntry?>(null) }
                    var compareEntry by remember { mutableStateOf<FileEntry?>(null) }

                    LaunchedEffect(rootRejectedMessage) {
                        rootRejectedMessage?.let {
                            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                            viewModel.clearRootRejectedMessage()
                        }
                    }

                    if (!rootPicked) {
                        WelcomeScreen(
                            onChooseFolder = { pickRoot.launch(null) },
                            hasFullStorageAccess = hasFullAccess,
                            onGrantFullStorageAccess = {
                                requestFullAccess.launch(
                                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")),
                                )
                            },
                        )
                    } else {
                        SwipeScreen(
                            entry = entry,
                            preview = { FilePreview(it) },
                            onSwiped = { direction ->
                                entry?.let { e ->
                                    val decision = when (direction) {
                                        SwipeDirection.LEFT -> Decision.KEEP
                                        SwipeDirection.RIGHT -> Decision.COMPRESS
                                        SwipeDirection.UP -> Decision.LATER
                                        SwipeDirection.DOWN -> return@let
                                    }
                                    viewModel.onDecision(e, decision)
                                }
                            },
                            onShowDetails = { detailsEntry = it },
                            onPickAnotherFolder = { pickRoot.launch(null) },
                            compressionMode = viewModel.settings.compressionMode,
                            completed = completed,
                            onOpenCompare = { compareEntry = it },
                        )
                    }

                    detailsEntry?.let { DetailsSheet(it, onDismiss = { detailsEntry = null }) }
                    compareEntry?.let { entry ->
                        viewModel.settings.rootTreeUri?.let { rootUri ->
                            CompareScreen(entry, Uri.parse(rootUri), onDismiss = { compareEntry = null })
                        }
                    }
                }
            }
        }
    }
}
