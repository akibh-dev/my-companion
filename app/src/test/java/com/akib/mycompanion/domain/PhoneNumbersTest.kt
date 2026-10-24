package com.akib.mycompanion.domain

import com.akib.mycompanion.domain.model.Contact
import com.akib.mycompanion.domain.model.PhoneNumbers
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class PhoneNumbersTest {

    @Test
    fun `international number is kept`() {
        assertThat(PhoneNumbers.normalize("+8801711123456")).isEqualTo("+8801711123456")
    }

    @Test
    fun `spaces dashes dots and brackets are ignored`() {
        assertThat(PhoneNumbers.normalize(" +880 1711-123 456 ")).isEqualTo("+8801711123456")
        assertThat(PhoneNumbers.normalize("(+1) 415.555.0123")).isEqualTo("+14155550123")
    }

    @Test
    fun `local number with trunk zero gets the default country code`() {
        assertThat(PhoneNumbers.normalize("01711-123456")).isEqualTo("+8801711123456")
    }

    @Test
    fun `double zero prefix means international`() {
        assertThat(PhoneNumbers.normalize("008801711123456")).isEqualTo("+8801711123456")
    }

    @Test
    fun `country code without plus is recognised`() {
        assertThat(PhoneNumbers.normalize("8801711123456")).isEqualTo("+8801711123456")
    }

    @Test
    fun `national number without trunk zero gets the country code`() {
        assertThat(PhoneNumbers.normalize("1711123456")).isEqualTo("+8801711123456")
    }

    @Test
    fun `other default country codes work`() {
        assertThat(PhoneNumbers.normalize("07911 123456", defaultCountryCode = "44")).isEqualTo("+447911123456")
    }

    @Test
    fun `garbage and implausible numbers are rejected`() {
        listOf("", "   ", "abc", "+", "12345", "+0123456789", "+1234567890123456", "+880-17x1", "++8801711123456")
            .forEach { input ->
                assertWithMessage("normalize(\"$input\")").that(PhoneNumbers.normalize(input)).isNull()
            }
    }

    @Test
    fun `isValid mirrors normalize`() {
        assertThat(PhoneNumbers.isValid("01711123456")).isTrue()
        assertThat(PhoneNumbers.isValid("hello")).isFalse()
    }

    @Test
    fun `contact digits drop the plus sign`() {
        assertThat(Contact(name = "Akib", phone = "+8801711123456").digits).isEqualTo("8801711123456")
    }
}
