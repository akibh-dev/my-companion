package com.akib.mycompanion.voice

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.messaging.ChatApp
import com.akib.mycompanion.messaging.IncomingMessage
import com.akib.mycompanion.voice.domain.AudioSituation
import com.akib.mycompanion.voice.domain.Privacy
import com.akib.mycompanion.voice.domain.VoiceDecider
import com.akib.mycompanion.voice.domain.VoiceSettings
import com.akib.mycompanion.voice.domain.VoiceText
import com.akib.mycompanion.voice.system.VoiceSettingsStore
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoiceSettingsStoreTest {

    /** The only test touching the store: its DataStore is one per process. */
    @Test
    fun `voice settings start at the defaults and are saved whole`() = runTest {
        val store = VoiceSettingsStore(ApplicationProvider.getApplicationContext<Context>())
        assertThat(store.settings.first()).isEqualTo(VoiceSettings())

        store.update {
            it.copy(
                privacy = Privacy.ALWAYS,
                apps = setOf(ChatApp.TELEGRAM),
                senders = listOf("Rahim", "Mum"),
                maxChars = 80,
                speechRate = 1.25f,
            )
        }

        assertThat(store.settings.first()).isEqualTo(
            VoiceSettings(Privacy.ALWAYS, setOf(ChatApp.TELEGRAM), listOf("Mum", "Rahim"), 80, 1.25f),
        )

        store.update { it.copy(senders = emptyList()) }
        assertThat(store.settings.first().senders).isEmpty()
        assertThat(store.settings.first().maxChars).isEqualTo(80)
    }

    private val message =
        IncomingMessage("k", ChatApp.SIGNAL, "Nadia", "hi", "Nadia", false, 0, true)

    @Test
    fun `a group named after the sender reads like a one-to-one chat`() {
        val group = message.copy(isGroup = true, conversation = "Nadia")

        assertThat(VoiceText.sentence(group, 200)).isEqualTo("Nadia on Signal says: hi")
    }

    @Test
    fun `www links, hearts and extra spaces are tidied`() {
        assertThat(VoiceText.clean("see   www.example.com/x   ❤️  ok", 200)).isEqualTo("see a link heart ok")
        assertThat(VoiceText.clean("HTTPS://EXAMPLE.COM", 200)).isEqualTo("a link")
    }

    @Test
    fun `short messages aren't cut`() {
        assertThat(VoiceText.clean("exactly ten", 11)).isEqualTo("exactly ten")
    }

    @Test
    fun `mostly english with a little bangla keeps the phone's language`() {
        assertThat(VoiceText.language("Meeting at the office tomorrow, ঠিক").language).isNotEqualTo("bn")
        assertThat(VoiceText.language("123 456").toLanguageTag()).isEqualTo(Locale.getDefault().toLanguageTag())
    }

    @Test
    fun `bangla script is never banglish, and hints ignore case`() {
        assertThat(VoiceText.looksLikeBanglish("আমি ami")).isFalse()
        assertThat(VoiceText.looksLikeBanglish("KEMON ACHO")).isTrue()
        assertThat(VoiceText.looksLikeBanglish("")).isFalse()
    }

    @Test
    fun `senders on the list are read, ignoring case`() {
        val headphones = AudioSituation(privateOutput = true, inCall = false)

        assertThat(VoiceDecider.skipReason(message, VoiceSettings(senders = listOf("nadia")), headphones)).isNull()
    }
}
