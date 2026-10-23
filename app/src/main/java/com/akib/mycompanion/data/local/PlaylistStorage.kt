package com.akib.mycompanion.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.akib.mycompanion.domain.model.Playlist
import com.akib.mycompanion.domain.model.PlaylistItem
import com.akib.mycompanion.domain.model.Video
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "playlist_items",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlist_id")],
)
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "playlist_id") val playlistId: Long,
    @ColumnInfo(name = "video_id") val videoId: String,
    val title: String,
    val author: String,
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Long,
    @ColumnInfo(name = "thumbnail_url") val thumbnailUrl: String?,
    val position: Int,
    @ColumnInfo(name = "added_at") val addedAt: Long,
) {
    fun toDomain() = PlaylistItem(
        id = id,
        playlistId = playlistId,
        video = Video(
            id = videoId,
            title = title,
            author = author,
            durationSeconds = durationSeconds,
            thumbnailUrl = thumbnailUrl,
        ),
        position = position,
    )
}

/** Row of the playlist overview query. */
data class PlaylistSummary(
    val id: Long,
    val name: String,
    @ColumnInfo(name = "item_count") val itemCount: Int,
    @ColumnInfo(name = "total_seconds") val totalSeconds: Long,
) {
    fun toDomain() = Playlist(id = id, name = name, itemCount = itemCount, totalSeconds = totalSeconds)
}

@Dao
abstract class PlaylistDao {
    @Query(
        """
        SELECT p.id, p.name, COUNT(i.id) AS item_count, COALESCE(SUM(i.duration_seconds), 0) AS total_seconds
        FROM playlists p LEFT JOIN playlist_items i ON i.playlist_id = p.id
        GROUP BY p.id
        ORDER BY p.name COLLATE NOCASE
        """,
    )
    abstract fun observeSummaries(): Flow<List<PlaylistSummary>>

    @Query(
        """
        SELECT p.id, p.name, COUNT(i.id) AS item_count, COALESCE(SUM(i.duration_seconds), 0) AS total_seconds
        FROM playlists p LEFT JOIN playlist_items i ON i.playlist_id = p.id
        WHERE p.id = :playlistId
        GROUP BY p.id
        """,
    )
    abstract suspend fun summary(playlistId: Long): PlaylistSummary?

    @Query("SELECT * FROM playlists WHERE name = :name COLLATE NOCASE LIMIT 1")
    abstract suspend fun findByName(name: String): PlaylistEntity?

    @Insert
    abstract suspend fun insert(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :playlistId")
    abstract suspend fun rename(playlistId: Long, name: String)

    @Query("DELETE FROM playlist_items WHERE playlist_id = :playlistId")
    abstract suspend fun deleteItemsOf(playlistId: Long)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    abstract suspend fun deletePlaylist(playlistId: Long)

    @Transaction
    open suspend fun delete(playlistId: Long) {
        deleteItemsOf(playlistId)
        deletePlaylist(playlistId)
    }

    @Query("SELECT * FROM playlist_items WHERE playlist_id = :playlistId ORDER BY position, id")
    abstract fun observeItems(playlistId: Long): Flow<List<PlaylistItemEntity>>

    @Query("SELECT * FROM playlist_items WHERE playlist_id = :playlistId ORDER BY position, id")
    abstract suspend fun items(playlistId: Long): List<PlaylistItemEntity>

    @Query("SELECT COUNT(*) > 0 FROM playlist_items WHERE playlist_id = :playlistId AND video_id = :videoId")
    abstract suspend fun contains(playlistId: Long, videoId: String): Boolean

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_items WHERE playlist_id = :playlistId")
    abstract suspend fun nextPosition(playlistId: Long): Int

    @Insert
    abstract suspend fun insertItem(item: PlaylistItemEntity): Long

    /** Appends at the end, atomically with reading the last position. */
    @Transaction
    open suspend fun append(item: PlaylistItemEntity): PlaylistItemEntity {
        val positioned = item.copy(position = nextPosition(item.playlistId))
        return positioned.copy(id = insertItem(positioned))
    }

    @Query("DELETE FROM playlist_items WHERE id = :itemId")
    abstract suspend fun deleteItem(itemId: Long)
}

/** Version 1 had contacts + reminders; version 2 adds playlists. Existing data is kept. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `playlists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `created_at` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `playlist_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`playlist_id` INTEGER NOT NULL, `video_id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`author` TEXT NOT NULL, `duration_seconds` INTEGER NOT NULL, `thumbnail_url` TEXT, " +
                "`position` INTEGER NOT NULL, `added_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`playlist_id`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_playlist_items_playlist_id` ON `playlist_items` (`playlist_id`)",
        )
    }
}
