package com.akib.mycompanion.domain

import com.akib.mycompanion.domain.youtube.AudioCandidate
import com.akib.mycompanion.domain.youtube.AudioStreamPicker
import com.akib.mycompanion.domain.youtube.StreamExpiry
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AudioStreamPickerTest {

    private fun stream(
        url: String,
        bitrate: Int,
        format: String = "webm",
        delivery: String = "PROGRESSIVE_HTTP",
        track: String? = null,
    ) = AudioCandidate(url = url, delivery = delivery, format = format, bitrate = bitrate, trackType = track)

    @Test
    fun `picks the best progressive stream`() {
        val best = stream("https://a/160", 160_000, format = "webm")
        val picked = AudioStreamPicker.pick(
            listOf(
                stream("https://a/48", 48_000),
                best,
                stream("https://a/128", 128_000, format = "m4a"),
            ),
        )
        assertThat(picked).isEqualTo(best)
    }

    @Test
    fun `prefers m4a when quality is equal`() {
        val m4a = stream("https://a/m4a", 128_000, format = "m4a")
        assertThat(AudioStreamPicker.pick(listOf(stream("https://a/webm", 128_000), m4a))).isEqualTo(m4a)
    }

    @Test
    fun `ignores DASH, HLS and non-URL streams`() {
        val ok = stream("https://a/ok", 64_000)
        val picked = AudioStreamPicker.pick(
            listOf(
                stream("https://a/dash", 256_000, delivery = "DASH"),
                stream("https://a/hls", 256_000, delivery = "HLS"),
                stream("", 256_000),
                ok,
            ),
        )
        assertThat(picked).isEqualTo(ok)
    }

    @Test
    fun `prefers the original language over dubbed or descriptive tracks`() {
        val original = stream("https://a/orig", 128_000, track = "ORIGINAL")
        val picked = AudioStreamPicker.pick(
            listOf(
                stream("https://a/dub", 160_000, track = "DUBBED"),
                stream("https://a/desc", 160_000, track = "DESCRIPTIVE"),
                original,
            ),
        )
        assertThat(picked).isEqualTo(original)
    }

    @Test
    fun `nothing playable means null`() {
        assertThat(AudioStreamPicker.pick(emptyList())).isNull()
        assertThat(AudioStreamPicker.pick(listOf(stream("https://a/dash", 1, delivery = "DASH")))).isNull()
    }

    @Test
    fun `expiry comes from the URL or defaults to a few hours`() {
        val url = "https://rr1.googlevideo.com/videoplayback?expire=1793000000&ei=abc"
        assertThat(StreamExpiry.expiresAt(url, nowMillis = 0)).isEqualTo(1_793_000_000_000L)
        assertThat(StreamExpiry.expiresAt("https://x/y?a=b", nowMillis = 1_000))
            .isEqualTo(1_000 + StreamExpiry.DEFAULT_LIFETIME_MILLIS)
    }
}
