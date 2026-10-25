package com.akib.mycompanion.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.core.crypto.FieldCipher
import com.google.common.truth.Truth.assertThat
import java.security.GeneralSecurityException
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/** A software key stands in for the Keystore one (Robolectric has no AndroidKeyStore). Base64 needs Robolectric. */
@RunWith(AndroidJUnit4::class)
class FieldCipherTest {

    private var keyUses = 0
    private val cipher = FieldCipher {
        keyUses++
        SecretKeySpec(ByteArray(32) { 1 }, "AES")
    }

    @Test
    fun `bytes round-trip with the IV in front and the tag behind`() {
        val plain = "Gate code 4321".toByteArray()

        val sealed = cipher.encrypt(plain)

        assertThat(sealed.size).isEqualTo(FieldCipher.IV_BYTES + plain.size + GCM_TAG_BYTES)
        assertThat(cipher.decrypt(sealed)).isEqualTo(plain)
    }

    @Test
    fun `the same text looks different every time`() {
        val first = cipher.encryptText("same")
        val second = cipher.encryptText("same")

        assertThat(first).isNotEqualTo(second)
        assertThat(cipher.decryptText(first)).isEqualTo("same")
        assertThat(cipher.decryptText(second)).isEqualTo("same")
    }

    @Test
    fun `text round-trips, empty, bangla and emoji included`() {
        listOf("", "আমি ভালো আছি", "👍 ok\nnext line", "a".repeat(10_000)).forEach { text ->
            assertThat(cipher.decryptText(cipher.encryptText(text))).isEqualTo(text)
        }
    }

    @Test
    fun `sealed text is plain base64 without line breaks`() {
        val sealed = cipher.encryptText("x".repeat(500))

        assertThat(sealed).doesNotContain("\n")
        assertThat(sealed).matches("[A-Za-z0-9+/=]+")
    }

    @Test
    fun `a changed byte is caught instead of reading wrong`() {
        val sealed = cipher.encrypt("pay 100".toByteArray())
        val last = sealed.size - 1
        sealed[last] = (sealed[last].toInt() xor 1).toByte()

        assertThrows(GeneralSecurityException::class.java) { cipher.decrypt(sealed) }
    }

    @Test
    fun `another key can't read it`() {
        val other = FieldCipher { SecretKeySpec(ByteArray(32) { 2 }, "AES") }

        assertThrows(GeneralSecurityException::class.java) { other.decrypt(cipher.encrypt("secret".toByteArray())) }
    }

    @Test
    fun `data too short to hold an IV is refused`() {
        assertThrows(IllegalArgumentException::class.java) { cipher.decrypt(ByteArray(FieldCipher.IV_BYTES)) }
        assertThrows(IllegalArgumentException::class.java) { cipher.decrypt(ByteArray(0)) }
    }

    @Test
    fun `the key is fetched on every use, never cached`() {
        cipher.decrypt(cipher.encrypt(byteArrayOf(1, 2, 3)))

        assertThat(keyUses).isEqualTo(2)
    }

    private companion object {
        const val GCM_TAG_BYTES = 16
    }
}
