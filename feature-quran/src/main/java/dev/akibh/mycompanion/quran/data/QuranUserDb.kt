package dev.akibh.mycompanion.quran.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/* What the user makes while reading: bookmarks and the pages read each day (for the khatm goal). */

@Entity(tableName = "bookmark", indices = [Index(value = ["ayah_id"], unique = true)])
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "ayah_id") val ayahId: Int,
    val note: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** A Madani page read on a day (epoch day, local time). Indo-Pak reading counts by its ayahs' Madani pages. */
@Entity(tableName = "page_read", primaryKeys = ["day", "page"])
data class PageReadEntity(val day: Long, val page: Int)

@Dao
interface QuranUserDao {
    @Query("SELECT * FROM bookmark ORDER BY created_at DESC")
    fun bookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmark ORDER BY created_at DESC")
    suspend fun allBookmarks(): List<BookmarkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmark WHERE ayah_id = :ayahId")
    suspend fun removeBookmark(ayahId: Int)

    @Query("DELETE FROM bookmark")
    suspend fun clearBookmarks()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markRead(read: PageReadEntity)

    @Query("SELECT * FROM page_read WHERE day >= :from")
    fun readSince(from: Long): Flow<List<PageReadEntity>>
}

@Database(entities = [BookmarkEntity::class, PageReadEntity::class], version = 1, exportSchema = true)
abstract class QuranUserDb : RoomDatabase() {
    abstract fun dao(): QuranUserDao

    companion object {
        fun create(context: Context): QuranUserDb =
            Room.databaseBuilder(context, QuranUserDb::class.java, "quran-user.db").build()
    }
}
