package com.akib.mycompanion.domain

import com.akib.mycompanion.domain.history.ListeningVisit
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ListeningVisitTest {
    @Test fun `only time actually playing qualifies, not paused elapsed time`() {
        val visit = ListeningVisit(0)
        assertThat(visit.update(30_000, false, 60_000, false, true)).isFalse()
        visit.update(30_000, true, 60_000, false, false)
        assertThat(visit.update(34_000, false, 60_000, false, true)).isFalse()
        visit.update(90_000, true, 60_000, false, false)
        assertThat(visit.update(91_000, true, 60_000, false, false)).isTrue()
    }

    @Test fun `progress is throttled but explicit pause flushes it`() {
        val visit = ListeningVisit(0)
        visit.update(0, true, 60_000, false, false)
        assertThat(visit.update(5_000, true, 60_000, false, false)).isTrue()
        assertThat(visit.update(6_000, true, 60_000, false, false)).isFalse()
        assertThat(visit.update(20_000, true, 60_000, false, false)).isTrue()
        assertThat(visit.update(21_000, false, 60_000, false, true)).isTrue()
    }

    @Test fun `short completion counts but an unplayed or longer seeked item does not`() {
        val short = ListeningVisit(0)
        short.update(0, true, 3_000, false, false)
        assertThat(short.update(3_000, false, 3_000, true, true)).isTrue()
        assertThat(ListeningVisit(0).update(3_000, false, 3_000, true, true)).isFalse()
        val seeked = ListeningVisit(0)
        seeked.update(0, true, 120_000, false, false)
        assertThat(seeked.update(1_000, false, 120_000, true, true)).isFalse()
    }
}
