package dev.akibh.mycompanion.quran.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** Two short lines about reading, for the home-screen widget and the Quick Settings tile. */
data class QuranSummary(val place: String, val goal: String?)

@Singleton
class QuranSummaries @Inject constructor(
    prefs: QuranPrefs,
    khatm: KhatmRepository,
    private val quran: QuranRepository,
) {
    val summary: Flow<QuranSummary> = combine(prefs.lastRead, khatm.status) { last, status ->
        val place = last?.let { read ->
            quran.ayah(read.ayahId)?.let { ayah ->
                "${quran.surah(ayah.surah).nameSimple} ${ayah.number} · page ${ayah.page}"
            }
        } ?: "Start reading"
        val goal = status?.second?.let { s ->
            when {
                s.finished -> "Khatm complete"
                s.todayTarget == 0 -> "Today's pages done · ${s.pagesLeft} left"
                else -> "Today: ${s.todayTarget} pages · ${s.pagesLeft} left"
            }
        }
        QuranSummary(place, goal)
    }.distinctUntilChanged()
}
