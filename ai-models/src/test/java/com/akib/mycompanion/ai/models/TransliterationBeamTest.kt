package com.akib.mycompanion.ai.models

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CancellationException
import org.junit.Assert.assertThrows
import org.junit.Test

class TransliterationBeamTest {
    @Test
    fun `only completed words are returned and forbidden symbols are never emitted`() {
        val result = TransliterationBeam.decode(eos = 2, forbidden = setOf(0, 1, 3), maxSteps = 3) { tokens ->
            if (tokens.size == 1) {
                floatArrayOf(100f, 100f, 100f, 100f, 10f, 0f)
            } else {
                floatArrayOf(100f, 100f, 10f, 100f, -10f, -10f)
            }
        }

        assertThat(result.first().tokens).containsExactly(2L, 4L, 2L).inOrder()
        result.forEach { candidate ->
            assertThat(candidate.tokens.last()).isEqualTo(2L)
            assertThat(candidate.tokens.drop(1)).containsNoneOf(0L, 1L, 3L)
        }
    }

    @Test
    fun `an unfinished prefix is never returned at the generation limit`() {
        val result = TransliterationBeam.decode(eos = 2, forbidden = setOf(0, 1, 3), maxSteps = 1) {
            floatArrayOf(-10f, -10f, -10f, -10f, 10f)
        }
        assertThat(result).isEmpty()
    }

    @Test
    fun `cancellation interrupts beam decoding`() {
        assertThrows(CancellationException::class.java) {
            TransliterationBeam.decode(eos = 2, forbidden = emptySet(), checkCancellation = {
                throw CancellationException()
            }) { floatArrayOf(0f, 1f, 2f) }
        }
    }

    @Test
    fun `invalid scores fail instead of producing corrupt candidates`() {
        assertThrows(IllegalArgumentException::class.java) {
            TransliterationBeam.decode(eos = 2, forbidden = emptySet()) { floatArrayOf(Float.NaN) }
        }
    }
}
