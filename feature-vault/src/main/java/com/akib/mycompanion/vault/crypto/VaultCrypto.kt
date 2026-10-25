package com.akib.mycompanion.vault.crypto

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM with the vault's data key (held in memory only while unlocked).
 * Small values: IV + ciphertext. Files: [stream] encrypts in chunks so a large file never sits in
 * memory whole; each chunk is bound to its position and the last one is marked, so cutting, reordering
 * or truncating the file fails to decrypt instead of reading wrong.
 */
class VaultCrypto(private val key: SecretKey) {

    fun encrypt(plain: ByteArray): ByteArray {
        val iv = randomIv()
        val cipher = Cipher.getInstance(
            TRANSFORMATION
        ).apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv)) }
        return iv + cipher.doFinal(plain)
    }

    fun decrypt(sealed: ByteArray): ByteArray {
        if (sealed.size <= IV_BYTES) throw IOException("Not vault data")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
        return cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
    }

    /** Copies [input] to [output] encrypted. */
    fun encryptStream(input: InputStream, output: OutputStream) {
        val out = DataOutputStream(output)
        out.write(MAGIC)
        val buffer = ByteArray(CHUNK_BYTES)
        var index = 0L
        var pending = readFully(input, buffer)
        while (true) {
            val next = ByteArray(CHUNK_BYTES)
            val nextCount = if (pending == CHUNK_BYTES) readFully(input, next) else 0
            val last = nextCount == 0
            val iv = randomIv()
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
                updateAAD(aad(index, last))
            }
            val sealed = cipher.doFinal(buffer, 0, pending)
            out.writeInt(sealed.size)
            out.write(iv)
            out.write(sealed)
            if (last) break
            next.copyInto(buffer)
            pending = nextCount
            index++
        }
        out.flush()
    }

    /** Copies encrypted [input] to [output] in plain. Throws when anything was changed or cut. */
    fun decryptStream(input: InputStream, output: OutputStream) {
        val data = DataInputStream(input)
        val magic = ByteArray(MAGIC.size).also { data.readFully(it) }
        if (!magic.contentEquals(MAGIC)) throw IOException("Not a vault file")
        var index = 0L
        while (true) {
            val size = try {
                data.readInt()
            } catch (_: EOFException) {
                throw IOException("Vault file is cut short")
            }
            if (size !in TAG_BITS / 8..CHUNK_BYTES + TAG_BITS / 8) throw IOException("Vault file is damaged")
            val iv = ByteArray(IV_BYTES).also { data.readFully(it) }
            val sealed = ByteArray(size).also { data.readFully(it) }
            // The chunk says whether it was the last; GCM checks that claim along with the content.
            val asLast = runCatching { open(sealed, iv, index, last = true) }.getOrNull()
            val plain = asLast ?: open(sealed, iv, index, last = false)
            output.write(plain)
            if (asLast != null) break
            index++
        }
        output.flush()
    }

    private fun open(sealed: ByteArray, iv: ByteArray, index: Long, last: Boolean): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            updateAAD(aad(index, last))
        }
        return cipher.doFinal(sealed)
    }

    private fun aad(index: Long, last: Boolean) = ByteBuffer.allocate(9).putLong(index).put(if (last) 1 else 0).array()

    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = input.read(buffer, total, buffer.size - total)
            if (read < 0) break
            total += read
        }
        return total
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
        private const val IV_BYTES = 12
        const val CHUNK_BYTES = 64 * 1024
        private val MAGIC = "MCV1".toByteArray()
        private val random = SecureRandom()

        fun randomIv() = ByteArray(IV_BYTES).also(random::nextBytes)

        fun newDataKey(): ByteArray = ByteArray(32).also(random::nextBytes)

        fun of(keyBytes: ByteArray) = VaultCrypto(SecretKeySpec(keyBytes, "AES"))
    }
}
