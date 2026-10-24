package com.akib.mycompanion.domain.repository

import com.akib.mycompanion.domain.model.Playlist
import com.akib.mycompanion.domain.model.PlaylistItem
import com.akib.mycompanion.domain.model.Video
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    /** All playlists with their item counts, sorted by name. */
    fun observePlaylists(): Flow<List<Playlist>>

    /** Items of one playlist in play order. */
    fun observeItems(playlistId: Long): Flow<List<PlaylistItem>>

    suspend fun get(playlistId: Long): Playlist?

    suspend fun findByName(name: String): Playlist?

    suspend fun create(name: String): Playlist

    suspend fun rename(playlistId: Long, name: String)

    /** Deletes the playlist and everything in it. */
    suspend fun delete(playlistId: Long)

    suspend fun items(playlistId: Long): List<PlaylistItem>

    suspend fun contains(playlistId: Long, videoId: String): Boolean

    /** Appends to the end of the playlist. */
    suspend fun addItem(playlistId: Long, video: Video): PlaylistItem

    suspend fun removeItem(itemId: Long)
}
