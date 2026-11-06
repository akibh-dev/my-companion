package com.akib.mycompanion.voice

import com.akib.mycompanion.ai.AiResult
import com.akib.mycompanion.ai.Transliterator
import com.akib.mycompanion.voice.domain.ReadingMode
import com.akib.mycompanion.voice.domain.VoiceBody
import com.akib.mycompanion.voice.domain.VoiceSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class VoiceBodyTest {
    private val calls = mutableListOf<String>()
    private val body = VoiceBody(object : Transliterator {
        override suspend fun toBangla(text: String): AiResult<String> {
            calls += text
            return AiResult.Ready(mapOf("ami" to "আমি", "bangla" to "বাংলা")[text.lowercase()] ?: "রূপান্তর")
        }
    })

    @Test fun `auto preserves Bangla and English and converts only known words`() = runTest {
        assertThat(body.prepare("হ্যালো ami bangla. Please call me later.", VoiceSettings()))
            .isEqualTo("হ্যালো আমি বাংলা. Please call me later.")
        assertThat(calls).containsExactly("ami", "bangla")
    }

    @Test fun `original mode never invokes the model`() = runTest {
        assertThat(
            body.prepare("ami bangla", VoiceSettings(readingMode = ReadingMode.ORIGINAL))
        ).isEqualTo("ami bangla")
        assertThat(calls).isEmpty()
    }

    @Test fun `banglish mode converts every Latin word once per request`() = runTest {
        body.prepare("ami ami unknown", VoiceSettings(readingMode = ReadingMode.BANGLISH))
        assertThat(calls).containsExactly("ami", "unknown")
    }

    @Test fun `failed conversion preserves the original word`() = runTest {
        val unavailable = VoiceBody(object : Transliterator {
            override suspend fun toBangla(text: String): AiResult<String> = AiResult.NotReady("Unavailable")
        })
        assertThat(unavailable.prepare("ami", VoiceSettings())).isEqualTo("ami")
    }
}
