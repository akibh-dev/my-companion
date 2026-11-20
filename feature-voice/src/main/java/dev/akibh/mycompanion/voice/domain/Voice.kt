package dev.akibh.mycompanion.voice.domain

import dev.akibh.mycompanion.messaging.Notice
import java.util.Locale

/** Where speech may come out (FR-5.7): privately only, or anywhere. */
enum class Privacy(val title: String) {
    HEADPHONES_OR_CAR("Only with headphones or in the car"),
    ALWAYS("Also through the speaker"),
}

enum class ReadingMode(val title: String) {
    AUTO("Auto (conservative)"),
    BANGLISH("Banglish text"),
    ORIGINAL("As written"),
}

/** Which apps' notifications are read. */
enum class AppScope(val title: String) {
    ALL_APPS("All apps"),
    CHAT_APPS("Chat apps only"),
}

/** Where a block rule looks for its words. */
enum class BlockWhere(val title: String) {
    TITLE("Title"),
    TEXT("Text"),
    ANYWHERE("Title or text"),
}

/** Don't read a notification whose title and/or text contains [phrase] (any case). */
data class BlockRule(val phrase: String, val where: BlockWhere = BlockWhere.ANYWHERE) {
    fun matches(notice: Notice): Boolean {
        val needle = phrase.trim()
        if (needle.isEmpty()) return false
        val titles = listOfNotNull(notice.title, notice.sender, notice.conversation)
        val inTitle = titles.any { it.contains(needle, ignoreCase = true) }
        val inText = notice.text.contains(needle, ignoreCase = true)
        return when (where) {
            BlockWhere.TITLE -> inTitle
            BlockWhere.TEXT -> inText
            BlockWhere.ANYWHERE -> inTitle || inText
        }
    }

    /** "TITLE|Promo"; the phrase may itself contain "|". */
    fun encode(): String = "${where.name}|${phrase.trim()}"

    companion object {
        fun decode(text: String): BlockRule? {
            val where = enumValues<BlockWhere>().firstOrNull { it.name == text.substringBefore('|') } ?: return null
            val phrase = text.substringAfter('|', "").trim()
            return if (phrase.isEmpty()) null else BlockRule(phrase, where)
        }
    }
}

data class VoiceSettings(
    val privacy: Privacy = Privacy.HEADPHONES_OR_CAR,
    val appScope: AppScope = AppScope.ALL_APPS,
    /** Package names of apps that are never read. */
    val mutedApps: Set<String> = emptySet(),
    val blockRules: List<BlockRule> = emptyList(),
    /** Notifications the app or user set to silent (low importance) are skipped unless this is on. */
    val readSilent: Boolean = false,
    /** Empty: everyone. Otherwise only messages from these senders are read (FR-5.4). */
    val senders: List<String> = emptyList(),
    /** Longer messages are cut with "and more" (FR-5.10). */
    val maxChars: Int = 200,
    /** 1.0 = normal. */
    val speechRate: Float = 1.0f,
    val readingMode: ReadingMode = ReadingMode.AUTO,
)

/** What the phone is doing, as far as speaking aloud is concerned. */
data class AudioSituation(val privateOutput: Boolean, val inCall: Boolean)

object VoiceDecider {
    /** Null when the notification should be read, otherwise why not. */
    fun skipReason(notice: Notice, settings: VoiceSettings, audio: AudioSituation): String? = when {
        notice.packageName in settings.mutedApps -> "${notice.appName} is muted"
        settings.appScope == AppScope.CHAT_APPS && !notice.isChatApp -> "only chat apps are read"
        notice.isSilent && !settings.readSilent -> "silent notification"
        notice.sender != null && settings.senders.isNotEmpty() && settings.senders.none {
            it.equals(notice.sender, ignoreCase = true)
        } -> "sender not on the list"
        else -> settings.blockRules.firstOrNull { it.matches(notice) }?.let { "blocked by \"${it.phrase}\"" }
            ?: if (settings.privacy == Privacy.HEADPHONES_OR_CAR && !audio.privateOutput) {
                "no headphones or car connected"
            } else {
                null
            }
    }
}

/** Turns a message into something pleasant to hear (FR-5.2, 5.3, 5.10). */
object VoiceText {
    /** One Bengali voice for the complete message; English keeps its spelling and may have an accent. */
    val readingLanguage: Locale = Locale.forLanguageTag("bn-BD")

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

    fun sentence(notice: Notice, maxChars: Int): String =
        announce(notice, if (notice.text.isBlank()) "" else clean(notice.text, maxChars))

    /**
     * Messages: "Nadia in Office on WhatsApp says: …". Anything else: "Gmail: New sign-in. …", with the
     * title or the body left out when it's empty.
     */
    fun announce(notice: Notice, body: String): String {
        val sender = notice.sender
        if (sender != null) {
            val conversation = notice.conversation
            val who = if (notice.isGroup && conversation != null && conversation != sender) {
                "$sender in $conversation on ${notice.appName}"
            } else {
                "$sender on ${notice.appName}"
            }
            return "$who says: $body"
        }
        val title = if (notice.title.isBlank()) "" else clean(notice.title, TITLE_CHARS)
        return when {
            title.isEmpty() -> "${notice.appName}: $body"
            body.isEmpty() -> "${notice.appName}: $title"
            else -> "${notice.appName}: ${title.trimEnd('.', ':')}. $body"
        }
    }

    private const val TITLE_CHARS = 120

    fun clean(text: String, maxChars: Int): String {
        var out = LINK.replace(text, " a link ")
        EMOJI.forEach { (emoji, word) -> out = out.replace(emoji, " $word ") }
        out = out.filter { ch -> !Character.isSurrogate(ch) && Character.getType(ch) != Character.OTHER_SYMBOL.toInt() }
        out = out.replace(Regex("\\s+"), " ").trim()
        val limit = maxChars.coerceIn(1, 600)
        if (out.length > limit) {
            out = out.take(limit).let {
                if (it.contains(' ')) it.substringBeforeLast(' ') else it
            } + ", and more"
        }
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
