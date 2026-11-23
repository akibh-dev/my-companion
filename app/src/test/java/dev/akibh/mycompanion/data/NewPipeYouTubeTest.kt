package dev.akibh.mycompanion.data

import com.google.common.truth.Truth.assertThat
import dev.akibh.mycompanion.core.log.AppLog
import dev.akibh.mycompanion.core.log.LogLevel
import dev.akibh.mycompanion.data.youtube.NewPipeYouTube
import dev.akibh.mycompanion.data.youtube.YouTubeException
import dev.akibh.mycompanion.fakes.FakeClock
import java.nio.file.Files
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.stream.StreamInfo

class NewPipeYouTubeTest {

    private val log = AppLog(Files.createTempDirectory("log").toFile(), mirrorToLogcat = false)

    private fun youTube(extract: (String) -> StreamInfo) = NewPipeYouTube(
        FakeClock(),
        Dispatchers.Unconfined,
        log,
        extract
    )

    @Test
    fun `a RuntimeException from the extractor becomes a YouTube error instead of a crash`() = runTest {
        val youTube = youTube { throw NullPointerException("ytInitialData was null") }

        val fetchError = runCatching { youTube.fetch("dQw4w9WgXcQ") }.exceptionOrNull()
        val streamError = runCatching { youTube.audioStream("dQw4w9WgXcQ") }.exceptionOrNull()

        assertThat(fetchError).isInstanceOf(YouTubeException::class.java)
        assertThat(fetchError).hasMessageThat().contains("the app needs an update")
        assertThat(fetchError).hasCauseThat().isInstanceOf(NullPointerException::class.java)
        assertThat(streamError).isInstanceOf(YouTubeException::class.java)
    }

    @Test
    fun `extractor failures are logged with the video id`() = runTest {
        val youTube = youTube { throw PrivateContentException("private") }

        val error = runCatching { youTube.fetch("dQw4w9WgXcQ") }.exceptionOrNull()

        assertThat(error).hasMessageThat().isEqualTo("This video is private")
        val line = log.read().single()
        assertThat(line.level).isEqualTo(LogLevel.WARN)
        assertThat(line.tag).isEqualTo("YouTube")
        assertThat(line.message).contains("dQw4w9WgXcQ")
        assertThat(line.message).contains("PrivateContentException")
    }

    @Test
    fun `cancellation passes through untouched`() = runTest {
        val youTube = youTube { throw CancellationException("player stopped") }

        val error = runCatching { youTube.audioStream("dQw4w9WgXcQ") }.exceptionOrNull()

        assertThat(error).isInstanceOf(CancellationException::class.java)
        assertThat(log.read()).isEmpty()
    }
}
