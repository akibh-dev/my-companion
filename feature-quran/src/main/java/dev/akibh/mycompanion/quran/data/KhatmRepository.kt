package dev.akibh.mycompanion.quran.data

import dev.akibh.mycompanion.core.time.Clock
import dev.akibh.mycompanion.quran.domain.Khatm
import dev.akibh.mycompanion.quran.domain.KhatmPlan
import dev.akibh.mycompanion.quran.domain.KhatmStatus
import dev.akibh.mycompanion.quran.domain.MushafScript
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** The khatm goal: which Madani pages were read on which day, and how the plan stands. */
@Singleton
class KhatmRepository @Inject constructor(
    private val dao: QuranUserDao,
    private val prefs: QuranPrefs,
    private val quran: QuranRepository,
    private val clock: Clock,
) {
    val plan: Flow<KhatmPlan?> = prefs.khatm

    @OptIn(ExperimentalCoroutinesApi::class)
    val status: Flow<Pair<KhatmPlan, KhatmStatus>?> = prefs.khatm.flatMapLatest { plan ->
        if (plan == null) {
            flowOf(null)
        } else {
            // A little before the start too, so the streak shows reading that led up to the plan.
            dao.readSince(plan.startDay - STREAK_LOOKBACK).map { rows ->
                val reads = rows.groupBy({ it.day }, { it.page }).mapValues { it.value.toSet() }
                plan to Khatm.status(plan, reads, today())
            }
        }
    }

    suspend fun start(days: Int) = prefs.setKhatm(KhatmPlan(today(), days))

    suspend fun stop() = prefs.setKhatm(null)

    /** The ayahs [fromId]..[toId] were read: their Madani pages count for today. */
    suspend fun markRead(fromId: Int, toId: Int) {
        val day = today()
        val first = quran.pageOf(fromId, MushafScript.MADANI) ?: return
        val last = quran.pageOf(toId, MushafScript.MADANI) ?: first
        (first..last).forEach { dao.markRead(PageReadEntity(day, it)) }
    }

    fun today(): Long = LocalDate.ofInstant(Instant.ofEpochMilli(clock.nowMillis()), clock.zone).toEpochDay()

    private companion object {
        const val STREAK_LOOKBACK = 400L
    }
}
