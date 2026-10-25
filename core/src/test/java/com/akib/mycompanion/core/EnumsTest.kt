package com.akib.mycompanion.core

import com.akib.mycompanion.core.util.enumOf
import com.akib.mycompanion.core.util.enumsOf
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EnumsTest {

    private enum class Colour { RED, GREEN }

    @Test
    fun `a stored name reads back as its entry`() {
        assertThat(enumOf<Colour>("GREEN")).isEqualTo(Colour.GREEN)
    }

    @Test
    fun `unknown, missing or differently cased names read as null`() {
        assertThat(enumOf<Colour>("BLUE")).isNull()
        assertThat(enumOf<Colour>(null)).isNull()
        assertThat(enumOf<Colour>("green")).isNull()
    }

    @Test
    fun `a stored set skips names that no longer exist`() {
        assertThat(enumsOf<Colour>(listOf("RED", "PURPLE", "GREEN", "RED")))
            .containsExactly(Colour.RED, Colour.GREEN)
        assertThat(enumsOf<Colour>(emptyList())).isEmpty()
        assertThat(enumsOf<Colour>(null)).isNull()
    }
}
