package com.akib.mycompanion.ai

import com.akib.mycompanion.core.log.AppLog
import com.akib.mycompanion.core.settings.AiEngine
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A model that takes memory while loaded. Track B's models implement this; today they are dummies. */
interface LoadableModel {
    val engine: AiEngine

    suspend fun load()

    fun release()
}

/**
 * The only place models are loaded and released (plan §2, "your RAM rule"): a model loads when it's
 * needed and is released [releaseAfterMillis] after its last use, unless it's [keep]-ed (the voice
 * model while voice mode is on). Calls run off the main thread.
 */
@Singleton
class ModelLoader internal constructor(
    models: Set<LoadableModel>,
    private val log: AppLog,
    private val releaseAfterMillis: Long,
) {
    @Inject constructor(
        models: Set<@JvmSuppressWildcards LoadableModel>,
        log: AppLog
    ) : this(models, log, RELEASE_AFTER_MILLIS)

    private val byEngine = models.associateBy { it.engine }
    private val loaded = mutableSetOf<AiEngine>()
    private val kept = mutableSetOf<AiEngine>()
    private val releases = mutableMapOf<AiEngine, Job>()
    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun isInstalled(engine: AiEngine): Boolean = engine in byEngine

    suspend fun isLoaded(engine: AiEngine): Boolean = lock.withLock { engine in loaded }

    /** Runs [block] with the model loaded; null when no model is installed for [engine]. */
    suspend fun <T> use(engine: AiEngine, block: suspend () -> T): T? {
        val model = byEngine[engine] ?: return null
        lock.withLock {
            releases.remove(engine)?.cancel()
            if (engine !in loaded) {
                withContext(Dispatchers.Default) { model.load() }
                loaded += engine
                log.i(TAG, "loaded ${engine.name}")
            }
        }
        return try {
            withContext(Dispatchers.Default) { block() }
        } finally {
            scheduleRelease(engine)
        }
    }

    /** Keeps a model loaded (voice mode on) or lets it go again (voice mode off). */
    suspend fun keep(engine: AiEngine, keep: Boolean) {
        lock.withLock { if (keep) kept += engine else kept -= engine }
        if (keep) use(engine) {} else scheduleRelease(engine)
    }

    private suspend fun scheduleRelease(engine: AiEngine) = lock.withLock {
        if (engine in kept || engine !in loaded) return@withLock
        releases.remove(engine)?.cancel()
        releases[engine] = scope.launch {
            delay(releaseAfterMillis)
            lock.withLock {
                if (engine in kept || engine !in loaded) return@withLock
                byEngine[engine]?.release()
                loaded -= engine
                log.i(TAG, "released ${engine.name}")
            }
        }
    }

    companion object {
        private const val TAG = "ModelLoader"
        const val RELEASE_AFTER_MILLIS = 30_000L
    }
}
