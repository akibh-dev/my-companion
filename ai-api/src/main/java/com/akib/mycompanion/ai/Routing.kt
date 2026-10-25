package com.akib.mycompanion.ai

import com.akib.mycompanion.core.settings.AiEngine
import com.akib.mycompanion.core.settings.SettingsStore
import dagger.Binds
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import java.io.File
import java.util.Locale
import java.util.Optional
import javax.inject.Inject
import javax.inject.Qualifier
import kotlinx.coroutines.flow.first

/** Marks a model implementation (Track B; dummies until the local models are ready). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ModelBacked

/**
 * Sends a call to the model when it's switched on in settings and answers, otherwise to the
 * fallback. Feature code only ever sees the plain interfaces, so switching never changes it.
 */
class AiRouter @Inject constructor(
    private val settings: SettingsStore,
    private val loader: ModelLoader,
) {
    suspend fun <M : Any, T> route(
        engine: AiEngine,
        model: Optional<M>,
        call: suspend M.() -> AiResult<T>,
        fallback: suspend () -> AiResult<T>,
    ): AiResult<T> {
        val backed = model.orElse(null)
        if (backed != null && settings.settings.first().usesModel(engine)) {
            val answer = loader.use(engine) { backed.call() } ?: backed.call()
            if (answer is AiResult.Ready) return answer
        }
        return fallback()
    }

    suspend fun usesModel(engine: AiEngine): Boolean = settings.settings.first().usesModel(engine)
}

class RoutedTransliterator @Inject constructor(
    private val router: AiRouter,
    private val fallback: PassThroughTransliterator,
    @ModelBacked private val model: Optional<Transliterator>,
) : Transliterator {
    override suspend fun toBangla(text: String) =
        router.route(AiEngine.TRANSLITERATOR, model, { toBangla(text) }) { fallback.toBangla(text) }
}

class RoutedSpeechSynth @Inject constructor(
    private val router: AiRouter,
    private val fallback: PhoneSpeechSynth,
    @ModelBacked private val model: Optional<SpeechSynth>,
) : SpeechSynth {
    override suspend fun speak(text: String, language: Locale) =
        router.route(AiEngine.SPEECH_SYNTH, model, { speak(text, language) }) { fallback.speak(text, language) }

    override fun stop() {
        model.ifPresent(SpeechSynth::stop)
        fallback.stop()
    }
}

class RoutedSpeechRecognizer @Inject constructor(
    private val router: AiRouter,
    private val fallback: NoSpeechRecognizer,
    @ModelBacked private val model: Optional<SpeechRecognizer>,
) : SpeechRecognizer {
    override suspend fun transcribe(audio: File, language: Locale) =
        router.route(AiEngine.SPEECH_RECOGNIZER, model, {
            transcribe(audio, language)
        }) { fallback.transcribe(audio, language) }
}

class RoutedSummarizer @Inject constructor(
    private val router: AiRouter,
    private val fallback: NoSummarizer,
    @ModelBacked private val model: Optional<Summarizer>,
) : Summarizer {
    override suspend fun summarize(text: String) =
        router.route(AiEngine.SUMMARIZER, model, { summarize(text) }) { fallback.summarize(text) }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {
    @Binds abstract fun transliterator(impl: RoutedTransliterator): Transliterator

    @Binds abstract fun speechSynth(impl: RoutedSpeechSynth): SpeechSynth

    @Binds abstract fun speechRecognizer(impl: RoutedSpeechRecognizer): SpeechRecognizer

    @Binds abstract fun summarizer(impl: RoutedSummarizer): Summarizer

    // Present only when a model module (ai-models) binds them.
    @BindsOptionalOf @ModelBacked
    abstract fun modelTransliterator(): Transliterator

    @BindsOptionalOf @ModelBacked
    abstract fun modelSpeechSynth(): SpeechSynth

    @BindsOptionalOf @ModelBacked
    abstract fun modelSpeechRecognizer(): SpeechRecognizer

    @BindsOptionalOf @ModelBacked
    abstract fun modelSummarizer(): Summarizer

    @Multibinds abstract fun loadableModels(): Set<LoadableModel>
}
