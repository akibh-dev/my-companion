package com.akib.mycompanion.ai.models

import com.akib.mycompanion.ai.AiResult
import com.akib.mycompanion.ai.LoadableModel
import com.akib.mycompanion.ai.Transliterator
import com.akib.mycompanion.core.settings.AiEngine
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Word-level conversion; callers decide which Latin spans actually represent Bangla. */
@Singleton
class IndicXlitTransliterator @Inject constructor(
    private val model: IndicXlitPrototype
) : Transliterator, LoadableModel {
    override val engine = AiEngine.TRANSLITERATOR
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override suspend fun load() = model.warmUp()

    override fun release() {
        scope.launch { model.close() }
    }

    override suspend fun toBangla(text: String): AiResult<String> = when (val result = model.preview(text)) {
        is TransliterationPreview.Ready -> AiResult.Ready(result.text)
        is TransliterationPreview.Unavailable -> AiResult.NotReady(result.reason)
    }
}
