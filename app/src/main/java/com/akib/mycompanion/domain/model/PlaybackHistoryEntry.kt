package com.akib.mycompanion.domain.model

/** Local listening metadata only; stream URLs are intentionally excluded. */
data class PlaybackHistoryEntry(
    val video: Video,
    val lastPlayedAt: Long,
    val positionMs: Long,
    val durationMs: Long,
)
