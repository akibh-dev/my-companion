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
    fun `matches only recognized Bangladesh local form without using suffix matching`() {
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

    @Test fun `equal international suffixes never establish phone identity`() {
        assertThat(WhatsAppNumberMatcher.sameNumber("+441711123456", "+491711123456")).isFalse()
        assertThat(WhatsAppNumberMatcher.matches("+441711123456", "491711123456@s.whatsapp.net")).isFalse()
    }

    @Test fun `arbitrary JIDs digit bearing text and conflicting labels are rejected`() {
        assertThat(WhatsAppNumberMatcher.matches(wanted, "$wanted@g.us", "Voice call +$wanted")).isFalse()
        assertThat(WhatsAppNumberMatcher.matches(wanted, "prefix${wanted}suffix")).isFalse()
        assertThat(WhatsAppNumberMatcher.matches(wanted, null, "Work 1 home +$wanted")).isFalse()
        assertThat(
            WhatsAppNumberMatcher.matches(wanted, "8801811123456@s.whatsapp.net", "Voice call +$wanted")
        ).isFalse()
    }

    @Test fun `full international JIDs do not inherit Bangladesh national interpretation`() {
        assertThat(WhatsAppNumberMatcher.matches("+1711123456", "1711123456@s.whatsapp.net")).isTrue()
        assertThat(WhatsAppNumberMatcher.matches("+8801711123456", "1711123456@s.whatsapp.net")).isFalse()
    }

    @Test fun `recognizable Bangladesh local formatting and international 00 normalize deliberately`() {
        assertThat(WhatsAppNumberMatcher.normalize("01711-123456")).isEqualTo(wanted)
        assertThat(WhatsAppNumberMatcher.normalize("1711123456")).isEqualTo(wanted)
        assertThat(WhatsAppNumberMatcher.normalize("00 880 1711 123456")).isEqualTo(wanted)
        assertThat(WhatsAppNumberMatcher.normalize("0123456789")).isNull()
        assertThat(WhatsAppNumberMatcher.normalize("Call +8801711123456")).isNull()
    }
}
