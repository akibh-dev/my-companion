package com.akib.mycompanion.core.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM with a key that never leaves the Android Keystore (plan §3: Jetpack security-crypto is
 * deprecated, so this builds on the Keystore directly). Each value gets its own random IV, stored in
 * front of the ciphertext. Tampered or foreign data fails to decrypt instead of reading wrong.
 */
class FieldCipher(private val key: () -> SecretKey) {

    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(plain)
    }

    fun decrypt(sealed: ByteArray): ByteArray {
        require(sealed.size > IV_BYTES) { "Too short to be encrypted data" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
        return cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
    }

    fun encryptText(text: String): String = Base64.encodeToString(encrypt(text.toByteArray()), Base64.NO_WRAP)

    fun decryptText(sealed: String): String = String(decrypt(Base64.decode(sealed, Base64.NO_WRAP)))

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
        const val IV_BYTES = 12
        private const val KEYSTORE = "AndroidKeyStore"

        /**
         * A cipher whose key lives in the Keystore under [alias], created on first use.
         * [requireUnlock] keys only work for a short while after a biometric/PIN unlock (the vault).
         */
        fun keystore(
            alias: String,
            requireUnlock: Boolean = false,
            unlockSeconds: Int = 60
        ): FieldCipher = FieldCipher {
            val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            val existing = store.getKey(alias, null) as? SecretKey
            existing ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
                val purposes = KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                val spec = KeyGenParameterSpec.Builder(alias, purposes)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .apply {
                        if (requireUnlock) {
                            setUserAuthenticationRequired(true)
                            setUserAuthenticationParameters(
                                unlockSeconds,
                                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                            )
                        }
                    }
                    .build()
                init(spec)
                generateKey()
            }
        }
    }
}
