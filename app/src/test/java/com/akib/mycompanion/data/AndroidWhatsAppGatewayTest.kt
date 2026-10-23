package com.akib.mycompanion.data

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.data.whatsapp.AndroidWhatsAppGateway
import com.akib.mycompanion.data.whatsapp.AndroidWhatsAppGateway.Companion.BUSINESS_VOICE_CALL_MIME
import com.akib.mycompanion.data.whatsapp.AndroidWhatsAppGateway.Companion.VOICE_CALL_MIME
import com.akib.mycompanion.domain.port.VoiceCallTarget
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class AndroidWhatsAppGatewayTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val gateway = AndroidWhatsAppGateway(app)

    @Before
    fun setUp() {
        FakeContactsProvider.rows.clear()
        Robolectric.setupContentProvider(FakeContactsProvider::class.java, ContactsContract.AUTHORITY)
    }

    @Test
    fun `detects WhatsApp and WhatsApp Business`() {
        assertThat(gateway.isInstalled()).isFalse()

        shadowOf(app.packageManager).addPackage(AndroidWhatsAppGateway.WHATSAPP_BUSINESS)

        assertThat(gateway.isInstalled()).isTrue()
    }

    @Test
    fun `finds the voice call entry for the number`() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS)
        FakeContactsProvider.rows += Row(10, VOICE_CALL_MIME, "8801811000000@s.whatsapp.net", "Voice call")
        FakeContactsProvider.rows += Row(42, VOICE_CALL_MIME, "8801711123456@s.whatsapp.net", "Voice call")
        FakeContactsProvider.rows += Row(50, "vnd.android.cursor.item/phone_v2", "+8801711123456", null)

        assertThat(gateway.findVoiceCallTarget("8801711123456")).isEqualTo(VoiceCallTarget(42, VOICE_CALL_MIME))
    }

    @Test
    fun `finds WhatsApp Business entries too`() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS)
        FakeContactsProvider.rows += Row(7, BUSINESS_VOICE_CALL_MIME, "8801711123456@s.whatsapp.net", null)

        assertThat(gateway.findVoiceCallTarget("8801711123456")).isEqualTo(VoiceCallTarget(7, BUSINESS_VOICE_CALL_MIME))
    }

    @Test
    fun `no entry for an unknown number`() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS)
        FakeContactsProvider.rows += Row(42, VOICE_CALL_MIME, "8801711123456@s.whatsapp.net", null)

        assertThat(gateway.findVoiceCallTarget("8801999999999")).isNull()
    }

    @Test
    fun `without contacts access there is no lookup`() {
        shadowOf(app).denyPermissions(Manifest.permission.READ_CONTACTS)
        FakeContactsProvider.rows += Row(42, VOICE_CALL_MIME, "8801711123456@s.whatsapp.net", null)

        assertThat(gateway.findVoiceCallTarget("8801711123456")).isNull()
    }

    @Test
    fun `starting a call views the WhatsApp contact row`() {
        assertThat(gateway.startVoiceCall(VoiceCallTarget(42, VOICE_CALL_MIME))).isTrue()

        val intent = shadowOf(app).nextStartedActivity
        assertThat(intent.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(intent.data).isEqualTo(Uri.parse("content://com.android.contacts/data/42"))
        assertThat(intent.type).isEqualTo(VOICE_CALL_MIME)
        assertThat(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
    }

    @Test
    fun `opening the chat prefers the WhatsApp app`() {
        shadowOf(app.packageManager).addPackage(AndroidWhatsAppGateway.WHATSAPP)

        assertThat(gateway.openChat("8801711123456")).isTrue()

        val intent = shadowOf(app).nextStartedActivity
        assertThat(intent.data).isEqualTo(Uri.parse("https://wa.me/8801711123456"))
        assertThat(intent.`package`).isEqualTo(AndroidWhatsAppGateway.WHATSAPP)
    }

    @Test
    fun `opening the chat without the app uses any handler`() {
        assertThat(gateway.openChat("8801711123456")).isTrue()

        val intent = shadowOf(app).nextStartedActivity
        assertThat(intent.data).isEqualTo(Uri.parse("https://wa.me/8801711123456"))
        assertThat(intent.`package`).isNull()
    }

    @Test
    fun `an empty number opens nothing`() {
        assertThat(gateway.openChat("")).isFalse()
        assertThat(shadowOf(app).nextStartedActivity).isNull()
    }

    data class Row(val id: Long, val mime: String, val data1: String?, val data3: String?)

    /** Stands in for the system Contacts provider: answers ContactsContract.Data queries. */
    class FakeContactsProvider : ContentProvider() {
        override fun onCreate() = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            val columns = projection ?: arrayOf(ContactsContract.Data._ID)
            val cursor = MatrixCursor(columns)
            rows.filter { selectionArgs == null || it.mime in selectionArgs }.forEach { row ->
                cursor.addRow(
                    columns.map { column ->
                        when (column) {
                            ContactsContract.Data._ID -> row.id
                            ContactsContract.Data.MIMETYPE -> row.mime
                            ContactsContract.Data.DATA1 -> row.data1
                            ContactsContract.Data.DATA3 -> row.data3
                            else -> null
                        }
                    },
                )
            }
            return cursor
        }

        override fun getType(uri: Uri): String? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

        companion object {
            val rows = mutableListOf<Row>()
        }
    }
}
