package com.akib.mycompanion.player

import androidx.media3.common.PlaybackException
import com.akib.mycompanion.data.youtube.YouTubeException

/** Player errors in words; an error code name like ERROR_CODE_IO_UNSPECIFIED means nothing to a person. */
object PlaybackErrors {
    private val network = setOf(
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    )
    private val refused = setOf(
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
    )
    private val format = setOf(
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
    )

    fun message(error: PlaybackException): String {
        // Looking the stream up failed (private video, no network…): already worded by the data layer.
        (error.cause as? YouTubeException)?.message?.let { return it }
        return when (error.errorCode) {
            in network -> "No internet connection. Press play to try again"
            in refused -> "YouTube refused the audio stream. Press play to try again"
            in format -> "This audio format can't be played on this phone"
            else -> "Couldn't play this (${error.errorCodeName})"
        }
    }
}
