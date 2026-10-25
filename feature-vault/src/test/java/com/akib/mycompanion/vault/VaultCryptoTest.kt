package com.akib.mycompanion.vault

import com.akib.mycompanion.vault.crypto.VaultCrypto
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.random.Random
import org.junit.Assert.assertThrows
import org.junit.Test

class VaultCryptoTest {

    private val crypto = VaultCrypto.of(VaultCrypto.newDataKey())

    private fun sealStream(plain: ByteArray) = ByteArrayOutputStream().also {
        crypto.encryptStream(ByteArrayInputStream(plain), it)
    }.toByteArray()

    private fun openStream(sealed: ByteArray, with: VaultCrypto = crypto) =
        ByteArrayOutputStream().also { with.decryptStream(ByteArrayInputStream(sealed), it) }.toByteArray()

    @Test
    fun `values round-trip and every encryption looks different`() {
        val plain = "secret note".toByteArray()
        assertThat(crypto.decrypt(crypto.encrypt(plain))).isEqualTo(plain)
        assertThat(crypto.encrypt(plain)).isNotEqualTo(crypto.encrypt(plain))
    }

    @Test
    fun `files of every size round-trip`() {
        val chunk = VaultCrypto.CHUNK_BYTES
        listOf(0, 1, chunk - 1, chunk, chunk + 1, 3 * chunk + 5).forEach { size ->
            val plain = Random(size).nextBytes(size)
            assertThat(openStream(sealStream(plain))).isEqualTo(plain)
        }
    }

    @Test
    fun `a changed byte is caught`() {
        val sealed = sealStream(Random(1).nextBytes(1000))
        sealed[sealed.size - 5] = (sealed[sealed.size - 5] + 1).toByte()
        assertThrows(Exception::class.java) { openStream(sealed) }
    }

    @Test
    fun `a file cut after a whole chunk is caught`() {
        val chunk = VaultCrypto.CHUNK_BYTES
        val sealed = sealStream(Random(2).nextBytes(2 * chunk + 10))
        val firstChunkEnd = 4 + 4 + 12 + chunk + 16 // magic, length, IV, data + tag
        assertThrows(IOException::class.java) { openStream(sealed.copyOf(firstChunkEnd)) }
    }

    @Test
    fun `chunks can't be swapped`() {
        val chunk = VaultCrypto.CHUNK_BYTES
        val sealed = sealStream(Random(3).nextBytes(3 * chunk))
        val record = 4 + 12 + chunk + 16
        val swapped = sealed.copyOf()
        System.arraycopy(sealed, 4 + record, swapped, 4, record)
        System.arraycopy(sealed, 4, swapped, 4 + record, record)
        assertThat(ByteBuffer.wrap(swapped, 4, 4).int).isEqualTo(chunk + 16)
        assertThrows(Exception::class.java) { openStream(swapped) }
    }

    @Test
    fun `another key can't read it`() {
        val sealed = sealStream("hello".toByteArray())
        assertThrows(Exception::class.java) { openStream(sealed, VaultCrypto.of(VaultCrypto.newDataKey())) }
    }
}
