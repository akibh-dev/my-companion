package dev.akibh.mycompanion.voice

import com.google.common.truth.Truth.assertThat
import dev.akibh.mycompanion.ai.models.BanglishLexicon
import dev.akibh.mycompanion.voice.domain.VoiceBody
import org.junit.Test

class VoiceLexiconCoverageTest {

    @Test
    fun `every word Auto mode converts has an exact lexicon spelling`() {
        // The model misspells several of these (achi → আচি); Auto mode should never depend on its guess.
        val missing = VoiceBody.BANGLISH_WORDS.filter { BanglishLexicon.lookup(it) == null }

        assertThat(missing).isEmpty()
    }
}
