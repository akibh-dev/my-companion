package com.akib.mycompanion.voice

import com.akib.mycompanion.ai.models.BanglishLexicon
import com.akib.mycompanion.voice.domain.VoiceBody
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VoiceLexiconCoverageTest {

    @Test
    fun `every word Auto mode converts has an exact lexicon spelling`() {
        // The model misspells several of these (achi → আচি); Auto mode should never depend on its guess.
        val missing = VoiceBody.BANGLISH_WORDS.filter { BanglishLexicon.lookup(it) == null }

        assertThat(missing).isEmpty()
    }
}
