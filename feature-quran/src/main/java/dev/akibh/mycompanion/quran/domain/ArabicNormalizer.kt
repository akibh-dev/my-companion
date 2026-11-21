package dev.akibh.mycompanion.quran.domain

/**
 * Turns Quran text or a typed query into plain letters, so `الرحمن` finds `ٱلرَّحۡمَٰنِ`.
 * Only for searching: the text shown is never changed.
 *
 * tools/quran/build_quran_db.py has the same rules (`normalize`) for the search index, and both
 * run tools/quran/normalizer_cases.tsv in their tests, so they can't drift apart.
 */
object ArabicNormalizer {
    fun normalize(text: String): String {
        val out = StringBuilder(text.length)
        var space = true
        for (char in text) {
            val mapped = map(char) ?: continue
            if (mapped == ' ') {
                if (!space) out.append(' ')
                space = true
            } else {
                out.append(mapped)
                space = false
            }
        }
        return out.trimEnd().toString()
    }

    private fun map(char: Char): Char? = when {
        char.isWhitespace() -> ' '
        char in 'ؐ'..'ؚ' -> null // honorifics and small high marks
        char in 'ً'..'ٟ' -> null // harakat, shadda, sukun, tanween
        char == 'ٰ' -> null // superscript (dagger) alef
        char in 'ۖ'..'ۭ' -> null // Quranic annotation, waqf and small letters
        char in '࣓'..'ࣿ' -> null // extended Quranic marks (open tanween, ...)
        char == 'ـ' -> null // tatweel
        char == '۝' || char == '۞' || char == '۩' -> null // ayah end, rub el hizb, sajdah
        char in '٠'..'٩' || char in '۰'..'۹' || char in '0'..'9' -> null
        char == 'ٱ' || char == 'آ' || char == 'أ' || char == 'إ' || char == 'ٲ' ||
            char == 'ٳ' -> 'ا' // alef wasla / madda / hamza forms -> alef
        char == 'ى' || char == 'ی' || char == 'ي' -> 'ي' // alef maqsura, Farsi yeh -> yeh
        char == 'ئ' -> 'ي' // yeh with hamza
        char == 'ؤ' -> 'و' // waw with hamza
        char == 'ة' -> 'ه' // ta marbuta -> heh
        char == 'ہ' || char == 'ھ' || char == 'ە' -> 'ه' // Urdu heh forms
        char == 'ک' -> 'ك' // keheh -> kaf
        char == '‌' || char == '‍' || char == '‏' || char == '‎' -> null
        char.isLetter() -> char
        else -> ' '
    }
}
