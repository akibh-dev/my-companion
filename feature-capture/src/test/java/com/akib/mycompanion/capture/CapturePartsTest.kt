package com.akib.mycompanion.capture

import com.akib.mycompanion.capture.data.CaptureFiles
import com.akib.mycompanion.capture.domain.Capture
import com.akib.mycompanion.capture.domain.CaptureType
import com.akib.mycompanion.capture.domain.VolumeComboDetector
import com.akib.mycompanion.capture.domain.VolumeComboDetector.Companion.VOLUME_DOWN
import com.akib.mycompanion.capture.domain.VolumeComboDetector.Companion.VOLUME_UP
import com.akib.mycompanion.capture.system.RecorderState
import com.akib.mycompanion.capture.system.RecordingStatus
import com.akib.mycompanion.capture.system.ScreenshotBridge
import com.akib.mycompanion.core.time.Clock
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import java.time.ZoneId
import org.junit.Test

class CapturePartsTest {

    // ---- Files ----

    private val dir = File(Files.createTempDirectory("captures").toFile(), "captures")
    private val files = CaptureFiles(dir)

    @Test
    fun `new files are named by kind and time, with the right extension, in their own folder`() {
        val voice = files.newFile(CaptureType.VOICE, 1_700_000_000_000)
        val photo = files.newFile(CaptureType.PHOTO, 1_700_000_000_000)
        val text = files.newFile(CaptureType.TEXT, 5)

        assertThat(dir.isDirectory).isTrue()
        assertThat(voice.parentFile).isEqualTo(dir)
        assertThat(voice.name).matches("voice-1700000000000-[0-9a-f-]{8}\\.m4a")
        assertThat(photo.name).endsWith(".jpg")
        assertThat(text.name).matches("text-5-.{8}\\.bin")
        assertThat(voice.name).isNotEqualTo(files.newFile(CaptureType.VOICE, 1_700_000_000_000).name)
    }

    @Test
    fun `a stored file name can never point outside the folder`() {
        assertThat(files.file("../../shared_prefs/secrets.xml")).isEqualTo(File(dir, "secrets.xml"))
        assertThat(files.file("/etc/passwd")).isEqualTo(File(dir, "passwd"))
        assertThat(files.file("voice-1.m4a")).isEqualTo(File(dir, "voice-1.m4a"))
    }

    // ---- Kinds and titles ----

    @Test
    fun `only voice, photo and screenshot captures have a file`() {
        assertThat(CaptureType.entries.filter { it.hasFile })
            .containsExactly(CaptureType.VOICE, CaptureType.PHOTO, CaptureType.SCREENSHOT)
        assertThat(CaptureType.SCREENSHOT.mime).isEqualTo("image/png")
        assertThat(CaptureType.VOICE.mime).isEqualTo("audio/mp4")
    }

    @Test
    fun `a long first line is cut for the title, blank lines skipped`() {
        val capture = Capture(type = CaptureType.TEXT, created = 0, text = "\n  ${"word ".repeat(30)}\nsecond")

        assertThat(capture.displayTitle).hasLength(60)
        assertThat(capture.displayTitle).startsWith("word word")
    }

    @Test
    fun `the transcript names a voice note when there's no title or text`() {
        val capture = Capture(type = CaptureType.VOICE, created = 0, transcript = "Call the plumber\nabout the leak")

        assertThat(capture.displayTitle).isEqualTo("Call the plumber")
        assertThat(Capture(type = CaptureType.CLIPBOARD, created = 0).displayTitle).isEqualTo("Clipboard")
        assertThat(Capture(type = CaptureType.VOICE, created = 0, title = "Plumber").displayTitle).isEqualTo("Plumber")
    }

    @Test
    fun `a summary is made from the typed text and the transcript`() {
        val both = Capture(type = CaptureType.VOICE, created = 0, text = "note", transcript = "spoken")

        assertThat(both.summarySource).isEqualTo("note\n\nspoken")
        assertThat(both.copy(text = " ").summarySource).isEqualTo("spoken")
        assertThat(Capture(type = CaptureType.PHOTO, created = 0).summarySource).isEmpty()
    }

    // ---- Recorder state and the screenshot bridge ----

    @Test
    fun `acknowledging clears a result but never a running recording`() {
        val status = RecordingStatus()
        assertThat(status.isRecording).isFalse()

        status.set(RecorderState.Recording(startedAt = 5))
        status.acknowledge()
        assertThat(status.isRecording).isTrue()

        status.set(RecorderState.Saved(id = 3))
        assertThat(status.isRecording).isFalse()
        status.acknowledge()
        assertThat(status.state.value).isEqualTo(RecorderState.Idle)

        status.set(RecorderState.Failed("no microphone"))
        status.acknowledge()
        assertThat(status.state.value).isEqualTo(RecorderState.Idle)
    }

    @Test
    fun `screenshots work only while the service is attached`() {
        val bridge = ScreenshotBridge()
        var taken = 0
        assertThat(bridge.available.value).isFalse()
        assertThat(bridge.request()).isFalse()

        bridge.attach { taken++ }
        assertThat(bridge.available.value).isTrue()
        assertThat(bridge.request()).isTrue()
        assertThat(taken).isEqualTo(1)

        bridge.detach()
        assertThat(bridge.available.value).isFalse()
        assertThat(bridge.request()).isFalse()
        assertThat(taken).isEqualTo(1)
    }

    // ---- Volume combo timing ----

    private val clock = object : Clock {
        var now = 0L
        override fun nowMillis() = now
        override val zone: ZoneId = ZoneId.of("Asia/Dhaka")
    }

    @Test
    fun `it says how long until a held combo fires`() {
        val detector = VolumeComboDetector(clock)
        assertThat(detector.millisUntilFire(0)).isNull()

        detector.onKey(VOLUME_UP, down = true, time = 100)
        assertThat(detector.millisUntilFire(150)).isNull()

        detector.onKey(VOLUME_DOWN, down = true, time = 200)
        assertThat(detector.bothHeldSince()).isEqualTo(200)
        assertThat(detector.millisUntilFire(500)).isEqualTo(700)
        assertThat(detector.millisUntilFire(5_000)).isEqualTo(0)

        assertThat(detector.check(1_200)).isTrue()
        assertThat(detector.millisUntilFire(1_300)).isNull()
    }

    @Test
    fun `auto-repeated key downs don't restart the hold`() {
        val detector = VolumeComboDetector(clock)

        detector.onKey(VOLUME_UP, down = true, time = 0)
        detector.onKey(VOLUME_DOWN, down = true, time = 0)
        (50L..950L step 50).forEach { t ->
            assertThat(detector.onKey(VOLUME_UP, down = true, time = t)).isFalse()
        }

        assertThat(detector.onKey(VOLUME_DOWN, down = true, time = 1_000)).isTrue()
    }

    @Test
    fun `check uses the clock when no time is given, and a custom hold length`() {
        val detector = VolumeComboDetector(clock, holdMillis = 300)
        clock.now = 10
        detector.onKey(VOLUME_UP, down = true, time = 10)
        detector.onKey(VOLUME_DOWN, down = true, time = 10)

        clock.now = 300
        assertThat(detector.check()).isFalse()
        clock.now = 310
        assertThat(detector.check()).isTrue()
    }
}
