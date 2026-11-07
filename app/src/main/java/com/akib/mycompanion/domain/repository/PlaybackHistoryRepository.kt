package com.akib.mycompanion.domain.repository

import com.akib.mycompanion.domain.model.PlaybackHistoryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** A generation captured when playback starts; deleting history invalidates old writes. */
data class HistoryVisit(val videoId: String, val epoch: Long, val videoEpoch: Long)

interface PlaybackHistoryRepository {
    fun observe(): Flow<List<PlaybackHistoryEntry>>
    fun beginVisit(videoId: String): HistoryVisit
    suspend fun record(visit: HistoryVisit, entry: PlaybackHistoryEntry)
    suspend fun remove(videoId: String)
    suspend fun clear()
}

/** Useful for previews and existing tests that do not exercise history. */
object NoPlaybackHistory : PlaybackHistoryRepository {
    override fun observe(): Flow<List<PlaybackHistoryEntry>> = flowOf(emptyList())
    override fun beginVisit(videoId: String) = HistoryVisit(videoId, 0, 0)
    override suspend fun record(visit: HistoryVisit, entry: PlaybackHistoryEntry) = Unit
    override suspend fun remove(videoId: String) = Unit
    override suspend fun clear() = Unit
}
