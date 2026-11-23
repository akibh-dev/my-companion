package dev.akibh.mycompanion.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommands
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.akibh.mycompanion.domain.youtube.YouTubeLinks

/**
 * Who may do what with the player. Any app can bind to a media session service, so without this
 * every app could read what's playing, replace the queue and write titles and artwork into the
 * listening history.
 *
 * - This app (its screens, its media notification) and trusted controllers (System UI, Bluetooth,
 *   Android Auto, apps with notification access: whatever Android itself vouches for) get everything.
 * - Any other app can only play, pause and seek. It doesn't see what's playing or the queue, and
 *   gets no custom commands.
 */
@OptIn(UnstableApi::class)
class PlayerSessionCallback(private val ownPackage: String) : MediaSession.Callback {

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult = if (hasFullAccess(controller)) {
        MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
    } else {
        MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(SessionCommands.EMPTY)
            .setAvailablePlayerCommands(LIMITED_COMMANDS)
            .build()
    }

    fun hasFullAccess(controller: MediaSession.ControllerInfo): Boolean =
        controller.packageName == ownPackage || controller.isTrusted

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
    ): ListenableFuture<MutableList<MediaItem>> =
        Futures.immediateFuture(MediaItems.withPlayableUris(mediaItems).toMutableList())

    /** Like the default, but the start index still points at the same item after bad ones are dropped. */
    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val kept = MediaItems.withPlayableUris(mediaItems)
        val startKept = startIndex in mediaItems.indices && YouTubeLinks.isVideoId(mediaItems[startIndex].mediaId)
        val result = when {
            startIndex == C.INDEX_UNSET -> MediaSession.MediaItemsWithStartPosition(
                kept,
                C.INDEX_UNSET,
                startPositionMs
            )
            startKept -> {
                // The same item, counted among the ones kept before it.
                val index = mediaItems.subList(0, startIndex).count { YouTubeLinks.isVideoId(it.mediaId) }
                MediaSession.MediaItemsWithStartPosition(kept, index, startPositionMs)
            }
            else -> MediaSession.MediaItemsWithStartPosition(kept, 0, C.TIME_UNSET)
        }
        return Futures.immediateFuture(result)
    }

    companion object {
        /** What other apps may do: play/pause and moving around in what's already queued. */
        val LIMITED_COMMANDS: Player.Commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_BACK,
                Player.COMMAND_SEEK_FORWARD,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            )
            .build()
    }
}
