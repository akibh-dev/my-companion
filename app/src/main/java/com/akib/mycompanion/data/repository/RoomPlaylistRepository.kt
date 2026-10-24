package com.akib.mycompanion.data.repository

import com.akib.mycompanion.data.local.PlaylistDao
import com.akib.mycompanion.data.local.PlaylistEntity
import com.akib.mycompanion.data.local.PlaylistItemEntity
import com.akib.mycompanion.domain.model.Playlist
import com.akib.mycompanion.domain.model.PlaylistItem
import com.akib.mycompanion.domain.model.Video
import com.akib.mycompanion.domain.repository.PlaylistRepository
import com.akib.mycompanion.domain.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomPlaylistRepository @Inject constructor(
    private val dao: PlaylistDao,
    private val clock: Clock,
) : PlaylistRepository {

    override fun observePlaylists(): Flow<List<Playlist>> =
        dao.observeSummaries().map { list -> list.map { it.toDomain() } }

    override fun observeItems(playlistId: Long): Flow<List<PlaylistItem>> =
        dao.observeItems(playlistId).map { list -> list.map { it.toDomain() } }

    override suspend fun get(playlistId: Long): Playlist? = dao.summary(playlistId)?.toDomain()

    override suspend fun findByName(name: String): Playlist? =
        dao.findByName(name)?.let { get(it.id) }

    override suspend fun create(name: String): Playlist {
        val id = dao.insert(PlaylistEntity(name = name, createdAt = clock.nowMillis()))
        return Playlist(id = id, name = name)
    }

    override suspend fun rename(playlistId: Long, name: String) = dao.rename(playlistId, name)

    override suspend fun delete(playlistId: Long) = dao.delete(playlistId)

    override suspend fun items(playlistId: Long): List<PlaylistItem> = dao.items(playlistId).map { it.toDomain() }

    override suspend fun contains(playlistId: Long, videoId: String): Boolean = dao.contains(playlistId, videoId)

    override suspend fun addItem(playlistId: Long, video: Video): PlaylistItem =
        dao.append(
            PlaylistItemEntity(
                playlistId = playlistId,
                videoId = video.id,
                title = video.title,
                author = video.author,
                durationSeconds = video.durationSeconds,
                thumbnailUrl = video.thumbnailUrl,
                position = 0,
                addedAt = clock.nowMillis(),
            ),
        ).toDomain()

    override suspend fun removeItem(itemId: Long) = dao.deleteItem(itemId)
}
