package com.akib.mycompanion.presentation.share

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akib.mycompanion.MainActivity
import com.akib.mycompanion.presentation.common.ObserveAsEvents
import com.akib.mycompanion.presentation.player.LinkStatus
import com.akib.mycompanion.presentation.player.VideoActionsCard
import com.akib.mycompanion.presentation.player.VideoActionsEvent
import com.akib.mycompanion.presentation.player.VideoActionsUiState
import com.akib.mycompanion.presentation.player.VideoActionsViewModel
import com.akib.mycompanion.ui.theme.CompanionTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Shows up in Android's share menu ("Share" in the YouTube app, or any text/link) and for
 * "Open with". A small sheet over whatever app you were in: Play now, Add to queue, Add to playlist.
 */
@AndroidEntryPoint
class ShareActivity : ComponentActivity() {

    private val viewModel: VideoActionsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Status bar sits on the dimmed app behind: light icons. Navigation bar sits on the sheet: follow the theme.
        val transparent = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(transparent),
            navigationBarStyle = SystemBarStyle.auto(transparent, transparent),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val shared = sharedText(intent)
            if (shared.isNullOrBlank()) {
                finish()
                return
            }
            viewModel.onSubmit(shared)
        }

        setContent {
            CompanionTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                ObserveAsEvents(viewModel.events) { event ->
                    when (event) {
                        is VideoActionsEvent.Message ->
                            Toast.makeText(applicationContext, event.text, Toast.LENGTH_SHORT).show()
                        is VideoActionsEvent.Done -> {
                            if (event.openPlayer) startActivity(MainActivity.openPlayerIntent(this@ShareActivity))
                            finish()
                        }
                    }
                }
                ShareSheet(
                    state = state,
                    onPlayNow = viewModel::onPlayNow,
                    onAddToQueue = viewModel::onAddToQueue,
                    onAddToPlaylist = viewModel::onAddToPlaylist,
                    onCreatePlaylistAndAdd = viewModel::onCreatePlaylistAndAdd,
                    onNewPlaylistNameChange = viewModel::onNewPlaylistNameChange,
                    onRetry = { viewModel.onSubmit() },
                    onClose = ::finish,
                )
            }
        }
    }

    companion object {
        /** The link from a share (EXTRA_TEXT) or an "Open with" (data URI). */
        fun sharedText(intent: Intent?): String? = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
    }
}

/** The bottom sheet; tapping outside closes it. Stateless so it can be tested. */
@Composable
fun ShareSheet(
    state: VideoActionsUiState,
    onPlayNow: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: (Long) -> Unit,
    onCreatePlaylistAndAdd: (String) -> Unit,
    onNewPlaylistNameChange: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            tonalElevation = 3.dp,
            modifier = Modifier
                .fillMaxWidth()
                // Swallow taps on the sheet itself so they don't close it.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Column(
                Modifier
                    .padding(20.dp)
                    .navigationBarsPadding()
                    // Edge-to-edge windows aren't resized for the keyboard: lift the sheet above it.
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("My Companion player", style = MaterialTheme.typography.titleMedium)
                when (val status = state.status) {
                    LinkStatus.Idle, LinkStatus.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                        Text("Getting video info…", modifier = Modifier.padding(start = 12.dp))
                    }
                    is LinkStatus.Error -> {
                        Text(status.message, color = MaterialTheme.colorScheme.error)
                        Row(Modifier.align(Alignment.End)) {
                            TextButton(onClick = onClose) { Text("Close") }
                            TextButton(onClick = onRetry) { Text("Try again") }
                        }
                    }
                    is LinkStatus.Ready -> VideoActionsCard(
                        video = status.video,
                        playlists = state.playlists,
                        newPlaylistError = state.newPlaylistError,
                        onPlayNow = onPlayNow,
                        onAddToQueue = onAddToQueue,
                        onAddToPlaylist = onAddToPlaylist,
                        onCreatePlaylistAndAdd = onCreatePlaylistAndAdd,
                        onNewPlaylistNameChange = onNewPlaylistNameChange,
                        onDismiss = onClose,
                    )
                }
            }
        }
    }
}
