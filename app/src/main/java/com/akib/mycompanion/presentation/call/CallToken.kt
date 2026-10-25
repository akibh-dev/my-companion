package com.akib.mycompanion.presentation.call

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * A random secret, made once per install and kept in private app storage. Our own shortcuts and
 * notification buttons put it in the [CallActivity] intent; an intent without it didn't come from us.
 *
 * CallActivity has to be exported (on Android 7.x the launcher itself starts pinned shortcuts), so
 * without this any app could start it with a guessed contact id and make the phone call that person.
 * Other apps can't read the secret: it's in our private storage, and only the launcher sees shortcut intents.
 */
object CallToken {
    private const val PREFS = "call_token"
    private const val KEY = "token"
    private const val BYTES = 32

    fun get(context: Context): String = synchronized(this) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY, null) ?: create().also { token ->
            // commit, not apply: icons carrying the token must never outlive a token that wasn't saved.
            prefs.edit().putString(KEY, token).commit()
        }
    }

    /** Constant-time compare, so timing doesn't reveal how much of a guess was right. */
    fun isValid(context: Context, candidate: String?): Boolean =
        candidate != null && MessageDigest.isEqual(candidate.toByteArray(), get(context).toByteArray())

    private fun create(): String {
        val bytes = ByteArray(BYTES).also(SecureRandom()::nextBytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)
    }
}
