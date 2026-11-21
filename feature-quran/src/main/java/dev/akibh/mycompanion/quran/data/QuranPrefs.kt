package dev.akibh.mycompanion.quran.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.akibh.mycompanion.quran.domain.KhatmPlan
import dev.akibh.mycompanion.quran.domain.MushafScript
import dev.akibh.mycompanion.quran.domain.ReadingMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** How the reader looks, and where reading stopped. */
data class ReaderPrefs(
    val script: MushafScript = MushafScript.MADANI,
    val mode: ReadingMode = ReadingMode.MUSHAF,
    /** Ayah list text size, as a multiple of the default. */
    val textScale: Float = 1f,
    /** Mushaf page zoom, 1 = the page fits the screen width. */
    val pageZoom: Float = 1f,
    val keepScreenOn: Boolean = true,
)

/** The last place read: an ayah (the top of the list, or the first ayah of the page). */
data class LastRead(val ayahId: Int, val script: MushafScript, val mode: ReadingMode, val at: Long)

@Singleton
class QuranPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val store: DataStore<Preferences> = context.quranStore

    val reader: Flow<ReaderPrefs> = store.data.map { it.reader() }

    val lastRead: Flow<LastRead?> = store.data.map { p ->
        val id = p[LAST_AYAH] ?: return@map null
        LastRead(
            ayahId = id,
            script = p[LAST_SCRIPT].toScript(),
            mode = p[LAST_MODE].toMode(),
            at = p[LAST_AT] ?: 0L,
        )
    }

    val khatm: Flow<KhatmPlan?> = store.data.map { p ->
        val start = p[KHATM_START] ?: return@map null
        KhatmPlan(start, p[KHATM_DAYS] ?: DEFAULT_KHATM_DAYS)
    }

    suspend fun setKhatm(plan: KhatmPlan?) {
        store.edit { p ->
            if (plan == null) {
                p.remove(KHATM_START)
                p.remove(KHATM_DAYS)
            } else {
                p[KHATM_START] = plan.startDay
                p[KHATM_DAYS] = plan.days
            }
        }
    }

    suspend fun currentReader(): ReaderPrefs = reader.first()

    suspend fun update(change: (ReaderPrefs) -> ReaderPrefs) {
        store.edit { p ->
            val next = change(p.reader())
            p[SCRIPT] = next.script.name
            p[MODE] = next.mode.name
            p[TEXT_SCALE] = next.textScale
            p[PAGE_ZOOM] = next.pageZoom
            p[KEEP_ON] = next.keepScreenOn
        }
    }

    suspend fun saveLastRead(read: LastRead) {
        store.edit { p ->
            p[LAST_AYAH] = read.ayahId
            p[LAST_SCRIPT] = read.script.name
            p[LAST_MODE] = read.mode.name
            p[LAST_AT] = read.at
        }
    }

    private fun Preferences.reader() = ReaderPrefs(
        script = this[SCRIPT].toScript(),
        mode = this[MODE].toMode(),
        textScale = this[TEXT_SCALE] ?: 1f,
        pageZoom = this[PAGE_ZOOM] ?: 1f,
        keepScreenOn = this[KEEP_ON] ?: true,
    )

    private fun String?.toScript() = MushafScript.entries.firstOrNull { it.name == this } ?: MushafScript.MADANI

    private fun String?.toMode() = ReadingMode.entries.firstOrNull { it.name == this } ?: ReadingMode.MUSHAF

    private companion object {
        val SCRIPT = stringPreferencesKey("script")
        val MODE = stringPreferencesKey("mode")
        val TEXT_SCALE = floatPreferencesKey("text_scale")
        val PAGE_ZOOM = floatPreferencesKey("page_zoom")
        val KEEP_ON = booleanPreferencesKey("keep_screen_on")
        val LAST_AYAH = intPreferencesKey("last_ayah")
        val LAST_SCRIPT = stringPreferencesKey("last_script")
        val LAST_MODE = stringPreferencesKey("last_mode")
        val LAST_AT = longPreferencesKey("last_at")
        val KHATM_START = longPreferencesKey("khatm_start_day")
        val KHATM_DAYS = intPreferencesKey("khatm_days")
        const val DEFAULT_KHATM_DAYS = 30
    }
}

private val Context.quranStore: DataStore<Preferences> by preferencesDataStore(name = "quran")
