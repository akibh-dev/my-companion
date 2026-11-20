package dev.akibh.mycompanion.voice

import com.google.common.truth.Truth.assertThat
import dev.akibh.mycompanion.messaging.ChatApp
import dev.akibh.mycompanion.messaging.IncomingMessage
import dev.akibh.mycompanion.messaging.Notice
import dev.akibh.mycompanion.messaging.toNotice
import dev.akibh.mycompanion.voice.domain.AudioSituation
import dev.akibh.mycompanion.voice.domain.BlockRule
import dev.akibh.mycompanion.voice.domain.BlockWhere
import dev.akibh.mycompanion.voice.domain.Privacy
import dev.akibh.mycompanion.voice.domain.VoiceDecider
import dev.akibh.mycompanion.voice.domain.VoiceSettings
import dev.akibh.mycompanion.voice.domain.VoiceText
import org.junit.Test

class VoiceTextTest {

    private val message =
        IncomingMessage(
            "k",
            ChatApp.TELEGRAM,
            "Nadia",
            "On my way 😂👍 https://maps.app/x",
            "Nadia",
            false,
            0,
            true
        ).toNotice()

    @Test
    fun `messages become sentences with emoji in words and links named`() {
        assertThat(
            VoiceText.sentence(message, 200)
        ).isEqualTo("Nadia on Telegram says: On my way laughing thumbs up a link")
    }

    @Test
    fun `group messages say where they're from`() {
        val group = message.copy(isGroup = true, conversation = "Office", text = "meeting at 3")
        assertThat(VoiceText.sentence(group, 200)).isEqualTo("Nadia in Office on Telegram says: meeting at 3")
    }

    @Test
    fun `long messages are cut at a word`() {
        val long = "word ".repeat(100)
        assertThat(VoiceText.clean(long, 22)).isEqualTo("word word word word, and more")
    }

    @Test
    fun `unknown emoji are dropped, an emoji-only message still says something`() {
        assertThat(VoiceText.clean("🦄", 200)).isEqualTo("an emoji")
    }

    @Test
    fun `bangla script picks a bangla voice, banglish is noticed`() {
        assertThat(VoiceText.language("আমি ভালো আছি").toLanguageTag()).isEqualTo("bn-BD")
        assertThat(VoiceText.language("I'm fine").language).isNotEqualTo("bn")
        assertThat(VoiceText.looksLikeBanglish("ami bhalo achi")).isTrue()
        assertThat(VoiceText.looksLikeBanglish("see you soon")).isFalse()
    }

    @Test
    fun `privacy, app and sender filters`() {
        val speaker = AudioSituation(privateOutput = false, inCall = false)
        val headphones = speaker.copy(privateOutput = true)
        val settings = VoiceSettings()

        assertThat(VoiceDecider.skipReason(message, settings, speaker)).isEqualTo("no headphones or car connected")
        assertThat(VoiceDecider.skipReason(message, settings, headphones)).isNull()
        assertThat(VoiceDecider.skipReason(message, settings.copy(privacy = Privacy.ALWAYS), speaker)).isNull()
        assertThat(
            VoiceDecider.skipReason(message, settings.copy(mutedApps = setOf(ChatApp.TELEGRAM.packageName)), headphones)
        ).isEqualTo("Telegram is muted")
        assertThat(
            VoiceDecider.skipReason(message, settings.copy(senders = listOf("Rahim")), headphones)
        ).isEqualTo("sender not on the list")
    }
    private val notice = Notice("n", "com.bank.app", "City Bank", "Payment received", "Tk 500 from Rahim")

    @Test
    fun `other apps read app, title and text, leaving out what's empty`() {
        assertThat(VoiceText.sentence(notice, 200)).isEqualTo("City Bank: Payment received. Tk 500 from Rahim")
        assertThat(VoiceText.sentence(notice.copy(title = ""), 200)).isEqualTo("City Bank: Tk 500 from Rahim")
        assertThat(VoiceText.sentence(notice.copy(text = ""), 200)).isEqualTo("City Bank: Payment received")
        assertThat(
            VoiceText.sentence(notice.copy(title = "Done."), 200)
        ).isEqualTo("City Bank: Done. Tk 500 from Rahim")
    }

    @Test
    fun `block rules match title or text, any case`() {
        assertThat(BlockRule("payment", BlockWhere.TITLE).matches(notice)).isTrue()
        assertThat(BlockRule("payment", BlockWhere.TEXT).matches(notice)).isFalse()
        assertThat(BlockRule("RAHIM", BlockWhere.TEXT).matches(notice)).isTrue()
        assertThat(BlockRule("rahim", BlockWhere.ANYWHERE).matches(notice)).isTrue()
        assertThat(BlockRule("  ", BlockWhere.ANYWHERE).matches(notice)).isFalse()
        // A chat message's title is its sender or group.
        assertThat(BlockRule("nadia", BlockWhere.TITLE).matches(message)).isTrue()
    }

    @Test
    fun `block rules survive encoding, even with a bar in the phrase`() {
        val rule = BlockRule("a | b", BlockWhere.TEXT)
        assertThat(BlockRule.decode(rule.encode())).isEqualTo(rule)
        assertThat(BlockRule.decode("NOWHERE|x")).isNull()
        assertThat(BlockRule.decode("TEXT|  ")).isNull()
    }

    @Test
    fun `the sender list only filters messages, not other notifications`() {
        val headphones = AudioSituation(privateOutput = true, inCall = false)
        val onlyMum = VoiceSettings(senders = listOf("Mum"))

        assertThat(VoiceDecider.skipReason(notice, onlyMum, headphones)).isNull()
        assertThat(VoiceDecider.skipReason(message, onlyMum, headphones)).isEqualTo("sender not on the list")
    }
}
