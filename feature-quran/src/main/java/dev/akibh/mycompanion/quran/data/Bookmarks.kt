package dev.akibh.mycompanion.quran.data

import dev.akibh.mycompanion.core.time.Clock
import dev.akibh.mycompanion.quran.domain.AyahRef
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A saved ayah with an optional note. */
data class Bookmark(val ayahId: Int, val ref: AyahRef, val note: String, val createdAt: Long)

/** Bookmarks as kept in a settings backup: by surah:ayah, so they don't depend on our ids. */
data class QuranBackup(val bookmarks: List<BackupBookmark>)

data class BackupBookmark(val ref: AyahRef, val note: String, val createdAt: Long)

@Singleton
class BookmarkRepository @Inject constructor(
    private val dao: QuranUserDao,
    private val quran: QuranRepository,
    private val clock: Clock,
) {
    val bookmarks: Flow<List<Bookmark>> = dao.bookmarks().map { rows ->
        rows.mapNotNull { row ->
            quran.ayah(row.ayahId)?.let { Bookmark(row.ayahId, it.ref, row.note, row.createdAt) }
        }
    }

    val bookmarkedIds: Flow<Set<Int>> = dao.bookmarks().map { rows -> rows.mapTo(HashSet()) { it.ayahId } }

    /** Adds the bookmark, or updates its note when the ayah is already bookmarked. */
    suspend fun save(ayahId: Int, note: String) {
        val existing = dao.allBookmarks().firstOrNull { it.ayahId == ayahId }
        dao.save(
            BookmarkEntity(
                id = existing?.id ?: 0,
                ayahId = ayahId,
                note = note.trim(),
                createdAt = existing?.createdAt ?: clock.nowMillis(),
            ),
        )
    }

    suspend fun remove(ayahId: Int) = dao.removeBookmark(ayahId)

    suspend fun backup(): QuranBackup = QuranBackup(
        dao.allBookmarks().mapNotNull { row ->
            quran.ayah(row.ayahId)?.let { BackupBookmark(it.ref, row.note, row.createdAt) }
        },
    )

    /** Replaces all bookmarks with the backup's; ayahs that don't exist are skipped. */
    suspend fun restore(backup: QuranBackup) {
        dao.clearBookmarks()
        backup.bookmarks.forEach { saved ->
            quran.ayah(
                saved.ref
            )?.let { dao.save(BookmarkEntity(ayahId = it.id, note = saved.note, createdAt = saved.createdAt)) }
        }
    }
}
