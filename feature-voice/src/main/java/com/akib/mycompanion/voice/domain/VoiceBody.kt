package com.akib.mycompanion.voice.domain

import com.akib.mycompanion.ai.Transliterator
import com.akib.mycompanion.ai.valueOrNull

/** Conservative token routing, not a semantic classifier. Unknown Latin words remain unchanged. */
class VoiceBody(private val transliterator: Transliterator) {
    suspend fun prepare(body: String, settings: VoiceSettings): String {
        return kotlinx.coroutines.withTimeoutOrNull(30_000) {
            convert(body, settings)
        } ?: VoiceText.clean(body, settings.maxChars)
    }

    private suspend fun convert(body: String, settings: VoiceSettings): String {
        val cleaned = VoiceText.clean(body, settings.maxChars)
        if (settings.readingMode == ReadingMode.ORIGINAL) return cleaned
        val words = WORD.findAll(cleaned).toList()
        val converted = mutableMapOf<String, String>()
        for (word in words) {
            val key = word.value.lowercase()
            if (settings.readingMode == ReadingMode.BANGLISH || key in BANGLISH_WORDS) {
                if (key !in converted) {
                    converted[key] = transliterator.toBangla(word.value).valueOrNull() ?: word.value
                }
            }
        }
        return WORD.replace(cleaned) { converted[it.value.lowercase()] ?: it.value }
    }

    private companion object {
        val WORD = Regex("[A-Za-z]+")

        // Deliberately excludes ambiguous English tokens such as am, to, or, a, me and na.
        val BANGLISH_WORDS = setOf(
            "ami", "tumi", "apni", "amar", "tomar", "amader", "tomader", "amake", "tomake",
            "bhalo", "valo", "achi", "acho", "achen", "kemon", "kothay", "korcho", "koro",
            "kori", "korbo", "khaiso", "khabo", "jabo", "ashchi", "aschi", "asho", "aso",
            "bangla", "boli", "bolo", "dekha", "hobe", "brishti", "pathao", "dekho", "bhai",
            "kal", "aj", "ajke", "bari", "ekhane", "sekhane", "ekhon", "ache", "hoyeche",
        )
    }
}
