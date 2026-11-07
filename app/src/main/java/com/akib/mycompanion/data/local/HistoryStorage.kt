package com.akib.mycompanion.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.akib.mycompanion.domain.model.PlaybackHistoryEntry
import com.akib.mycompanion.domain.model.Video
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "playback_history", indices = [Index("lastPlayedAt")])
data class PlaybackHistoryEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val author: String,
    val durationSeconds: Long,
    val thumbnailUrl: String?,
    val lastPlayedAt: Long,
    val positionMs: Long,
    val durationMs: Long,
) {
    fun toDomain() = PlaybackHistoryEntry(
        Video(videoId, title, author, durationSeconds, thumbnailUrl),
        lastPlayedAt,
        positionMs,
        durationMs,
    )

    companion object {
        fun from(entry: PlaybackHistoryEntry) = PlaybackHistoryEntity(
            entry.video.id,
            entry.video.title,
            entry.video.author,
            entry.video.durationSeconds,
            entry.video.thumbnailUrl,
            entry.lastPlayedAt,
            entry.positionMs,
            entry.durationMs,
        )
    }
}

@Dao
abstract class PlaybackHistoryDao {
    @Query("SELECT * FROM playback_history ORDER BY lastPlayedAt DESC, videoId LIMIT 500")
    abstract fun observe(): Flow<List<PlaybackHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(entry: PlaybackHistoryEntity)

    @Query(
        "DELETE FROM playback_history WHERE videoId NOT IN " +
            "(SELECT videoId FROM playback_history ORDER BY lastPlayedAt DESC, videoId LIMIT 500)"
    )
    abstract suspend fun prune()

    @Transaction
    open suspend fun record(entry: PlaybackHistoryEntity) {
        upsert(entry)
        prune()
    }

    @Query("DELETE FROM playback_history WHERE videoId = :videoId")
    abstract suspend fun remove(videoId: String)

    @Query("DELETE FROM playback_history")
    abstract suspend fun clear()
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `playback_history` (`videoId` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`author` TEXT NOT NULL, `durationSeconds` INTEGER NOT NULL, `thumbnailUrl` TEXT, " +
                "`lastPlayedAt` INTEGER NOT NULL, `positionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, " +
                "PRIMARY KEY(`videoId`))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_playback_history_lastPlayedAt` ON `playback_history` (`lastPlayedAt`)"
        )
    }
}
