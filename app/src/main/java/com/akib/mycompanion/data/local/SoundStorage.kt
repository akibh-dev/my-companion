package com.akib.mycompanion.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "reminder_sounds")
data class ReminderSoundEntity(
    @PrimaryKey val id: String,
    val name: String,
    val uri: String,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "duration_ms") val durationMillis: Long,
    @ColumnInfo(name = "byte_count") val byteCount: Long,
)

@Dao
interface ReminderSoundDao {
    @Query("SELECT * FROM reminder_sounds ORDER BY name COLLATE NOCASE, id")
    fun observeAll(): Flow<List<ReminderSoundEntity>>

    @Query("SELECT * FROM reminder_sounds WHERE id = :id")
    suspend fun find(id: String): ReminderSoundEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sound: ReminderSoundEntity)

    @Query("SELECT COUNT(*) FROM reminder_sounds")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM reminders WHERE sound = :encoded")
    suspend fun references(encoded: String): Int

    @Query("DELETE FROM reminder_sounds WHERE id = :id")
    suspend fun remove(id: String)
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reminders ADD COLUMN sound TEXT NOT NULL DEFAULT 'DEFAULT'")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS reminder_sounds (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                "uri TEXT NOT NULL, mime_type TEXT NOT NULL, duration_ms INTEGER NOT NULL, " +
                "byte_count INTEGER NOT NULL)",
        )
    }
}
