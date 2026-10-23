package com.akib.mycompanion.player

import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.akib.mycompanion.data.youtube.CachingStreamResolver
import com.akib.mycompanion.domain.model.Video

/**
 * Queue items carry a `ytaudio:<videoId>` URI. The real (expiring) stream URL is only looked up
 * when ExoPlayer is about to load the item, see [YouTubeSpecResolver].
 */
object MediaItems {
    const val SCHEME = "ytaudio"
    private const val EXTRA_DURATION = "duration_seconds"

    fun uriFor(videoId: String): Uri = Uri.parse("$SCHEME:$videoId")

    fun videoId(uri: Uri?): String? =
        uri?.takeIf { it.scheme == SCHEME }?.schemeSpecificPart?.takeIf { it.isNotEmpty() }

    fun from(video: Video): MediaItem = MediaItem.Builder()
        .setMediaId(video.id)
        .setUri(uriFor(video.id))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(video.title)
                .setArtist(video.author.ifBlank { null })
                .setArtworkUri(video.thumbnailUrl?.let(Uri::parse))
                .setExtras(Bundle().apply { putLong(EXTRA_DURATION, video.durationSeconds) })
                .build(),
        )
        .build()

    fun toVideo(item: MediaItem): Video {
        val metadata = item.mediaMetadata
        return Video(
            id = item.mediaId,
            title = metadata.title?.toString() ?: "YouTube video",
            author = metadata.artist?.toString().orEmpty(),
            durationSeconds = metadata.extras?.getLong(EXTRA_DURATION) ?: 0,
            thumbnailUrl = metadata.artworkUri?.toString(),
        )
    }

    /**
     * Media3 strips URIs from items a controller sends to the session (so other apps can't make
     * the service load arbitrary URLs). Rebuild them from the media id.
     */
    fun withPlayableUris(items: List<MediaItem>): List<MediaItem> =
        items.map { item -> item.buildUpon().setUri(uriFor(item.mediaId)).build() }
}

/** Swaps `ytaudio:<id>` for the current googlevideo URL, on ExoPlayer's loading thread. */
@OptIn(UnstableApi::class)
class YouTubeSpecResolver(private val resolver: CachingStreamResolver) : ResolvingDataSource.Resolver {
    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val videoId = MediaItems.videoId(dataSpec.uri) ?: return dataSpec
        val stream = resolver.resolve(videoId)
        return dataSpec.buildUpon()
            .setUri(Uri.parse(stream.url))
            .setHttpRequestHeaders(dataSpec.httpRequestHeaders + stream.headers)
            .build()
    }
}
