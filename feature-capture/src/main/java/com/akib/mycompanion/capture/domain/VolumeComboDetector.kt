package com.akib.mycompanion.capture.domain

import com.akib.mycompanion.core.time.Clock

/**
 * Spots "both volume keys held for a second" (FR-4.1, spike S2) from the raw key stream an
 * accessibility service sees. It never decides to swallow a key: single presses must keep changing
 * the volume, so the service always passes keys on and only asks this class whether to open the panel.
 *
 * Pure Kotlin so it can be tested without a phone. Times are whatever the caller uses consistently
 * (the service passes [Clock.nowMillis]); [check] uses [clock] for the moment a held combo is due.
 */
class VolumeComboDetector(
    private val clock: Clock,
    private val holdMillis: Long = HOLD_MILLIS,
) {
    private var upSince: Long? = null
    private var downSince: Long? = null

    /** Set once the current hold has opened the panel, so holding longer doesn't open it again. */
    private var fired = false

    /**
     * Feeds one key event (auto-repeated "down" events included).
     * @return true when this event completes a hold of both keys for [holdMillis].
     */
    fun onKey(code: Int, down: Boolean, time: Long): Boolean {
        when (code) {
            VOLUME_UP -> upSince = if (down) upSince ?: time else null
            VOLUME_DOWN -> downSince = if (down) downSince ?: time else null
            else -> return false
        }
        if (!down) fired = false
        return check(time)
    }

    /** When both keys are down: the moment the later one went down. */
    fun bothHeldSince(): Long? {
        val up = upSince ?: return null
        val down = downSince ?: return null
        return maxOf(up, down)
    }

    /** How long until a held combo fires, so the caller can [check] again then; null when nothing is pending. */
    fun millisUntilFire(now: Long = clock.nowMillis()): Long? {
        if (fired) return null
        val since = bothHeldSince() ?: return null
        return (since + holdMillis - now).coerceAtLeast(0)
    }

    /** True (once per hold) when both keys have been down for [holdMillis] at [now]. */
    fun check(now: Long = clock.nowMillis()): Boolean {
        if (fired) return false
        val since = bothHeldSince() ?: return false
        if (now - since < holdMillis) return false
        fired = true
        return true
    }

    /** Forgets every key, e.g. when the service is switched off mid-press. */
    fun reset() {
        upSince = null
        downSince = null
        fired = false
    }

    companion object {
        const val HOLD_MILLIS = 1_000L

        /** `KeyEvent.KEYCODE_VOLUME_UP` / `KEYCODE_VOLUME_DOWN`, repeated here to stay free of Android. */
        const val VOLUME_UP = 24
        const val VOLUME_DOWN = 25
    }
}
