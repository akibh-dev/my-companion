package com.akib.mycompanion.presentation

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.akib.mycompanion.fakes.MainDispatcherRule
import com.akib.mycompanion.presentation.common.collectWhileStarted
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** Snackbar events must not play out while the app is in the background (device test finding #3). */
class ObserveAsEventsTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val owner = object : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }
    private val events = Channel<String>(Channel.BUFFERED)
    private val handled = mutableListOf<String>()

    @Test
    fun `events wait until the screen is visible again`() = runTest {
        owner.registry.currentState = Lifecycle.State.STARTED
        val job = launch(mainDispatcher.dispatcher) {
            owner.lifecycle.collectWhileStarted(events.receiveAsFlow()) { handled += it }
        }

        owner.registry.currentState = Lifecycle.State.CREATED // another app is in front
        events.send("Opened the WhatsApp chat")
        assertThat(handled).isEmpty()

        owner.registry.currentState = Lifecycle.State.STARTED // back in the app
        assertThat(handled).containsExactly("Opened the WhatsApp chat")
        job.cancel()
    }

    @Test
    fun `an event cut short by leaving the app is shown again on return, once`() = runTest {
        val dismissed = CompletableDeferred<Unit>()
        owner.registry.currentState = Lifecycle.State.STARTED
        val job = launch(mainDispatcher.dispatcher) {
            owner.lifecycle.collectWhileStarted(events.receiveAsFlow()) {
                handled += it
                dismissed.await() // like showSnackbar: suspends while the snackbar is on screen
            }
        }

        events.send("Removed Mum")
        owner.registry.currentState = Lifecycle.State.CREATED
        owner.registry.currentState = Lifecycle.State.STARTED
        assertThat(handled).containsExactly("Removed Mum", "Removed Mum")

        dismissed.complete(Unit)
        owner.registry.currentState = Lifecycle.State.CREATED
        owner.registry.currentState = Lifecycle.State.STARTED
        assertThat(handled).hasSize(2)
        job.cancel()
    }
}
