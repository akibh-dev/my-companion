package com.akib.mycompanion.capture.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * A capture row (plan §11: Capture is encrypted). Everything you wrote or said is in the `_enc`
 * columns, sealed with the "captures" Keystore key; only the kind, the time and the file name stay
 * readable, to sort the timeline. The audio and image files stay in the app's private storage.
 */
@Entity(tableName = "captures")
data class CaptureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** A [com.akib.mycompanion.capture.domain.CaptureType] name. */
    val type: String,
    val created: Long,
    @ColumnInfo(name = "text_enc") val textEnc: String? = null,
    /** File name inside the `captures` folder. */
    val file: String? = null,
    @ColumnInfo(name = "transcript_enc") val transcriptEnc: String? = null,
    @ColumnInfo(name = "title_enc") val titleEnc: String? = null,
    @ColumnInfo(name = "summary_enc") val summaryEnc: String? = null,
    @ColumnInfo(name = "actions_enc") val actionsEnc: String? = null,
    @ColumnInfo(name = "moved_to_vault") val movedToVault: Boolean = false,
)

@Dao
interface CaptureDao {
    @Query("SELECT * FROM captures ORDER BY created DESC, id DESC")
    fun observeAll(): Flow<List<CaptureEntity>>

    @Query("SELECT * FROM captures WHERE id = :id")
    fun observeOne(id: Long): Flow<CaptureEntity?>

    @Query("SELECT * FROM captures WHERE id = :id")
    suspend fun get(id: Long): CaptureEntity?

    @Insert
    suspend fun insert(capture: CaptureEntity): Long

    @Update
    suspend fun update(capture: CaptureEntity)

    @Query("DELETE FROM captures WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [CaptureEntity::class], version = 1, exportSchema = false)
abstract class CaptureDatabase : RoomDatabase() {
    abstract fun dao(): CaptureDao

    companion object {
        fun create(context: Context): CaptureDatabase =
            Room.databaseBuilder(context, CaptureDatabase::class.java, "capture.db").build()
    }
}
