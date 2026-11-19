package dev.akibh.mycompanion

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.EntryPointAccessors
import dev.akibh.mycompanion.ai.AiResult
import dev.akibh.mycompanion.core.settings.AiEngine
import dev.akibh.mycompanion.core.settings.Feature
import dev.akibh.mycompanion.debug.BanglishDemoEntryPoint
import dev.akibh.mycompanion.voice.domain.Privacy
import dev.akibh.mycompanion.voice.domain.ReadingMode
import dev.akibh.mycompanion.voice.system.VoiceState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the same routed model, privacy checks, audio focus and serializer as automatic reading. */
@RunWith(AndroidJUnit4::class)
class ProductionVoiceDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun productionReaderConvertsAndSpeaksWithConfiguredRate() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val entry = EntryPointAccessors.fromApplication(context, BanglishDemoEntryPoint::class.java)
        val settings = entry.settings()
        val voice = entry.voiceSettings()
        val previous = settings.settings.first()
        val previousVoice = voice.settings.first()
        try {
            settings.setEnabled(Feature.VOICE_MODE, false)
            entry.playback().stop()
            settings.setUseModel(AiEngine.TRANSLITERATOR, true)
            settings.setPaused(false)
            voice.update { it.copy(privacy = Privacy.ALWAYS, readingMode = ReadingMode.AUTO, speechRate = 1.1f) }
            assertThat(entry.transliterator().toBangla("ami")).isEqualTo(AiResult.Ready("আমি"))
            val result = withTimeout(90_000) { entry.playback().sample() }
            assertThat(result).isEqualTo(AiResult.Ready(Unit))
            assertThat(entry.playback().state.value).isEqualTo(VoiceState.Idle)
            // The privacy gate must apply to the foreground sample too.
            voice.update { it.copy(privacy = Privacy.HEADPHONES_OR_CAR) }
            settings.setPaused(true)
            assertThat(entry.playback().sample()).isInstanceOf(AiResult.NotReady::class.java)
        } finally {
            entry.playback().stop()
            voice.update { previousVoice }
            settings.setUseModel(AiEngine.TRANSLITERATOR, previous.usesModel(AiEngine.TRANSLITERATOR))
            settings.setPaused(previous.paused)
            settings.setEnabled(Feature.VOICE_MODE, Feature.VOICE_MODE in previous.enabled)
        }
    }
}
