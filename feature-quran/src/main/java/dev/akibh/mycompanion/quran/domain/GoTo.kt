package dev.akibh.mycompanion.quran.domain

/** A place the search box can jump to directly. */
sealed interface GoTo {
    data class ToAyah(val ref: AyahRef) : GoTo

    data class ToSurah(val number: Int) : GoTo

    data class ToPage(val page: Int) : GoTo

    data class ToJuz(val juz: Int) : GoTo
}

/**
 * Reads what was typed as a reference: `2:255`, `2 255`, `٢:٢٥٥`, `page 50` / `p50`, `juz 30` / `j30`,
 * a surah number, or a surah name in English or Arabic (`baqara`, `Al-Kahf`, `الكهف`).
 * [ayahCounts] and [names] are indexed by surah - 1; the names are (simple, arabic).
 */
object GoToParser {
    private val ayahPattern = Regex("""^(\d{1,3})\s*[:.\s]\s*(\d{1,3})$""")
    private val pagePattern = Regex("""^(?:page|pg|p|পৃষ্ঠা)\s*(\d{1,3})$""")
    private val juzPattern = Regex("""^(?:juz|para|j|পারা)\s*(\d{1,2})$""")

    fun parse(input: String, ayahCounts: List<Int>, names: List<Pair<String, String>>): List<GoTo> {
        val text = westernDigits(input).trim().lowercase()
        if (text.isEmpty()) return emptyList()
        ayahPattern.matchEntire(text)?.let { match ->
            val (surah, ayah) = match.destructured.toList().map(String::toInt)
            return if (surah in 1..ayahCounts.size && ayah in 1..ayahCounts[surah - 1]) {
                listOf(GoTo.ToAyah(AyahRef(surah, ayah)))
            } else {
                emptyList()
            }
        }
        pagePattern.matchEntire(text)?.let { match ->
            val page = match.groupValues[1].toInt()
            return if (page in 1..MushafScript.MADANI.pages) listOf(GoTo.ToPage(page)) else emptyList()
        }
        juzPattern.matchEntire(text)?.let { match ->
            val juz = match.groupValues[1].toInt()
            return if (juz in 1..30) listOf(GoTo.ToJuz(juz)) else emptyList()
        }
        text.toIntOrNull()?.let { number ->
            return if (number in 1..ayahCounts.size) listOf(GoTo.ToSurah(number)) else emptyList()
        }
        val key = simplify(text)
        val arabic = ArabicNormalizer.normalize(text).replace(" ", "")
        if (key.length < 2 && arabic.length < 2) return emptyList()
        return names.withIndex().filter { (_, name) ->
            (key.length >= 2 && simplify(name.first).contains(key)) ||
                (arabic.length >= 2 && ArabicNormalizer.normalize(name.second).replace(" ", "").contains(arabic))
        }.sortedBy { (_, name) -> if (simplify(name.first).startsWith(key)) 0 else 1 }
            .take(MAX_SURAHS)
            .map { GoTo.ToSurah(it.index + 1) }
    }

    /** "Al-Baqarah" and "baqara" both become "baqar": no article, letters only, no trailing h/a. */
    private fun simplify(name: String): String = name.lowercase()
        .replace(Regex("""^(al|an|ar|as|at|ad|az|ash|adh)[-\s']"""), "")
        .filter { it.isLetter() && it.code < 0x0600 }
        .trimEnd('h', 'a')

    private fun westernDigits(text: String) = buildString {
        for (c in text) {
            append(
                when (c) {
                    in '٠'..'٩' -> '0' + (c - '٠')
                    in '۰'..'۹' -> '0' + (c - '۰')
                    in '০'..'৯' -> '0' + (c - '০')
                    else -> c
                },
            )
        }
    }

    private const val MAX_SURAHS = 5
}
