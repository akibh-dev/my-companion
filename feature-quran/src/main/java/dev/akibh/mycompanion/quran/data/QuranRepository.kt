package dev.akibh.mycompanion.quran.data

import dev.akibh.mycompanion.quran.domain.Ayah
import dev.akibh.mycompanion.quran.domain.AyahRef
import dev.akibh.mycompanion.quran.domain.Juz
import dev.akibh.mycompanion.quran.domain.LineKind
import dev.akibh.mycompanion.quran.domain.MushafLine
import dev.akibh.mycompanion.quran.domain.MushafScript
import dev.akibh.mycompanion.quran.domain.Surah
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Read access to the Quran text, its pages and its index. The content never changes while running. */
@Singleton
class QuranRepository @Inject constructor(private val dao: QuranContentDao) {
    private val lock = Mutex()
    private var surahCache: List<Surah>? = null
    private var allCache: List<AyahEntity>? = null

    suspend fun surahs(): List<Surah> = lock.withLock {
        surahCache ?: dao.surahs().map { it.toDomain() }.also { surahCache = it }
    }

    suspend fun surah(number: Int): Surah = surahs()[number - 1]

    /** All 6236 ayahs (about 2 MB), for the continuous ayah list. */
    suspend fun all(script: MushafScript): List<Ayah> {
        val rows = lock.withLock { allCache ?: dao.ayahs(1, AYAH_COUNT).also { allCache = it } }
        return rows.map { it.toDomain(script) }
    }

    suspend fun juzList(): List<Juz> = dao.juzStarts().map { start ->
        val ayah = requireNotNull(dao.ayah(start.id))
        Juz(start.juz, AyahRef(ayah.surah, ayah.number), ayah.page)
    }

    suspend fun ayah(id: Int, script: MushafScript = MushafScript.MADANI): Ayah? = dao.ayah(id)?.toDomain(script)

    suspend fun ayah(ref: AyahRef, script: MushafScript = MushafScript.MADANI): Ayah? =
        dao.ayah(ref.surah, ref.ayah)?.toDomain(script)

    suspend fun ayahs(from: Int, to: Int, script: MushafScript): List<Ayah> = dao.ayahs(from, to).map {
        it.toDomain(script)
    }

    suspend fun page(script: MushafScript, page: Int): List<MushafLine> = dao.page(script.ordinal, page).map {
        MushafLine(it.line, LineKind.entries[it.kind], it.text, it.centered, it.surah, it.firstAyahId, it.lastAyahId)
    }

    /** The page an ayah is on, or null when that script has no pages yet. */
    suspend fun pageOf(ayahId: Int, script: MushafScript): Int? {
        val row = dao.ayah(ayahId) ?: return null
        return if (script == MushafScript.MADANI) row.page else row.pageIndopak
    }

    suspend fun firstAyahOn(script: MushafScript, page: Int): Int? = when (script) {
        MushafScript.MADANI -> dao.firstAyahOnPage(page)
        MushafScript.INDOPAK -> dao.firstAyahOnIndopakPage(page)
    }

    suspend fun hasPages(script: MushafScript): Boolean = dao.page(script.ordinal, 3).isNotEmpty()

    suspend fun search(match: String, limit: Int): List<Ayah> = dao.search(match, limit).map {
        it.toDomain(MushafScript.MADANI)
    }

    suspend fun countMatches(match: String): Int = dao.countMatches(match)

    private companion object {
        const val AYAH_COUNT = 6236
    }
}

private fun SurahEntity.toDomain() =
    Surah(number, nameArabic, nameSimple, nameEnglish, meccan, ayahCount, firstAyahId, firstPage, firstPageIndopak)

private fun AyahEntity.toDomain(script: MushafScript) = Ayah(
    id = id,
    surah = surah,
    number = number,
    text = if (script == MushafScript.MADANI) textHafs else textIndopak,
    juz = juz,
    hizbQuarter = hizbQuarter,
    sajdah = sajdah,
    page = page,
    pageIndopak = pageIndopak,
)
