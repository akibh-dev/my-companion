package com.akib.mycompanion.data.repository

import com.akib.mycompanion.data.local.PlaybackHistoryDao
import com.akib.mycompanion.data.local.PlaybackHistoryEntity
import com.akib.mycompanion.domain.model.PlaybackHistoryEntry
import com.akib.mycompanion.domain.repository.HistoryVisit
import com.akib.mycompanion.domain.repository.PlaybackHistoryRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class RoomPlaybackHistoryRepository @Inject constructor(
    private val dao: PlaybackHistoryDao
) : PlaybackHistoryRepository {
    private val writes = Mutex()
    private val generations = Any()
    private var epoch = 0L
    private val videoEpochs = mutableMapOf<String, Long>()

    override fun observe() = dao.observe().map { rows -> rows.map(PlaybackHistoryEntity::toDomain) }

    override fun beginVisit(videoId: String): HistoryVisit = synchronized(generations) {
        HistoryVisit(videoId, epoch, videoEpochs[videoId] ?: 0L)
    }

    override suspend fun record(visit: HistoryVisit, entry: PlaybackHistoryEntry) = writes.withLock {
        val valid = synchronized(generations) {
            visit.videoId == entry.video.id && visit.epoch == epoch &&
                visit.videoEpoch == (videoEpochs[visit.videoId] ?: 0L)
        }
        if (valid) dao.record(PlaybackHistoryEntity.from(entry))
    }

    override suspend fun remove(videoId: String) = writes.withLock {
        synchronized(generations) { videoEpochs[videoId] = (videoEpochs[videoId] ?: 0L) + 1 }
        dao.remove(videoId)
    }

    override suspend fun clear() = writes.withLock {
        synchronized(generations) {
            epoch++
            videoEpochs.clear()
        }
        dao.clear()
    }
}
