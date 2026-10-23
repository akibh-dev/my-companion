package com.akib.mycompanion.domain

import com.akib.mycompanion.domain.whatsapp.WhatsAppNumberMatcher
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WhatsAppNumberMatcherTest {

    private val wanted = "8801711123456"

    @Test
    fun `matches on the WhatsApp id`() {
        assertThat(WhatsAppNumberMatcher.matches(wanted, "8801711123456@s.whatsapp.net")).isTrue()
    }

    @Test
    fun `matches on the voice call label`() {
        assertThat(WhatsAppNumberMatcher.matches(wanted, null, "Voice call +880 1711-123456")).isTrue()
    }

    @Test
    fun `matches a number saved without country code on the last ten digits`() {
        assertThat(WhatsAppNumberMatcher.matches(wanted, "01711123456@s.whatsapp.net")).isTrue()
    }

    @Test
    fun `different numbers do not match`() {
        assertThat(WhatsAppNumberMatcher.matches(wanted, "8801711123457@s.whatsapp.net", "Voice call +880 1711-123457"))
            .isFalse()
    }

    @Test
    fun `empty or missing values never match`() {
        assertThat(WhatsAppNumberMatcher.matches("", "8801711123456@s.whatsapp.net")).isFalse()
        assertThat(WhatsAppNumberMatcher.matches(wanted, null, null)).isFalse()
        assertThat(WhatsAppNumberMatcher.matches(wanted, "@s.whatsapp.net", "")).isFalse()
    }

    @Test
    fun `short numbers need an exact match`() {
        assertThat(WhatsAppNumberMatcher.sameNumber("123456789", "0123456789")).isFalse()
        assertThat(WhatsAppNumberMatcher.sameNumber("123456789", "123456789")).isTrue()
    }
}
