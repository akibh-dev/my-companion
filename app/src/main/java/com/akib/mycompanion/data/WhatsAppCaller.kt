package com.akib.mycompanion.data

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.akib.mycompanion.domain.model.Contact

enum class CallResult {
    /** WhatsApp voice call screen was launched. */
    CALL_STARTED,

    /** Couldn't start a call directly, opened the chat instead. */
    CHAT_OPENED,

    /** WhatsApp isn't installed (or nothing could handle the intent). */
    WHATSAPP_MISSING,
}

/**
 * WhatsApp has no public "call this number" API. The reliable trick: WhatsApp adds a
 * "voip call" data row to every phone contact that uses it. Launching that row with
 * ACTION_VIEW starts the call straight away. This needs READ_CONTACTS and the number
 * to be saved in the phone's address book. Otherwise we fall back to opening the chat
 * via wa.me, where the call button is one tap away.
 */
object WhatsAppCaller {
    private const val VOIP_MIME = "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"

    fun call(context: Context, contact: Contact): CallResult {
        findVoipUri(context, contact.digits)?.let { uri ->
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, VOIP_MIME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return CallResult.CALL_STARTED
            } catch (_: ActivityNotFoundException) {
                // fall through to chat
            }
        }
        return openChat(context, contact.digits)
    }

    private fun openChat(context: Context, digits: String): CallResult {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            CallResult.CHAT_OPENED
        } catch (_: ActivityNotFoundException) {
            CallResult.WHATSAPP_MISSING
        }
    }

    private fun findVoipUri(context: Context, digits: String): Uri? {
        if (digits.isEmpty()) return null
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return null

        // WhatsApp stores the JID ("<number>@s.whatsapp.net") in DATA1 of its rows.
        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data._ID),
            "${ContactsContract.Data.MIMETYPE}=? AND ${ContactsContract.Data.DATA1} LIKE ?",
            arrayOf(VOIP_MIME, "$digits@%"),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, cursor.getLong(0))
            }
        }
        return null
    }
}
