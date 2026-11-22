package dev.akibh.mycompanion.library.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "book", indices = [Index(value = ["uri"], unique = true)])
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val format: String,
    val title: String,
    val author: String,
    val coverPath: String?,
    val fileName: String,
    val size: Long,
    val addedAt: Long,
    val lastOpenedAt: Long? = null,
    val locator: String? = null,
    val progress: Double = 0.0,
    val positions: Int = 0,
    val status: String,
    val finishedAt: Long? = null,
)

@Entity(tableName = "label", indices = [Index(value = ["kind", "name"], unique = true)])
data class LabelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String,
)

@Entity(
    tableName = "book_label",
    primaryKeys = ["bookId", "labelId"],
    indices = [Index("labelId")],
    foreignKeys = [
        ForeignKey(BookEntity::class, ["id"], ["bookId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(LabelEntity::class, ["id"], ["labelId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class BookLabelEntity(val bookId: Long, val labelId: Long)

@Entity(
    tableName = "bookmark",
    indices = [Index("bookId")],
    foreignKeys = [ForeignKey(BookEntity::class, ["id"], ["bookId"], onDelete = ForeignKey.CASCADE)],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val locator: String,
    val label: String,
    val progress: Double,
    val createdAt: Long,
)

/** Seconds read per book per day. No foreign key: removing a book keeps the reading history. */
@Entity(tableName = "reading_day", primaryKeys = ["bookId", "day"])
data class ReadingDayEntity(val bookId: Long, val day: Long, val seconds: Long)

data class DayTotal(val day: Long, val seconds: Long)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM book")
    fun books(): Flow<List<BookEntity>>

    @Query("SELECT * FROM book")
    suspend fun allBooks(): List<BookEntity>

    @Query("SELECT * FROM book WHERE id = :id")
    suspend fun book(id: Long): BookEntity?

    @Query("SELECT * FROM book WHERE id = :id")
    fun bookFlow(id: Long): Flow<BookEntity?>

    @Query("SELECT * FROM book WHERE uri = :uri")
    suspend fun bookByUri(uri: String): BookEntity?

    @Query("SELECT * FROM book WHERE fileName = :fileName AND size = :size LIMIT 1")
    suspend fun bookByFile(fileName: String, size: Long): BookEntity?

    @Insert
    suspend fun insert(book: BookEntity): Long

    @Update
    suspend fun update(book: BookEntity)

    @Query("DELETE FROM book WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE book SET locator = :locator, progress = :progress, lastOpenedAt = :at WHERE id = :id")
    suspend fun savePosition(id: Long, locator: String, progress: Double, at: Long)

    @Query("UPDATE book SET status = :status, finishedAt = :finishedAt WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, finishedAt: Long?)

    @Query("SELECT * FROM label ORDER BY name COLLATE NOCASE")
    fun labels(): Flow<List<LabelEntity>>

    @Query("SELECT * FROM label")
    suspend fun allLabels(): List<LabelEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLabel(label: LabelEntity): Long

    @Query("SELECT * FROM label WHERE kind = :kind AND name = :name")
    suspend fun label(kind: String, name: String): LabelEntity?

    @Query("UPDATE label SET name = :name WHERE id = :id")
    suspend fun renameLabel(id: Long, name: String)

    @Query("DELETE FROM label WHERE id = :id")
    suspend fun deleteLabel(id: Long)

    @Query("SELECT * FROM book_label")
    fun bookLabels(): Flow<List<BookLabelEntity>>

    @Query("SELECT * FROM book_label")
    suspend fun allBookLabels(): List<BookLabelEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addBookLabel(link: BookLabelEntity)

    @Query("DELETE FROM book_label WHERE bookId = :bookId AND labelId = :labelId")
    suspend fun removeBookLabel(bookId: Long, labelId: Long)

    @Query("SELECT * FROM bookmark WHERE bookId = :bookId ORDER BY progress")
    fun bookmarks(bookId: Long): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmark")
    suspend fun allBookmarks(): List<BookmarkEntity>

    @Insert
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Query("UPDATE bookmark SET label = :label WHERE id = :id")
    suspend fun renameBookmark(id: Long, label: String)

    @Query("DELETE FROM bookmark WHERE id = :id")
    suspend fun deleteBookmark(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDay(day: ReadingDayEntity)

    @Query("UPDATE reading_day SET seconds = seconds + :seconds WHERE bookId = :bookId AND day = :day")
    suspend fun addToDay(bookId: Long, day: Long, seconds: Long)

    @Transaction
    suspend fun addReading(bookId: Long, day: Long, seconds: Long) {
        insertDay(ReadingDayEntity(bookId, day, 0))
        addToDay(bookId, day, seconds)
    }

    @Query("SELECT day, SUM(seconds) AS seconds FROM reading_day GROUP BY day")
    fun dayTotals(): Flow<List<DayTotal>>

    @Query("SELECT * FROM reading_day")
    suspend fun allDays(): List<ReadingDayEntity>

    @Query("SELECT COALESCE(SUM(seconds), 0) FROM reading_day WHERE bookId = :bookId")
    fun bookSeconds(bookId: Long): Flow<Long>

    @Transaction
    suspend fun replaceAll(
        books: List<BookEntity>,
        labels: List<LabelEntity>,
        links: List<BookLabelEntity>,
        bookmarks: List<BookmarkEntity>,
        days: List<ReadingDayEntity>,
    ) {
        clearDays()
        clearBooks()
        clearLabels()
        books.forEach { insert(it) }
        labels.forEach { insertLabel(it) }
        links.forEach { addBookLabel(it) }
        bookmarks.forEach { insertBookmark(it) }
        days.forEach { insertDay(it) }
    }

    @Query("DELETE FROM book")
    suspend fun clearBooks()

    @Query("DELETE FROM label")
    suspend fun clearLabels()

    @Query("DELETE FROM reading_day")
    suspend fun clearDays()
}

@Database(
    entities = [
        BookEntity::class,
        LabelEntity::class,
        BookLabelEntity::class,
        BookmarkEntity::class,
        ReadingDayEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class LibraryDb : RoomDatabase() {
    abstract fun dao(): LibraryDao

    companion object {
        fun create(context: Context): LibraryDb =
            Room.databaseBuilder(context, LibraryDb::class.java, "library.db").build()
    }
}
