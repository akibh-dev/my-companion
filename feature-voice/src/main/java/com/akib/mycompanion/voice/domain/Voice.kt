package com.akib.mycompanion.voice.domain

import com.akib.mycompanion.messaging.ChatApp
import com.akib.mycompanion.messaging.IncomingMessage
import java.util.Locale

/** Where speech may come out (FR-5.7): privately only, or anywhere. */
enum class Privacy(val title: String) {
    HEADPHONES_OR_CAR("Only with headphones or in the car"),
    ALWAYS("Also through the speaker"),
}

data class VoiceSettings(
    val privacy: Privacy = Privacy.HEADPHONES_OR_CAR,
    val apps: Set<ChatApp> = ChatApp.entries.toSet(),
    /** Empty: everyone. Otherwise only these senders are read (FR-5.4). */
    val senders: List<String> = emptyList(),
    /** Longer messages are cut with "and more" (FR-5.10). */
    val maxChars: Int = 200,
    /** 1.0 = normal. */
    val speechRate: Float = 1.0f,
)

/** What the phone is doing, as far as speaking aloud is concerned. */
data class AudioSituation(val privateOutput: Boolean, val inCall: Boolean)

object VoiceDecider {
    /** Null when the message should be read, otherwise why not. */
    fun skipReason(message: IncomingMessage, settings: VoiceSettings, audio: AudioSituation): String? = when {
        message.app !in settings.apps -> "${message.app.title} is off"
        settings.senders.isNotEmpty() && settings.senders.none {
            it.equals(message.sender, ignoreCase = true)
        } -> "sender not on the list"
        settings.privacy == Privacy.HEADPHONES_OR_CAR && !audio.privateOutput -> "no headphones or car connected"
        else -> null
    }
}

/** Turns a message into something pleasant to hear (FR-5.2, 5.3, 5.10). */
object VoiceText {
    private val LINK = Regex("""(https?://|www\.)\S+""", RegexOption.IGNORE_CASE)

    /** Common emoji in words; anything else emoji-like is dropped rather than read as gibberish. */
    private val EMOJI = mapOf(
        "😂" to "laughing", "🤣" to "laughing", "😊" to "smiling",
        "🙂" to "smiling", "😀" to "smiling", "😁" to "grinning",
        "😍" to "heart eyes", "❤️" to "heart", "❤" to "heart",
        "😢" to "sad", "😭" to "crying", "😡" to "angry",
        "👍" to "thumbs up", "👎" to "thumbs down", "🙏" to "thanks",
        "👏" to "clapping", "🔥" to "fire", "🎉" to "party",
        "😮" to "surprised", "🤔" to "thinking", "😴" to "sleepy",
        "👋" to "waving", "✅" to "done", "💯" to "hundred percent",
    )

    fun sentence(message: IncomingMessage, maxChars: Int): String {
        val who = if (message.isGroup && message.conversation != message.sender) {
            "${message.sender} in ${message.conversation} on ${message.app.title}"
        } else {
            "${message.sender} on ${message.app.title}"
        }
        return "$who says: ${clean(message.text, maxChars)}"
    }

    fun clean(text: String, maxChars: Int): String {
        var out = LINK.replace(text, " a link ")
        EMOJI.forEach { (emoji, word) -> out = out.replace(emoji, " $word ") }
        out = out.filter { ch -> !Character.isSurrogate(ch) && Character.getType(ch) != Character.OTHER_SYMBOL.toInt() }
        out = out.replace(Regex("\\s+"), " ").trim()
        if (out.length > maxChars) out = out.take(maxChars).substringBeforeLast(' ') + ", and more"
        return out.ifEmpty { "an emoji" }
    }

    /** Bangla script ⇒ a Bangla voice (FR-5.11); otherwise the phone's language. */
    fun language(text: String): Locale {
        val letters = text.count { it.isLetter() }
        val bangla = text.count { it in 'ঀ'..'৿' }
        return if (letters > 0 && bangla * 10 >= letters * 3) Locale.forLanguageTag("bn-BD") else Locale.getDefault()
    }

    /** Latin letters only and some common Bangla words: probably Banglish (FR-5.12, Track B). */
    fun looksLikeBanglish(text: String): Boolean {
        val words = text.lowercase().split(Regex("\\W+")).filter { it.isNotEmpty() }
        return words.count { it in BANGLISH_HINTS } >= 1 && text.none { it in 'ঀ'..'৿' }
    }

    private val BANGLISH_HINTS =
        setOf(
            "ami", "tumi", "apni", "kemon", "acho", "achen", "bhalo", "valo", "korcho", "khaiso", "kothay", "na", "hae",
        )
}
