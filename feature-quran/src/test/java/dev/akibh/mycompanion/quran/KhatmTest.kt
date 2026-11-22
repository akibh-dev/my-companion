package dev.akibh.mycompanion.quran

import com.google.common.truth.Truth.assertThat
import dev.akibh.mycompanion.quran.domain.Khatm
import dev.akibh.mycompanion.quran.domain.KhatmPlan
import org.junit.Test

class KhatmTest {
    private val plan = KhatmPlan(startDay = 100, days = 30)

    @Test
    fun `a fresh 30-day plan asks for 21 pages a day`() {
        val status = Khatm.status(plan, emptyMap(), today = 100)

        assertThat(status.todayTarget).isEqualTo(21) // ceil(604 / 30)
        assertThat(status.pagesLeft).isEqualTo(604)
        assertThat(status.daysLeft).isEqualTo(30)
        assertThat(status.streak).isEqualTo(0)
    }

    @Test
    fun `pages read today count down today's target, re-reads count once`() {
        val reads = mapOf(100L to (1..10).toSet(), 101L to (5..15).toSet())

        val status = Khatm.status(plan, reads, today = 101)

        assertThat(status.pagesRead).isEqualTo(15)
        assertThat(status.todayRead).isEqualTo(5) // pages 11..15 are new today
        assertThat(status.todayTarget).isEqualTo(21 - 5) // ceil(594 / 29) = 21
        assertThat(status.streak).isEqualTo(2)
    }

    @Test
    fun `falling behind spreads the rest over the days left`() {
        val status = Khatm.status(plan, emptyMap(), today = 120) // 10 days left, nothing read

        assertThat(status.todayTarget).isEqualTo(61) // ceil(604 / 10)
        assertThat(Khatm.status(plan, emptyMap(), today = 140).overdue).isTrue()
    }

    @Test
    fun `reading before the plan started doesn't count, the streak survives until today ends`() {
        val reads = mapOf(99L to setOf(1, 2), 100L to setOf(3))

        val status = Khatm.status(plan, reads, today = 101)

        assertThat(status.pagesRead).isEqualTo(1)
        assertThat(status.streak).isEqualTo(2) // days 99 and 100; today isn't over yet
    }

    @Test
    fun `every page read finishes the khatm`() {
        val status = Khatm.status(plan, mapOf(110L to (1..604).toSet()), today = 110)

        assertThat(status.finished).isTrue()
        assertThat(status.todayTarget).isEqualTo(0)
    }
}
