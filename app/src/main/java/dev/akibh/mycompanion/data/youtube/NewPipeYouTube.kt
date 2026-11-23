package dev.akibh.mycompanion.data.youtube

import dev.akibh.mycompanion.core.log.AppLog
import dev.akibh.mycompanion.di.IoDispatcher
import dev.akibh.mycompanion.domain.model.Video
import dev.akibh.mycompanion.domain.port.VideoInfoSource
import dev.akibh.mycompanion.domain.time.Clock
import dev.akibh.mycompanion.domain.youtube.AudioCandidate
import dev.akibh.mycompanion.domain.youtube.AudioStreamPicker
import dev.akibh.mycompanion.domain.youtube.StreamExpiry
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
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
class NewPipeYouTube internal constructor(
    private val clock: Clock,
    private val io: CoroutineDispatcher,
    private val log: AppLog,
    /** Reads a watch page; swapped in tests, since NewPipe's entry point is static. */
    private val extract: (url: String) -> StreamInfo,
) : VideoInfoSource, AudioStreamSource {

    @Inject constructor(
        downloader: OkHttpDownloader,
        clock: Clock,
        @IoDispatcher io: CoroutineDispatcher,
        log: AppLog,
    ) : this(clock, io, log, { url ->
        ensureInitialised(downloader)
        StreamInfo.getInfo(ServiceList.YouTube, url)
    })

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

    /**
     * Every extractor failure becomes a [YouTubeException] (an IOException the player and screens
     * already handle). A RuntimeException is what a YouTube page change usually looks like; left
     * alone it would end the process from the player's loading thread.
     */
    private fun info(videoId: String): StreamInfo = try {
        extract("https://www.youtube.com/watch?v=$videoId")
    } catch (e: YouTubeException) {
        throw e
    } catch (e: ExtractionException) {
        throw failed(videoId, e)
    } catch (e: IOException) {
        throw failed(videoId, e)
    } catch (e: CancellationException) {
        // A RuntimeException too, but it means "stop", not "YouTube changed".
        throw e
    } catch (e: RuntimeException) {
        throw failed(videoId, e)
    }

    private fun failed(videoId: String, error: Exception): YouTubeException {
        log.w(TAG, "couldn't read $videoId", error)
        return YouTubeException.from(error)
    }

    private companion object {
        const val TAG = "YouTube"

        @Volatile
        var initialised = false

        fun ensureInitialised(downloader: OkHttpDownloader) {
            synchronized(NewPipeYouTube::class.java) {
                if (!initialised) {
                    NewPipe.init(downloader)
                    initialised = true
                }
            }
        }
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
