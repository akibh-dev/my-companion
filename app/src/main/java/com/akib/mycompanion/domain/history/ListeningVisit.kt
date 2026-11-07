package com.akib.mycompanion.domain.history

/** Counts monotonic time actually playing, never changes in the media seek position. */
class ListeningVisit(startedAt: Long) {
    private var lastTick = startedAt
    private var playing = false
    private var accumulatedMs = 0L
    private var lastSavedAt: Long? = null

    fun update(now: Long, isPlaying: Boolean, durationMs: Long, completed: Boolean, force: Boolean): Boolean {
        if (playing) accumulatedMs += (now - lastTick).coerceAtLeast(0)
        lastTick = now
        playing = isPlaying
        val qualified = accumulatedMs >= 5_000 ||
            (completed && durationMs in 1..4_999 && accumulatedMs > 0)
        if (!qualified) return false
        if (lastSavedAt == null || force || now - requireNotNull(lastSavedAt) >= 15_000) {
            lastSavedAt = now
            return true
        }
        return false
    }
}
