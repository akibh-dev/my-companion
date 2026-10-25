package com.akib.mycompanion.capture.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** What was captured (FR-4.2, FR-4.7). The names are stored in the database, so never rename them. */
enum class CaptureType(val title: String, val extension: String?, val mime: String) {
    TEXT("Note", null, "text/plain"),
    VOICE("Voice", "m4a", "audio/mp4"),
    PHOTO("Photo", "jpg", "image/jpeg"),
    SCREENSHOT("Screenshot", "png", "image/png"),
    CLIPBOARD("Clipboard", null, "text/plain"),
    ;

    val hasFile: Boolean get() = extension != null
}

/**
 * One capture, decrypted (only ever in memory). [file] is the name of its audio or image file in the
 * app's private `captures` folder. After "Move to vault" (FR-4.9) the content lives only in the vault:
 * [movedToVault] is set and the text fields and file are gone.
 */
data class Capture(
    val id: Long = 0,
    val type: CaptureType,
    val created: Long,
    val text: String = "",
    val file: String? = null,
    val transcript: String = "",
    val title: String = "",
    val summary: String = "",
    val actions: List<String> = emptyList(),
    val movedToVault: Boolean = false,
) {
    /** The title, or the first line of what was said or written, or the kind of capture. */
    val displayTitle: String
        get() = title.ifBlank { firstLine(text).ifBlank { firstLine(transcript) } }.ifBlank { defaultTitle(type) }

    /** The words a summary is made from: typed text and the voice transcript. */
    val summarySource: String
        get() = listOf(text, transcript).filter { it.isNotBlank() }.joinToString("\n\n")

    private companion object {
        const val TITLE_CHARS = 60

        fun firstLine(text: String) = text.trim().lineSequence().firstOrNull().orEmpty().take(TITLE_CHARS)

        fun defaultTitle(type: CaptureType) = when (type) {
            CaptureType.TEXT -> "Note"
            CaptureType.VOICE -> "Voice note"
            CaptureType.PHOTO -> "Photo"
            CaptureType.SCREENSHOT -> "Screenshot"
            CaptureType.CLIPBOARD -> "Clipboard"
        }
    }
}

/** What the capture space shows (FR-4.3): search words and an optional type. */
data class CaptureFilter(val query: String = "", val type: CaptureType? = null)

/** One day of the timeline. */
data class DayGroup(val day: LocalDate, val captures: List<Capture>)

/** Search, filter and the day-by-day timeline of the capture space. Pure, so it's unit-tested. */
object CaptureSearch {

    /** Newest first; every search word must appear in the text, transcript, title, summary or actions. */
    fun apply(captures: List<Capture>, filter: CaptureFilter): List<Capture> {
        val words = filter.query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return captures
            .filter { capture ->
                (filter.type == null || capture.type == filter.type) &&
                    words.all { word -> haystack(capture).contains(word) }
            }
            .sortedWith(compareByDescending<Capture> { it.created }.thenByDescending { it.id })
    }

    /** Groups [captures] (already in order) by the day they were made in [zone], keeping their order. */
    fun groupByDay(captures: List<Capture>, zone: ZoneId): List<DayGroup> = captures
        .groupBy { Instant.ofEpochMilli(it.created).atZone(zone).toLocalDate() }
        .map { (day, list) -> DayGroup(day, list) }

    /** "Today", "Yesterday", or the date in the phone's format. */
    fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
    }

    private fun haystack(capture: Capture) = listOf(
        capture.title,
        capture.text,
        capture.transcript,
        capture.summary,
        capture.actions.joinToString(" "),
    ).joinToString(" ").lowercase()
}
