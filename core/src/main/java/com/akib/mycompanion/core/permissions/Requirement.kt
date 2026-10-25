package com.akib.mycompanion.core.permissions

import android.content.Context
import android.content.Intent
import com.akib.mycompanion.core.settings.Feature

/**
 * One permission or special access a feature needs, for the permission and health screen (NFR-3, NFR-8).
 * Features contribute theirs with `@IntoSet`, so the screen lists every one without knowing the features.
 */
data class Requirement(
    val id: String,
    val title: String,
    /** Why the feature needs it, in one plain sentence. */
    val why: String,
    val feature: Feature?,
    val isGranted: (Context) -> Boolean,
    /** The system screen where it's switched on. */
    val settingsIntent: (Context) -> Intent,
    /** Extra steps, e.g. "Allow restricted settings" for sideloaded apps (plan §3). */
    val steps: List<String> = emptyList(),
)
