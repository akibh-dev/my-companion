package com.akib.mycompanion.vault.data

import com.akib.mycompanion.vault.crypto.VaultCrypto
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Encrypted backup and restore with a passphrase (FR-2.15). The file is the vault's own chunked
 * format, keyed by PBKDF2 from the passphrase, so it can be restored on a new phone. Restoring adds
 * the items to what's already there.
 */
class VaultBackup @Inject constructor(private val vault: Vault) {

    suspend fun export(output: OutputStream, passphrase: CharArray) = withContext(Dispatchers.IO) {
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val plain = ByteArrayOutputStream()
        DataOutputStream(plain).use { out ->
            val items = vault.items.value
            out.writeInt(items.size)
            items.forEach { item ->
                writeBytes(out, ItemCodec.encode(item))
                out.writeUTF(item.type.name)
                out.writeBoolean(item.pinned)
                out.writeBoolean(item.favourite)
                out.writeLong(item.created)
                out.writeLong(item.size)
                writeBytes(out, if (item.blob != null) vault.content(item) else ByteArray(0))
            }
        }
        val data = DataOutputStream(output)
        data.write(MAGIC)
        data.write(salt)
        data.writeInt(ITERATIONS)
        VaultCrypto.of(
            derive(passphrase, salt, ITERATIONS)
        ).encryptStream(ByteArrayInputStream(plain.toByteArray()), data)
        data.flush()
    }

    /** @return how many items were restored. @throws IOException for a wrong passphrase or a damaged file. */
    suspend fun restore(input: InputStream, passphrase: CharArray): Int = withContext(Dispatchers.IO) {
        val data = DataInputStream(input)
        val magic = ByteArray(MAGIC.size).also { data.readFully(it) }
        if (!magic.contentEquals(MAGIC)) throw IOException("Not a My Companion vault backup")
        val salt = ByteArray(SALT_BYTES).also { data.readFully(it) }
        val iterations = data.readInt()
        val plain = ByteArrayOutputStream()
        try {
            VaultCrypto.of(derive(passphrase, salt, iterations)).decryptStream(data, plain)
        } catch (e: Exception) {
            throw IOException("Wrong passphrase, or the backup is damaged", e)
        }
        val records = DataInputStream(ByteArrayInputStream(plain.toByteArray()))
        val count = records.readInt()
        repeat(count) {
            val secret = readBytes(records)
            val base = VaultItem(
                type = ItemType.valueOf(records.readUTF()),
                title = "",
                pinned = records.readBoolean(),
                favourite = records.readBoolean(),
                created = records.readLong(),
                size = records.readLong(),
            )
            val content = readBytes(records)
            vault.add(ItemCodec.decode(secret, base), content.takeIf { it.isNotEmpty() }?.let(::ByteArrayInputStream))
        }
        count
    }

    private fun writeBytes(out: DataOutputStream, bytes: ByteArray) {
        out.writeInt(bytes.size)
        out.write(bytes)
    }

    private fun readBytes(input: DataInputStream): ByteArray {
        val size = input.readInt()
        if (size < 0) throw EOFException()
        return ByteArray(size).also { input.readFully(it) }
    }

    companion object {
        private val MAGIC = "MCVB1".toByteArray()
        private const val SALT_BYTES = 16
        private const val ITERATIONS = 210_000

        fun derive(passphrase: CharArray, salt: ByteArray, iterations: Int): ByteArray =
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(PBEKeySpec(passphrase, salt, iterations, 256))
                .encoded
    }
}
