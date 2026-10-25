package com.akib.mycompanion.capture

import com.akib.mycompanion.capture.domain.Capture
import com.akib.mycompanion.capture.domain.CaptureFilter
import com.akib.mycompanion.capture.domain.CaptureSearch
import com.akib.mycompanion.capture.domain.CaptureType
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

class CaptureSearchTest {

    private val zone = ZoneId.of("Asia/Dhaka")

    private fun at(day: Int, hour: Int) =
        ZonedDateTime.of(2026, 10, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()

    private val note = Capture(id = 1, type = CaptureType.TEXT, created = at(24, 9), text = "Buy milk and eggs")
    private val voice = Capture(
        id = 2,
        type = CaptureType.VOICE,
        created = at(25, 8),
        file = "voice.m4a",
        transcript = "Call the plumber about the kitchen sink",
    )
    private val photo =
        Capture(id = 3, type = CaptureType.PHOTO, created = at(25, 23), file = "p.jpg", title = "Receipt")
    private val clip = Capture(
        id = 4,
        type = CaptureType.CLIPBOARD,
        created = at(23, 12),
        text = "https://example.com",
        summary = "A link about Milk prices",
        actions = listOf("Compare shops"),
    )
    private val all = listOf(note, voice, photo, clip)

    @Test
    fun `newest first without a filter`() {
        assertThat(CaptureSearch.apply(all, CaptureFilter())).containsExactly(photo, voice, note, clip).inOrder()
    }

    @Test
    fun `search looks in text, transcript, title, summary and actions, ignoring case`() {
        assertThat(CaptureSearch.apply(all, CaptureFilter(query = "milk"))).containsExactly(note, clip).inOrder()
        assertThat(CaptureSearch.apply(all, CaptureFilter(query = "PLUMBER"))).containsExactly(voice)
        assertThat(CaptureSearch.apply(all, CaptureFilter(query = "receipt"))).containsExactly(photo)
        assertThat(CaptureSearch.apply(all, CaptureFilter(query = "compare"))).containsExactly(clip)
    }

    @Test
    fun `every word must match`() {
        assertThat(CaptureSearch.apply(all, CaptureFilter(query = "kitchen  sink"))).containsExactly(voice)
        assertThat(CaptureSearch.apply(all, CaptureFilter(query = "kitchen milk"))).isEmpty()
    }

    @Test
    fun `type filter combines with search`() {
        assertThat(CaptureSearch.apply(all, CaptureFilter(type = CaptureType.VOICE))).containsExactly(voice)
        assertThat(CaptureSearch.apply(all, CaptureFilter(query = "milk", type = CaptureType.TEXT)))
            .containsExactly(note)
    }

    @Test
    fun `timeline groups by local day, newest day first`() {
        val groups = CaptureSearch.groupByDay(CaptureSearch.apply(all, CaptureFilter()), zone)
        assertThat(groups.map { it.day }).containsExactly(
            LocalDate.of(2026, 10, 25),
            LocalDate.of(2026, 10, 24),
            LocalDate.of(2026, 10, 23),
        ).inOrder()
        assertThat(groups.first().captures).containsExactly(photo, voice).inOrder()
    }

    @Test
    fun `days are named today and yesterday`() {
        val today = LocalDate.of(2026, 10, 25)
        assertThat(CaptureSearch.dayLabel(today, today)).isEqualTo("Today")
        assertThat(CaptureSearch.dayLabel(today.minusDays(1), today)).isEqualTo("Yesterday")
        assertThat(CaptureSearch.dayLabel(today.minusDays(2), today)).contains("23")
    }

    @Test
    fun `display title falls back to the first line, then the kind`() {
        assertThat(note.displayTitle).isEqualTo("Buy milk and eggs")
        assertThat(voice.displayTitle).isEqualTo("Call the plumber about the kitchen sink")
        assertThat(photo.displayTitle).isEqualTo("Receipt")
        assertThat(Capture(type = CaptureType.SCREENSHOT, created = 0).displayTitle).isEqualTo("Screenshot")
    }
}
