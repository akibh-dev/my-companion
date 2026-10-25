package com.akib.mycompanion.data

import com.akib.mycompanion.data.youtube.AudioStreamSource
import com.akib.mycompanion.data.youtube.CachingStreamResolver
import com.akib.mycompanion.data.youtube.OkHttpDownloader
import com.akib.mycompanion.data.youtube.ResolvedStream
import com.akib.mycompanion.data.youtube.StreamHeaders
import com.akib.mycompanion.data.youtube.YouTubeException
import com.akib.mycompanion.fakes.FakeClock
import com.google.common.truth.Truth.assertThat
import java.net.UnknownHostException
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import org.schabi.newpipe.extractor.exceptions.YoutubeMusicPremiumContentException

class YouTubeDataTest {

    // ---- Error wording (device test finding #8) ----

    @Test
    fun `extractor errors become plain sentences, most specific first`() {
        fun say(error: Exception) = YouTubeException.from(error).message

        assertThat(say(PrivateContentException("private"))).isEqualTo("This video is private")
        assertThat(say(AgeRestrictedContentException("age"))).contains("age-restricted")
        assertThat(say(GeographicRestrictionException("geo"))).contains("your country")
        assertThat(say(YoutubeMusicPremiumContentException())).contains("Premium")
        assertThat(say(ContentNotAvailableException("Video unavailable"))).contains("isn't available")
        assertThat(say(SignInConfirmNotBotException("bot"))).contains("not a bot")
        assertThat(say(ParsingException("JSON response is too short"))).contains("Check the link")
        assertThat(say(UnknownHostException("www.youtube.com"))).contains("internet connection")
    }

    @Test
    fun `the extractor's own message is kept as the cause`() {
        val raw = ParsingException("JSON response is too short")

        assertThat(YouTubeException.from(raw).cause).isSameInstanceAs(raw)
    }

    // ---- Stream URL cache ----

    private val clock = FakeClock(now = 1_000_000)
    private val source = object : AudioStreamSource {
        var calls = 0

        override fun audioStream(videoId: String): ResolvedStream {
            calls++
            return ResolvedStream("https://audio/$videoId/$calls", emptyMap(), expiresAtMillis = clock.now + HOUR)
        }
    }
    private val resolver = CachingStreamResolver(source, clock)

    @Test
    fun `a resolved stream is reused until shortly before it expires`() {
        val first = resolver.resolve("abc")
        clock.now += 30 * MINUTE
        assertThat(resolver.resolve("abc")).isSameInstanceAs(first)

        clock.now += 25 * MINUTE // within the 10 minute safety margin
        assertThat(resolver.resolve("abc").url).isEqualTo("https://audio/abc/2")
        assertThat(source.calls).isEqualTo(2)
    }

    @Test
    fun `invalidate forces a fresh lookup`() {
        resolver.resolve("abc")
        resolver.invalidate("abc")

        assertThat(resolver.resolve("abc").url).isEqualTo("https://audio/abc/2")
    }

    @Test
    fun `each video is cached separately`() {
        resolver.resolve("a")
        resolver.resolve("b")
        resolver.resolve("a")

        assertThat(source.calls).isEqualTo(2)
    }

    // ---- Headers for googlevideo URLs ----

    @Test
    fun `stream requests imitate the YouTube client that issued the URL`() {
        val android = StreamHeaders.forUrl("https://rr.googlevideo.com/videoplayback?id=1&c=ANDROID&x=y")
        assertThat(android["User-Agent"]).startsWith("com.google.android.youtube/")

        val ios = StreamHeaders.forUrl("https://rr.googlevideo.com/videoplayback?id=1&c=IOS&x=y")
        assertThat(ios["User-Agent"]).startsWith("com.google.ios.youtube/")

        val web = StreamHeaders.forUrl("https://rr.googlevideo.com/videoplayback?id=1&c=WEB&x=y")
        assertThat(web["User-Agent"]).isEqualTo(OkHttpDownloader.USER_AGENT)
        assertThat(web["Origin"]).isEqualTo("https://www.youtube.com")
        assertThat(web["Referer"]).isEqualTo("https://www.youtube.com/")
    }

    // ---- HTTP client used by NewPipeExtractor ----

    private val server = MockWebServer()
    private val downloader = OkHttpDownloader(OkHttpClient())

    @After
    fun stopServer() = server.shutdown()

    @Test
    fun `GET passes headers through and returns body and final URL`() {
        server.enqueue(MockResponse().setBody("hello").addHeader("X-Test", "1"))

        val response = downloader.execute(
            Request.newBuilder()
                .get(server.url("/watch").toString())
                .addHeader("Accept-Language", "en")
                .build(),
        )

        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("GET")
        assertThat(recorded.getHeader("Accept-Language")).isEqualTo("en")
        assertThat(recorded.getHeader("User-Agent")).isEqualTo(OkHttpDownloader.USER_AGENT)
        assertThat(response.responseCode()).isEqualTo(200)
        assertThat(response.responseBody()).isEqualTo("hello")
        assertThat(response.getHeader("X-Test")).isEqualTo("1")
        assertThat(response.latestUrl()).isEqualTo(server.url("/watch").toString())
    }

    @Test
    fun `POST sends the body and may override the user agent`() {
        server.enqueue(MockResponse().setBody("{}"))

        downloader.execute(
            Request.newBuilder()
                .post(server.url("/youtubei/v1/player").toString(), """{"videoId":"x"}""".toByteArray())
                .setHeader("User-Agent", "custom")
                .build(),
        )

        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("POST")
        assertThat(recorded.body.readUtf8()).isEqualTo("""{"videoId":"x"}""")
        assertThat(recorded.headers.values("User-Agent")).containsExactly("custom")
    }

    @Test
    fun `rate limiting becomes a reCAPTCHA error`() {
        server.enqueue(MockResponse().setResponseCode(429))

        assertThrows(ReCaptchaException::class.java) {
            downloader.execute(Request.newBuilder().get(server.url("/").toString()).build())
        }
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}
