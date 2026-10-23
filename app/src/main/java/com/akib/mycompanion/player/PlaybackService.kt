package com.akib.mycompanion.player

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.akib.mycompanion.MainActivity
import com.akib.mycompanion.data.youtube.CachingStreamResolver
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Plays audio in the background like a music app: media notification, lock-screen and headset
 * controls, keeps going with the screen off, pauses when headphones are unplugged.
 * Media3 handles the foreground-service and notification lifecycle for us.
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var resolver: CachingStreamResolver

    private var session: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(TIMEOUT_MS)
            .setReadTimeoutMs(TIMEOUT_MS)
        val dataSources = ResolvingDataSource.Factory(http, YouTubeSpecResolver(resolver))

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(dataSources))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                // handleAudioFocus: pause for calls and other players
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(RetryExpiredStream(player, resolver))

        session = MediaSession.Builder(this, player)
            .setCallback(SessionCallback)
            .setSessionActivity(openPlayerIntent())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Swiping the app away keeps music playing; if nothing is playing, shut down. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private fun openPlayerIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        MainActivity.openPlayerIntent(this),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private object SessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(MediaItems.withPlayableUris(mediaItems).toMutableList())
    }

    private companion object {
        const val TIMEOUT_MS = 20_000
    }
}

/**
 * Stream URLs expire and can be revoked (HTTP 403). On such an error, drop the cached URL and
 * retry the same item once from where it stopped.
 */
class RetryExpiredStream(
    private val player: Player,
    private val resolver: CachingStreamResolver,
) : Player.Listener {
    private var retriedMediaId: String? = null

    override fun onPlayerError(error: PlaybackException) {
        val item = player.currentMediaItem ?: return
        if (retriedMediaId == item.mediaId) return
        retriedMediaId = item.mediaId
        resolver.invalidate(item.mediaId)
        player.prepare()
        player.play()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        retriedMediaId = null
    }
}
