package com.akib.mycompanion.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow

/**
 * Handles a ViewModel's one-off [events] (snackbars, toasts, closing a screen) only while the
 * screen is visible.
 *
 * A plain `LaunchedEffect { events.collect { } }` keeps running while the app is in the background,
 * so a snackbar plays and expires unseen (e.g. while WhatsApp is in front). Here events wait in
 * the ViewModel's Channel until the user comes back. [onEvent] handles one event at a time.
 */
@Composable
fun <T> ObserveAsEvents(events: Flow<T>, onEvent: suspend (T) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(events, lifecycleOwner) {
        lifecycleOwner.lifecycle.collectWhileStarted(events) { currentOnEvent(it) }
    }
}

/**
 * The lifecycle part of [ObserveAsEvents]. Collects only while STARTED. An event whose handling
 * is cut short by leaving the app (say, a snackbar still on screen when you switch apps) is handled
 * again on return, so it isn't lost.
 */
suspend fun <T> Lifecycle.collectWhileStarted(events: Flow<T>, onEvent: suspend (T) -> Unit) {
    var unfinished: Unfinished<T>? = null
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        unfinished?.let {
            onEvent(it.event)
            unfinished = null
        }
        events.collect { event ->
            unfinished = Unfinished(event)
            onEvent(event)
            unfinished = null
        }
    }
}

private class Unfinished<T>(val event: T)
