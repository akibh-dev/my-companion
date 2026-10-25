package com.akib.mycompanion.core.context

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What's going on right now, shared between features without them depending on each other:
 * context rules publish which rules are active, auto-reply reads it to reply by context (FR-1.3).
 */
@Singleton
class ContextStatus @Inject constructor() {
    private val mutableActiveRules = MutableStateFlow<Set<String>>(emptySet())

    /** Names of context rules whose triggers match right now. */
    val activeRules: StateFlow<Set<String>> = mutableActiveRules.asStateFlow()

    fun setActiveRules(names: Set<String>) {
        mutableActiveRules.value = names
    }
}
