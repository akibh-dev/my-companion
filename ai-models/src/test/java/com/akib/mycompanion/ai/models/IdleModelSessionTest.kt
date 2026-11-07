package com.akib.mycompanion.ai.models

import com.google.common.truth.Truth.assertThat
import java.io.Closeable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IdleModelSessionTest {
    private class Resource : Closeable {
        var closes = 0
        override fun close() {
            closes++
        }
    }

    @Test
    fun `reuse extends idle lifetime and next use reloads after release`() = runTest {
        val created = mutableListOf<Resource>()
        val sessions = IdleModelSession(backgroundScope, 100) { Resource().also { created += it } }
        sessions.use { _, cold -> assertThat(cold).isTrue() }
        runCurrent()
        advanceTimeBy(90)
        sessions.use { _, cold -> assertThat(cold).isFalse() }
        runCurrent()
        advanceTimeBy(90)
        assertThat(created.single().closes).isEqualTo(0)
        advanceTimeBy(11)
        runCurrent()
        assertThat(created.single().closes).isEqualTo(1)
        sessions.use { _, cold -> assertThat(cold).isTrue() }
        assertThat(created).hasSize(2)
    }

    @Test
    fun `a session cannot be closed while inference is in progress`() = runTest {
        val resource = Resource()
        val sessions = IdleModelSession(backgroundScope, 100) { resource }
        sessions.use { _, _ -> }
        runCurrent()
        advanceTimeBy(90)
        val inference = launch {
            sessions.use { _, _ ->
                delay(250)
                assertThat(resource.closes).isEqualTo(0)
            }
        }
        runCurrent()
        advanceTimeBy(251)
        inference.join()
        assertThat(resource.closes).isEqualTo(0)
        advanceTimeBy(101)
        runCurrent()
        assertThat(resource.closes).isEqualTo(1)
    }

    @Test
    fun `failed inference still releases its native session after idle`() = runTest {
        val resource = Resource()
        val sessions = IdleModelSession(backgroundScope, 100) { resource }
        try {
            sessions.use<Unit> { _, _ -> throw IllegalArgumentException("fixture failure") }
        } catch (_: IllegalArgumentException) {
            // The caller receives the error, while the session still has its release timer.
        }
        runCurrent()
        advanceTimeBy(101)
        runCurrent()
        assertThat(resource.closes).isEqualTo(1)
    }
}
