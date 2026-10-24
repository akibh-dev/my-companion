package com.akib.mycompanion.domain.youtube

/** One audio stream YouTube offers for a video, reduced to what matters for choosing. */
data class AudioCandidate(
    val url: String,
    /** PROGRESSIVE_HTTP, DASH, HLS, … */
    val delivery: String,
    /** m4a, webm, opus, … */
    val format: String?,
    val bitrate: Int,
    /** ORIGINAL, DUBBED, DESCRIPTIVE, SECONDARY or null when the video has a single track. */
    val trackType: String? = null,
)

/**
 * Picks the stream to play: a plain HTTP file (ExoPlayer streams it like any audio URL),
 * the original-language track, the best quality, preferring m4a on ties (widest device support).
 */
object AudioStreamPicker {
    private const val PROGRESSIVE = "PROGRESSIVE_HTTP"

    fun pick(candidates: List<AudioCandidate>): AudioCandidate? =
        candidates
            .filter { it.delivery == PROGRESSIVE && it.url.startsWith("http") }
            .sortedWith(
                compareBy<AudioCandidate> { trackRank(it.trackType) }
                    .thenByDescending { it.bitrate }
                    .thenBy { if (it.format.equals("m4a", ignoreCase = true)) 0 else 1 },
            )
            .firstOrNull()

    private fun trackRank(type: String?): Int = when (type) {
        null, "ORIGINAL" -> 0
        "DUBBED", "SECONDARY" -> 1
        else -> 2
    }
}

/** When a googlevideo URL stops working (the `expire` query parameter, epoch seconds). */
object StreamExpiry {
    const val DEFAULT_LIFETIME_MILLIS = 5 * 60 * 60 * 1000L
    private val EXPIRE = Regex("""[?&]expire=(\d+)""")

    fun expiresAt(url: String, nowMillis: Long): Long =
        EXPIRE.find(url)?.groupValues?.get(1)?.toLongOrNull()?.times(1000) ?: (nowMillis + DEFAULT_LIFETIME_MILLIS)
}
