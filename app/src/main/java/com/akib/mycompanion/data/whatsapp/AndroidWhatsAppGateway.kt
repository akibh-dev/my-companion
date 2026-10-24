package com.akib.mycompanion.data.whatsapp

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.akib.mycompanion.domain.port.VoiceCallTarget
import com.akib.mycompanion.domain.port.WhatsAppGateway
import com.akib.mycompanion.domain.whatsapp.WhatsAppNumberMatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * WhatsApp adds a "voice call" data row to every phone contact that uses it. Launching that row
 * with ACTION_VIEW + its MIME type starts the call immediately. Reading those rows needs
 * READ_CONTACTS; without it (or if the number isn't in the phone book) we open the chat instead.
 */
class AndroidWhatsAppGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) : WhatsAppGateway {

    override fun isInstalled(): Boolean = PACKAGES.any { isPackageInstalled(it) }

    override fun findVoiceCallTarget(digits: String): VoiceCallTarget? {
        if (digits.isEmpty() || !hasContactsPermission()) return null
        val selection = VOICE_CALL_MIME_TYPES.joinToString(" OR ") { "${ContactsContract.Data.MIMETYPE}=?" }
        val projection = arrayOf(
            ContactsContract.Data._ID,
            ContactsContract.Data.MIMETYPE,
            ContactsContract.Data.DATA1,
            ContactsContract.Data.DATA3,
        )
        return try {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                projection,
                selection,
                VOICE_CALL_MIME_TYPES.toTypedArray(),
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val data1 = cursor.getString(2)
                    val data3 = cursor.getString(3)
                    if (WhatsAppNumberMatcher.matches(digits, data1, data3)) {
                        return@use VoiceCallTarget(dataId = cursor.getLong(0), mimeType = cursor.getString(1))
                    }
                }
                null
            }
        } catch (_: SecurityException) {
            null
        }
    }

    override fun startVoiceCall(target: VoiceCallTarget): Boolean {
        val uri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, target.dataId)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, target.mimeType)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return launch(intent)
    }

    override fun openChat(digits: String): Boolean {
        if (digits.isEmpty()) return false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val app = PACKAGES.firstOrNull { isPackageInstalled(it) }
        if (app != null) {
            // Prefer the WhatsApp app over a browser.
            if (launch(Intent(intent).setPackage(app))) return true
        }
        return launch(intent)
    }

    private fun launch(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private fun isPackageInstalled(packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    companion object {
        const val WHATSAPP = "com.whatsapp"
        const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"
        val PACKAGES = listOf(WHATSAPP, WHATSAPP_BUSINESS)

        const val VOICE_CALL_MIME = "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"
        const val BUSINESS_VOICE_CALL_MIME = "vnd.android.cursor.item/vnd.com.whatsapp.w4b.voip.call"
        val VOICE_CALL_MIME_TYPES = listOf(VOICE_CALL_MIME, BUSINESS_VOICE_CALL_MIME)
    }
}
