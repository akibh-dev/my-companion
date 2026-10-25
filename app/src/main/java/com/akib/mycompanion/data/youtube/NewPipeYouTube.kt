package com.akib.mycompanion.data.youtube

import com.akib.mycompanion.di.IoDispatcher
import com.akib.mycompanion.domain.model.Video
import com.akib.mycompanion.domain.port.VideoInfoSource
import com.akib.mycompanion.domain.time.Clock
import com.akib.mycompanion.domain.youtube.AudioCandidate
import com.akib.mycompanion.domain.youtube.AudioStreamPicker
import com.akib.mycompanion.domain.youtube.StreamExpiry
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.stream.StreamInfo

/** A playable audio URL plus the HTTP headers YouTube expects with it. */
data class ResolvedStream(
    val url: String,
    val headers: Map<String, String>,
    val expiresAtMillis: Long,
)

/** Blocking: called from the player's loading thread. */
interface AudioStreamSource {
    @Throws(IOException::class)
    fun audioStream(videoId: String): ResolvedStream
}

/** YouTube via NewPipeExtractor (no API key, no Google account). */
@Singleton
class NewPipeYouTube @Inject constructor(
    private val downloader: OkHttpDownloader,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher,
) : VideoInfoSource, AudioStreamSource {

    override suspend fun fetch(videoId: String): Video = withContext(io) {
        val info = info(videoId)
        Video(
            id = videoId,
            title = info.name,
            author = info.uploaderName.orEmpty(),
            durationSeconds = info.duration,
            thumbnailUrl = info.thumbnails.maxByOrNull { it.height }?.url,
        )
    }

    override fun audioStream(videoId: String): ResolvedStream {
        val candidates = info(videoId).audioStreams.map { stream ->
            AudioCandidate(
                url = if (stream.isUrl) stream.content else "",
                delivery = stream.deliveryMethod.name,
                format = stream.format?.suffix,
                bitrate = stream.averageBitrate,
                trackType = stream.audioTrackType?.name,
            )
        }
        val chosen = AudioStreamPicker.pick(candidates) ?: throw YouTubeException("No playable audio for this video")
        return ResolvedStream(
            url = chosen.url,
            headers = StreamHeaders.forUrl(chosen.url),
            expiresAtMillis = StreamExpiry.expiresAt(chosen.url, clock.nowMillis()),
        )
    }

    private fun info(videoId: String): StreamInfo {
        ensureInitialised()
        return try {
            StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
        } catch (e: ExtractionException) {
            throw YouTubeException.from(e)
        } catch (e: IOException) {
            throw YouTubeException.from(e)
        }
    }

    private fun ensureInitialised() {
        synchronized(NewPipeYouTube::class.java) {
            if (!initialised) {
                NewPipe.init(downloader)
                initialised = true
            }
        }
    }

    private companion object {
        @Volatile
        var initialised = false
    }
}

/**
 * googlevideo URLs are tied to the YouTube client that requested them (`c=ANDROID`, `c=IOS`, …);
 * the request must look like that client or YouTube answers 403.
 */
object StreamHeaders {
    private const val ORIGIN = "https://www.youtube.com"

    fun forUrl(url: String): Map<String, String> = when {
        YoutubeParsingHelper.isAndroidStreamingUrl(url) ->
            mapOf("User-Agent" to YoutubeParsingHelper.getAndroidUserAgent(null))
        YoutubeParsingHelper.isIosStreamingUrl(url) ->
            mapOf("User-Agent" to YoutubeParsingHelper.getIosUserAgent(null))
        YoutubeParsingHelper.isVisionOsStreamingUrl(url) ->
            mapOf("User-Agent" to YoutubeParsingHelper.getVisionOsUserAgent(null))
        else -> mapOf(
            "User-Agent" to OkHttpDownloader.USER_AGENT,
            "Origin" to ORIGIN,
            "Referer" to "$ORIGIN/",
        )
    }
}

/**
 * Stream URLs live a few hours. Resolving one costs a network round trip, so keep them until
 * shortly before they expire. Thread-safe: the player may resolve the next item while one plays.
 */
@Singleton
class CachingStreamResolver @Inject constructor(
    private val source: AudioStreamSource,
    private val clock: Clock,
) {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, ResolvedStream>()

    @Throws(IOException::class)
    fun resolve(videoId: String): ResolvedStream {
        cache[videoId]?.takeIf { it.expiresAtMillis - SAFETY_MARGIN_MILLIS > clock.nowMillis() }?.let { return it }
        return source.audioStream(videoId).also { cache[videoId] = it }
    }

    /** Forget a URL that stopped working (e.g. 403), so the next attempt resolves a fresh one. */
    fun invalidate(videoId: String) {
        cache.remove(videoId)
    }

    private companion object {
        const val SAFETY_MARGIN_MILLIS = 10 * 60 * 1000L
    }
}
