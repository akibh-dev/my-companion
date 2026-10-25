package com.akib.mycompanion.ai

import com.akib.mycompanion.core.log.AppLog
import com.akib.mycompanion.core.settings.AiEngine
import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.core.settings.Settings
import com.akib.mycompanion.core.settings.SettingsStore
import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.util.Optional
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AiRoutingTest {

    private val settings = object : SettingsStore {
        val state = MutableStateFlow(Settings())
        override val settings = state
        override suspend fun setPaused(paused: Boolean) = Unit
        override suspend fun setEnabled(feature: Feature, enabled: Boolean) = Unit
        override suspend fun setUseModel(engine: AiEngine, useModel: Boolean) {
            state.value = state.value.copy(modelEngines = if (useModel) setOf(engine) else emptySet())
        }
    }

    private class CountingModel(var answer: AiResult<String> = AiResult.Ready("আমি")) : Transliterator, LoadableModel {
        override val engine = AiEngine.TRANSLITERATOR
        var loads = 0
        var releases = 0
        override suspend fun load() {
            loads++
        }
        override fun release() {
            releases++
        }
        override suspend fun toBangla(text: String) = answer
    }

    private val log = AppLog(Files.createTempDirectory("log").toFile(), mirrorToLogcat = false)
    private val model = CountingModel()
    private val loader = ModelLoader(setOf(model), log, releaseAfterMillis = 100)
    private val transliterator =
        RoutedTransliterator(AiRouter(settings, loader), PassThroughTransliterator(), Optional.of(model))

    @Test
    fun `the fallback answers until the model is switched on`() = runTest {
        assertThat(transliterator.toBangla("ami")).isEqualTo(AiResult.Ready("ami"))

        settings.setUseModel(AiEngine.TRANSLITERATOR, true)
        assertThat(transliterator.toBangla("ami")).isEqualTo(AiResult.Ready("আমি"))
    }

    @Test
    fun `a model that isn't ready falls back`() = runTest {
        settings.setUseModel(AiEngine.TRANSLITERATOR, true)
        model.answer = AiResult.NotReady("loading")

        assertThat(transliterator.toBangla("ami")).isEqualTo(AiResult.Ready("ami"))
    }

    @Test
    fun `with no model module installed the fallback is used`() = runTest {
        settings.setUseModel(AiEngine.TRANSLITERATOR, true)
        val plain = RoutedTransliterator(AiRouter(settings, loader), PassThroughTransliterator(), Optional.empty())

        assertThat(plain.toBangla("ami")).isEqualTo(AiResult.Ready("ami"))
    }

    @Test
    fun `the loader loads once, then releases the model after a quiet spell`() = runBlocking {
        loader.use(AiEngine.TRANSLITERATOR) {}
        loader.use(AiEngine.TRANSLITERATOR) {}
        assertThat(model.loads).isEqualTo(1)

        Thread.sleep(400)
        assertThat(model.releases).isEqualTo(1)
        assertThat(loader.isLoaded(AiEngine.TRANSLITERATOR)).isFalse()
    }

    @Test
    fun `a kept model stays loaded until it's let go`() = runBlocking {
        loader.keep(AiEngine.TRANSLITERATOR, true)
        Thread.sleep(300)
        assertThat(loader.isLoaded(AiEngine.TRANSLITERATOR)).isTrue()

        loader.keep(AiEngine.TRANSLITERATOR, false)
        Thread.sleep(400)
        assertThat(loader.isLoaded(AiEngine.TRANSLITERATOR)).isFalse()
    }

    @Test
    fun `fallbacks without a model say so`() = runTest {
        assertThat(NoSummarizer().summarize("x")).isInstanceOf(AiResult.NotReady::class.java)
        assertThat(NoSpeechRecognizer().transcribe(java.io.File("none"))).isInstanceOf(AiResult.NotReady::class.java)
    }
}
