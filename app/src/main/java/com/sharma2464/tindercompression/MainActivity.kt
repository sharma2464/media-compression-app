package com.sharma2464.tindercompression

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sharma2464.tindercompression.data.Decision
import com.sharma2464.tindercompression.data.FileEntry
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
                    val pickRoot = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                        uri?.let(viewModel::onRootPicked)
                    }
                    val entry by viewModel.current.collectAsState()
                    val rootPicked by viewModel.rootPicked.collectAsState()
                    var detailsEntry by remember { mutableStateOf<FileEntry?>(null) }

                    if (!rootPicked) {
                        WelcomeScreen(onChooseFolder = { pickRoot.launch(null) })
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
                        )
                    }

                    detailsEntry?.let { DetailsSheet(it, onDismiss = { detailsEntry = null }) }
                }
            }
        }
    }
}
