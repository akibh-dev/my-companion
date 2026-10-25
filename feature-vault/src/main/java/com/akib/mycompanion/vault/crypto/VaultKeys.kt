package com.akib.mycompanion.vault.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import com.akib.mycompanion.core.crypto.FieldCipher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject

/**
 * Wraps the vault's data key with a key the user has to unlock (fingerprint or PIN), so the data key
 * is only ever readable for a moment after an unlock. Swappable in tests (no Keystore on the JVM).
 */
interface KeyWrapper {
    /** False when the phone has no screen lock, so nothing can protect the key. */
    fun canProtect(): Boolean

    /** @throws NeedsUnlock when the user hasn't just unlocked. */
    fun wrap(dataKey: ByteArray): ByteArray

    fun unwrap(wrapped: ByteArray): ByteArray

    class NeedsUnlock : Exception("Unlock first")
}

/** Seals items shared while the vault is locked (FR-2.4): needs no unlock, so the inbox can be written any time. */
interface InboxCipher {
    fun encrypt(plain: ByteArray): ByteArray

    fun decrypt(sealed: ByteArray): ByteArray
}

class KeystoreKeyWrapper @Inject constructor(@ApplicationContext private val context: Context) : KeyWrapper {

    override fun canProtect(): Boolean =
        context.getSystemService(android.app.KeyguardManager::class.java).isDeviceSecure

    override fun wrap(dataKey: ByteArray): ByteArray = guarded {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        cipher.iv + cipher.doFinal(dataKey)
    }

    override fun unwrap(wrapped: ByteArray): ByteArray = guarded {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, wrapped, 0, IV_BYTES))
        cipher.doFinal(wrapped, IV_BYTES, wrapped.size - IV_BYTES)
    }

    private fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (_: UserNotAuthenticatedException) {
        throw KeyWrapper.NeedsUnlock()
    }

    /** Usable for [UNLOCK_SECONDS] after a fingerprint or PIN unlock; deleted if the screen lock is removed. */
    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(
                UNLOCK_SECONDS,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
            )
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(spec)
            generateKey()
        }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "vault_kek"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val IV_BYTES = 12
        const val UNLOCK_SECONDS = 30
    }
}

class KeystoreInboxCipher @Inject constructor() : InboxCipher {
    private val cipher = FieldCipher.keystore("vault_inbox")

    override fun encrypt(plain: ByteArray) = cipher.encrypt(plain)

    override fun decrypt(sealed: ByteArray) = cipher.decrypt(sealed)
}

/** Where the wrapped data key lives. */
class WrappedKeyFile @Inject constructor(@ApplicationContext context: Context) {
    private val file = File(context.noBackupFilesDir, "vault.key")

    fun exists() = file.exists()

    fun read(): ByteArray = file.readBytes()

    fun write(bytes: ByteArray) {
        val tmp = File(file.parentFile, "vault.key.tmp")
        tmp.writeBytes(bytes)
        tmp.renameTo(file)
    }
}
