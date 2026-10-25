package com.akib.mycompanion.voice

import com.akib.mycompanion.messaging.ChatApp
import com.akib.mycompanion.messaging.IncomingMessage
import com.akib.mycompanion.voice.domain.AudioSituation
import com.akib.mycompanion.voice.domain.Privacy
import com.akib.mycompanion.voice.domain.VoiceDecider
import com.akib.mycompanion.voice.domain.VoiceSettings
import com.akib.mycompanion.voice.domain.VoiceText
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VoiceTextTest {

    private val message =
        IncomingMessage("k", ChatApp.TELEGRAM, "Nadia", "On my way 😂👍 https://maps.app/x", "Nadia", false, 0, true)

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
            VoiceDecider.skipReason(message, settings.copy(apps = setOf(ChatApp.SIGNAL)), headphones)
        ).isEqualTo("Telegram is off")
        assertThat(
            VoiceDecider.skipReason(message, settings.copy(senders = listOf("Rahim")), headphones)
        ).isEqualTo("sender not on the list")
    }
}
