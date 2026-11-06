package com.akib.mycompanion.ai.models

import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes native inference and closes its session after a quiet period, never during a lease. */
internal class IdleModelSession<T : Closeable>(
    private val scope: CoroutineScope,
    private val idleMillis: Long,
    private val create: () -> T,
) {
    private val lock = Mutex()
    private var value: T? = null
    private var release: Job? = null

    suspend fun close() = lock.withLock {
        release?.cancel()
        val previous = value
        value = null
        previous?.close()
    }

    suspend fun <R> use(block: suspend (T, Boolean) -> R): R = lock.withLock {
        release?.cancel()
        val cold = value == null
        val current = value ?: create().also { value = it }
        try {
            block(current, cold)
        } finally {
            release = scope.launch {
                delay(idleMillis)
                lock.withLock {
                    val previous = value
                    value = null
                    previous?.close()
                }
            }
        }
    }
}
