package com.akib.mycompanion.context.screenon

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What keep-screen-on sees right now, for the live status line on its screen. */
data class ScreenOnState(
    /** The background part is running (feature on, nothing paused). */
    val running: Boolean = false,
    val screenOn: Boolean = false,
    val outside: Boolean = false,
    val raised: Boolean = false,
    /** The invisible window that keeps the screen awake is showing. */
    val keepingOn: Boolean = false,
    /** "Display over other apps" is granted; without it nothing can keep the screen on. */
    val overlayAllowed: Boolean = true,
) {
    /** One plain line, e.g. "Outside · raised · keeping the screen on". */
    fun summary(): String = when {
        !running -> "Not running"
        !overlayAllowed -> "Needs \"Display over other apps\" to keep the screen on"
        !screenOn -> "Screen off · sensors off"
        else -> listOf(
            if (outside) "Outside" else "At a saved place",
            if (raised) "raised" else "not raised",
            if (keepingOn) "keeping the screen on" else "normal screen timeout",
        ).joinToString(" · ")
    }
}

/** Shared between the background component (writes) and the screen (reads). */
@Singleton
class ScreenOnStatus @Inject constructor() {
    private val mutableState = MutableStateFlow(ScreenOnState())
    val state: StateFlow<ScreenOnState> = mutableState.asStateFlow()

    fun set(state: ScreenOnState) {
        mutableState.value = state
    }

    fun reset() {
        mutableState.value = ScreenOnState()
    }
}
