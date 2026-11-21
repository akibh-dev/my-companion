package dev.akibh.mycompanion.quran.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

/*
 * The Quran itself: read-only, built by tools/quran/build_quran_db.py into assets/quran/quran.db and
 * opened with createFromAsset. Nothing the user makes is stored here (see QuranUserDb), so a newer
 * text or layout simply replaces the file (bump VERSION here and in the tool).
 */

@Entity(tableName = "surah")
data class SurahEntity(
    @PrimaryKey val number: Int,
    @ColumnInfo(name = "name_arabic") val nameArabic: String,
    @ColumnInfo(name = "name_simple") val nameSimple: String,
    @ColumnInfo(name = "name_english") val nameEnglish: String,
    val meccan: Boolean,
    @ColumnInfo(name = "ayah_count") val ayahCount: Int,
    @ColumnInfo(name = "first_ayah_id") val firstAyahId: Int,
    @ColumnInfo(name = "first_page") val firstPage: Int,
    @ColumnInfo(name = "first_page_indopak") val firstPageIndopak: Int?,
)

@Entity(
    tableName = "ayah",
    indices = [Index(value = ["surah", "number"], unique = true), Index("page"), Index("page_indopak"), Index("juz")],
)
data class AyahEntity(
    /** 1..6236 in mushaf order. */
    @PrimaryKey val id: Int,
    val surah: Int,
    val number: Int,
    /** KFGQPC Uthmanic Hafs text (shown with the Hafs font). */
    @ColumnInfo(name = "text_hafs") val textHafs: String,
    /** Indo-Pak Nastaleeq text with its ayah-end mark (quran.com, shown with the indopak-nastaleeq font). */
    @ColumnInfo(name = "text_indopak") val textIndopak: String,
    /** Plain letters for searching (ArabicNormalizer); never shown. */
    @ColumnInfo(name = "text_search") val textSearch: String,
    val juz: Int,
    /** Rub el hizb, 1..240. */
    @ColumnInfo(name = "hizb_quarter") val hizbQuarter: Int,
    val ruku: Int,
    val manzil: Int,
    val sajdah: Boolean,
    val page: Int,
    @ColumnInfo(name = "page_indopak") val pageIndopak: Int?,
)

@Fts4(contentEntity = AyahEntity::class)
@Entity(tableName = "ayah_fts")
data class AyahFts(@ColumnInfo(name = "text_search") val textSearch: String)

/** A line of a mushaf page. `script` is MushafScript.ordinal, `kind` LineKind.ordinal. */
@Entity(tableName = "mushaf_line", primaryKeys = ["script", "page", "line"])
data class MushafLineEntity(
    val script: Int,
    val page: Int,
    val line: Int,
    val kind: Int,
    val text: String,
    val centered: Boolean,
    val surah: Int,
    @ColumnInfo(name = "first_ayah_id") val firstAyahId: Int,
    @ColumnInfo(name = "last_ayah_id") val lastAyahId: Int,
)

@Dao
interface QuranContentDao {
    @Query("SELECT * FROM surah ORDER BY number")
    suspend fun surahs(): List<SurahEntity>

    @Query("SELECT * FROM ayah WHERE surah = :surah ORDER BY number")
    suspend fun ayahsOf(surah: Int): List<AyahEntity>

    @Query("SELECT * FROM ayah WHERE id BETWEEN :from AND :to ORDER BY id")
    suspend fun ayahs(from: Int, to: Int): List<AyahEntity>

    @Query("SELECT * FROM ayah WHERE surah = :surah AND number = :number")
    suspend fun ayah(surah: Int, number: Int): AyahEntity?

    @Query("SELECT * FROM ayah WHERE id = :id")
    suspend fun ayah(id: Int): AyahEntity?

    @Query("SELECT MIN(id) FROM ayah WHERE page = :page")
    suspend fun firstAyahOnPage(page: Int): Int?

    @Query("SELECT MIN(id) FROM ayah WHERE page_indopak = :page")
    suspend fun firstAyahOnIndopakPage(page: Int): Int?

    @Query("SELECT juz, MIN(id) AS id FROM ayah GROUP BY juz ORDER BY juz")
    suspend fun juzStarts(): List<JuzStart>

    @Query("SELECT * FROM mushaf_line WHERE script = :script AND page = :page ORDER BY line")
    suspend fun page(script: Int, page: Int): List<MushafLineEntity>

    @Query(
        """
        SELECT ayah.* FROM ayah JOIN ayah_fts ON ayah.rowid = ayah_fts.rowid
        WHERE ayah_fts MATCH :match ORDER BY ayah.id LIMIT :limit
        """,
    )
    suspend fun search(match: String, limit: Int): List<AyahEntity>

    @Query("SELECT COUNT(*) FROM ayah_fts WHERE ayah_fts MATCH :match")
    suspend fun countMatches(match: String): Int
}

data class JuzStart(val juz: Int, val id: Int)

@Database(
    entities = [SurahEntity::class, AyahEntity::class, AyahFts::class, MushafLineEntity::class],
    version = QuranContentDb.VERSION,
    exportSchema = true,
)
abstract class QuranContentDb : RoomDatabase() {
    abstract fun dao(): QuranContentDao

    companion object {
        /** Bump with tools/quran/build_quran_db.py whenever the content changes. */
        const val VERSION = 2
        const val ASSET = "quran/quran.db"

        fun create(context: Context): QuranContentDb =
            Room.databaseBuilder(context, QuranContentDb::class.java, "quran-content.db")
                .createFromAsset(ASSET)
                // A newer asset replaces the old copy; there is nothing of the user's in it.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
