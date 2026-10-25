package com.akib.mycompanion.ai

import com.akib.mycompanion.core.log.AppLog
import com.akib.mycompanion.core.settings.AiEngine
import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test

/** The loader works on real threads and real time, so these use short release delays and wait for them. */
class ModelLoaderTest {

    private class Model(override val engine: AiEngine) : LoadableModel {
        val loads = AtomicInteger()
        val releases = AtomicInteger()

        override suspend fun load() {
            loads.incrementAndGet()
        }

        override fun release() {
            releases.incrementAndGet()
        }
    }

    private val log = AppLog(Files.createTempDirectory("log").toFile(), mirrorToLogcat = false)
    private val summarizer = Model(AiEngine.SUMMARIZER)
    private val voice = Model(AiEngine.SPEECH_SYNTH)
    private val loader = ModelLoader(setOf(summarizer, voice), log, releaseAfterMillis = 150)

    @Test
    fun `only engines with a model module are installed`() {
        assertThat(loader.isInstalled(AiEngine.SUMMARIZER)).isTrue()
        assertThat(loader.isInstalled(AiEngine.SPEECH_SYNTH)).isTrue()
        assertThat(loader.isInstalled(AiEngine.TRANSLITERATOR)).isFalse()
    }

    @Test
    fun `use answers null and loads nothing without a model`() = runBlocking {
        var ran = false

        val answer = loader.use(AiEngine.TRANSLITERATOR) {
            ran = true
            "x"
        }

        assertThat(answer).isNull()
        assertThat(ran).isFalse()
    }

    @Test
    fun `use runs the call with the model loaded and returns its answer`() = runBlocking {
        val answer = loader.use(AiEngine.SUMMARIZER) { loader.isInstalled(AiEngine.SUMMARIZER) to 42 }

        assertThat(answer).isEqualTo(true to 42)
        assertThat(summarizer.loads.get()).isEqualTo(1)
        assertThat(loader.isLoaded(AiEngine.SUMMARIZER)).isTrue()
        assertThat(loader.isLoaded(AiEngine.SPEECH_SYNTH)).isFalse()
    }

    @Test
    fun `using it again within the quiet spell keeps it loaded`() = runBlocking {
        loader.use(AiEngine.SUMMARIZER) {}
        Thread.sleep(100)
        loader.use(AiEngine.SUMMARIZER) {}
        Thread.sleep(100)

        assertThat(loader.isLoaded(AiEngine.SUMMARIZER)).isTrue()
        assertThat(summarizer.releases.get()).isEqualTo(0)

        Thread.sleep(400)
        assertThat(loader.isLoaded(AiEngine.SUMMARIZER)).isFalse()
        assertThat(summarizer.releases.get()).isEqualTo(1)
        assertThat(summarizer.loads.get()).isEqualTo(1)
    }

    @Test
    fun `a call that fails still lets the model go later`() = runBlocking {
        assertThrows(IllegalStateException::class.java) {
            runBlocking { loader.use(AiEngine.SUMMARIZER) { error("model crashed") } }
        }

        Thread.sleep(500)
        assertThat(loader.isLoaded(AiEngine.SUMMARIZER)).isFalse()
        assertThat(summarizer.releases.get()).isEqualTo(1)
    }

    @Test
    fun `a kept model stays loaded while another one is released`() = runBlocking {
        loader.keep(AiEngine.SPEECH_SYNTH, true)
        loader.use(AiEngine.SUMMARIZER) {}

        Thread.sleep(500)
        assertThat(loader.isLoaded(AiEngine.SUMMARIZER)).isFalse()
        assertThat(loader.isLoaded(AiEngine.SPEECH_SYNTH)).isTrue()
        assertThat(voice.releases.get()).isEqualTo(0)

        loader.keep(AiEngine.SPEECH_SYNTH, false)
        Thread.sleep(500)
        assertThat(loader.isLoaded(AiEngine.SPEECH_SYNTH)).isFalse()
        assertThat(voice.loads.get()).isEqualTo(1)
    }

    @Test
    fun `a model released after its quiet spell loads again on the next use`() = runBlocking {
        loader.use(AiEngine.SUMMARIZER) {}
        Thread.sleep(500)

        loader.use(AiEngine.SUMMARIZER) {}

        assertThat(summarizer.loads.get()).isEqualTo(2)
        assertThat(loader.isLoaded(AiEngine.SUMMARIZER)).isTrue()
    }

    @Test
    fun `letting go of a model that was never kept does nothing`() = runBlocking {
        loader.keep(AiEngine.SUMMARIZER, false)

        Thread.sleep(300)
        assertThat(summarizer.loads.get()).isEqualTo(0)
        assertThat(summarizer.releases.get()).isEqualTo(0)
    }
}
