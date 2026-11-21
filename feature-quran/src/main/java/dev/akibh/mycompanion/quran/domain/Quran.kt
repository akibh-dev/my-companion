package dev.akibh.mycompanion.quran.domain

/** One ayah, as `surah:ayah` (both 1-based). */
data class AyahRef(val surah: Int, val ayah: Int) {
    override fun toString() = "$surah:$ayah"
}

/** The two printed scripts the reader can show (D22). */
enum class MushafScript(val pages: Int) {
    /** King Fahd Madinah mushaf, 1421H print (QPC V2): 604 pages, 15 lines. */
    MADANI(604),

    /** Indo-Pak (Hafezi) 15-line mushaf: 610 pages, each page ends on an ayah. */
    INDOPAK(610),
}

enum class ReadingMode { MUSHAF, AYAH_LIST }

data class Surah(
    val number: Int,
    val nameArabic: String,
    val nameSimple: String,
    val nameEnglish: String,
    val meccan: Boolean,
    val ayahCount: Int,
    val firstAyahId: Int,
    val firstPage: Int,
    val firstPageIndopak: Int?,
)

data class Ayah(
    val id: Int,
    val surah: Int,
    val number: Int,
    val text: String,
    val juz: Int,
    val hizbQuarter: Int,
    val sajdah: Boolean,
    val page: Int,
    val pageIndopak: Int?,
) {
    val ref get() = AyahRef(surah, number)

    /** The Indo-Pak text ends with its ayah-end mark (a font glyph carrying the number); the Madani one doesn't. */
    val hasEndMark get() = text.any { it in PRIVATE_USE }

    /** As shown: the Madani text gets its ayah number added; the Indo-Pak one has it already. */
    val display get() = if (hasEndMark) text else "$text ${number.arabicDigits()}"

    /** For copying and sharing: plain Unicode, with the number as Arabic-Indic digits instead of a font glyph. */
    val plain get() = "${text.filterNot { it in PRIVATE_USE }.trimEnd()} ${number.arabicDigits()}"
}

/** Font-specific glyphs (ayah-end marks with numbers) live in Unicode's private use area. */
private val PRIVATE_USE = '\uE000'..'\uF8FF'

/** What a line of a mushaf page holds. */
enum class LineKind { AYAH, SURAH_HEADER, BISMILLAH }

data class MushafLine(
    val line: Int,
    val kind: LineKind,
    /** Words joined by spaces: page-font glyphs (Madani) or Nastaleeq text (Indo-Pak); empty for headers. */
    val text: String,
    val centered: Boolean,
    val surah: Int,
    val firstAyahId: Int,
    val lastAyahId: Int,
)

data class Juz(val number: Int, val firstAyah: AyahRef, val page: Int)

/** Arabic-Indic digits, for ayah numbers inside the Arabic text. */
fun Int.arabicDigits(): String = toString().map { '٠' + (it - '0') }.joinToString("")
