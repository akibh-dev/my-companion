package dev.akibh.mycompanion.quran.domain

import kotlin.math.ceil

/** A goal to finish the whole Quran (604 Madani pages) in [days] days, starting on [startDay] (epoch day). */
data class KhatmPlan(val startDay: Long, val days: Int) {
    val endDay get() = startDay + days - 1
}

data class KhatmStatus(
    val pagesRead: Int,
    val pagesLeft: Int,
    /** Days left including today. */
    val daysLeft: Int,
    /** Pages to read today to stay on plan (0 when today's share is done or the plan is finished). */
    val todayTarget: Int,
    val todayRead: Int,
    /** Days in a row, up to today (or yesterday if nothing yet today), with at least one page read. */
    val streak: Int,
    val finished: Boolean,
    val overdue: Boolean,
)

object Khatm {
    const val PAGES = 604

    /**
     * [reads] maps an epoch day to the Madani pages read that day. A page counts once for the plan
     * however often it is re-read; days before the plan's start don't count.
     */
    fun status(plan: KhatmPlan, reads: Map<Long, Set<Int>>, today: Long): KhatmStatus {
        val inPlan = reads.filterKeys { it in plan.startDay..today }
        val readPages = inPlan.values.flatten().toSet()
        val beforeToday = inPlan.filterKeys { it < today }.values.flatten().toSet()
        val todayNew = (inPlan[today].orEmpty() - beforeToday)
        val left = PAGES - readPages.size
        val daysLeft = (plan.endDay - today + 1).toInt()
        val leftAtDawn = PAGES - beforeToday.size
        val share = if (daysLeft > 0) ceil(leftAtDawn.toDouble() / daysLeft).toInt() else leftAtDawn
        return KhatmStatus(
            pagesRead = readPages.size,
            pagesLeft = left,
            daysLeft = daysLeft.coerceAtLeast(0),
            todayTarget = if (left == 0) 0 else (share - todayNew.size).coerceAtLeast(0),
            todayRead = todayNew.size,
            streak = streak(reads.filterValues { it.isNotEmpty() }.keys, today),
            finished = left == 0,
            overdue = left > 0 && daysLeft <= 0,
        )
    }

    private fun streak(days: Set<Long>, today: Long): Int {
        var day = if (today in days) today else today - 1
        var count = 0
        while (day in days) {
            count++
            day--
        }
        return count
    }
}
