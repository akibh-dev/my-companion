package com.akib.mycompanion.data

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.data.whatsapp.AndroidWhatsAppGateway
import com.akib.mycompanion.domain.port.VoiceCallLookup
import com.akib.mycompanion.domain.port.VoiceCallTarget
import com.akib.mycompanion.domain.port.WhatsAppCallFailure
import com.akib.mycompanion.domain.port.WhatsAppLaunchResult
import com.akib.mycompanion.domain.port.WhatsAppVariant
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class AndroidWhatsAppGatewayTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val gateway = AndroidWhatsAppGateway(app)
    private val number = "8801711123456"
    private val mime = AndroidWhatsAppGateway.VOICE_CALL_MIME
    private fun row(id: Long = 42, account: String = "com.whatsapp", identity: String = "$number@s.whatsapp.net"): Row {
        val row = Row(id, mime, identity, "Voice call", 100, 200 + id)
        FakeContactsProvider.rows += row
        FakeContactsProvider.accounts[row.rawId] = account
        return row
    }

    @Before fun setup() {
        FakeContactsProvider.reset()
        Robolectric.setupContentProvider(FakeContactsProvider::class.java, ContactsContract.AUTHORITY)
        shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS)
        shadowOf(app.packageManager).addPackage("com.whatsapp")
    }

    private fun handler(target: VoiceCallTarget) {
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(
            Uri.parse("content://com.android.contacts/data/${target.dataId}"),
            target.mimeType,
        ).setPackage(target.packageName)
        shadowOf(app.packageManager).addResolveInfoForIntent(
            intent,
            ResolveInfo().apply {
                isDefault = true
                activityInfo = ActivityInfo().apply {
                    packageName = target.packageName
                    name = "CallHandler"
                    exported = true
                }
            }
        )
    }

    private suspend fun target(): VoiceCallTarget =
        (gateway.resolveVoiceCall("+$number") as VoiceCallLookup.Found).targets.single()

    @Test fun `scoped full phone match verifies owning account before returning target`() = runTest {
        row()
        row(55, "untrusted.account")
        FakeContactsProvider.rows += Row(70, mime, "$number@s.whatsapp.net", null, 999, 900)
        val result = gateway.inspect("+$number")
        val target = (result.result as VoiceCallLookup.Found).targets.single()
        assertThat(target.dataId).isEqualTo(42)
        assertThat(target.packageName).isEqualTo("com.whatsapp")
        assertThat(result.candidateRows).isEqualTo(2)
        assertThat(result.phonebookMatches).isEqualTo(1)
        assertThat(FakeContactsProvider.queryThreads).doesNotContain(Thread.currentThread().name)
        assertThat(shadowOf(app).nextStartedActivity).isNull()
    }

    @Test fun `both owned accounts return candidates and business may share regular voice MIME`() = runTest {
        shadowOf(app.packageManager).addPackage("com.whatsapp.w4b")
        row()
        row(43, "com.whatsapp.w4b")
        val targets = (gateway.resolveVoiceCall(number) as VoiceCallLookup.Found).targets
        assertThat(targets.map { it.variant }).containsExactly(WhatsAppVariant.STANDARD, WhatsAppVariant.BUSINESS)
    }

    @Test fun `same variant conflicting rows are refused rather than first cursor wins`() = runTest {
        row()
        row(43)
        assertThat(
            gateway.resolveVoiceCall(number)
        ).isEqualTo(VoiceCallLookup.Unavailable(WhatsAppCallFailure.AMBIGUOUS))
    }

    @Test fun `aggregated contact sibling number cannot match a WhatsApp row`() = runTest {
        row(identity = "8801811123456@s.whatsapp.net")
        assertThat(
            gateway.resolveVoiceCall(number)
        ).isEqualTo(VoiceCallLookup.Unavailable(WhatsAppCallFailure.NO_VOICE_ROW))
    }

    @Test fun `phone lookup approximate international suffix match is rejected`() = runTest {
        FakeContactsProvider.phones = listOf(100L to "+491711123456")
        row()
        assertThat(
            gateway.resolveVoiceCall(number)
        ).isEqualTo(VoiceCallLookup.Unavailable(WhatsAppCallFailure.NO_PHONEBOOK_MATCH))
    }

    @Test fun `unknown raw account and noncall MIME never become call targets`() = runTest {
        row(account = "phone.account")
        FakeContactsProvider.rows += Row(55, "vnd.android.cursor.item/phone_v2", "+$number", null, 100, 100)
        assertThat(
            gateway.resolveVoiceCall(number)
        ).isEqualTo(VoiceCallLookup.Unavailable(WhatsAppCallFailure.NO_VOICE_ROW))
    }

    @Test fun `permission denial is distinct and does not query contacts`() = runTest {
        shadowOf(app).denyPermissions(Manifest.permission.READ_CONTACTS)
        assertThat(
            gateway.resolveVoiceCall(number)
        ).isEqualTo(VoiceCallLookup.Unavailable(WhatsAppCallFailure.PERMISSION_REQUIRED))
        assertThat(FakeContactsProvider.queryThreads).isEmpty()
    }

    @Test fun `provider null, denied and errors have explicit outcomes`() = runTest {
        FakeContactsProvider.failure = "null"
        assertThat(
            gateway.resolveVoiceCall(number)
        ).isEqualTo(VoiceCallLookup.Unavailable(WhatsAppCallFailure.PROVIDER_ERROR))
        FakeContactsProvider.failure = "denied"
        assertThat(
            gateway.resolveVoiceCall(number)
        ).isEqualTo(VoiceCallLookup.Unavailable(WhatsAppCallFailure.PROVIDER_DENIED))
        FakeContactsProvider.failure = "error"
        assertThat(
            gateway.resolveVoiceCall(number)
        ).isEqualTo(VoiceCallLookup.Unavailable(WhatsAppCallFailure.PROVIDER_ERROR))
    }

    @Test fun `fresh target launches explicit package URI MIME on caller context`() = runTest {
        row()
        val target = target()
        handler(target)
        assertThat(gateway.canHandle(target)).isTrue()
        assertThat(gateway.launchVoiceCall(target)).isEqualTo(WhatsAppLaunchResult.HandedOff)
        val intent = shadowOf(app).nextStartedActivity
        assertThat(intent.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(intent.data.toString()).isEqualTo("content://com.android.contacts/data/42")
        assertThat(intent.type).isEqualTo(mime)
        assertThat(intent.`package`).isEqualTo("com.whatsapp")
        assertThat(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
    }

    @Test fun `stale removed or reassigned rows cannot hand off`() = runTest {
        row()
        val target = target()
        handler(target)
        FakeContactsProvider.rows.clear()
        assertThat(
            gateway.launchVoiceCall(target)
        ).isEqualTo(WhatsAppLaunchResult.Failed(WhatsAppCallFailure.STALE_TARGET))
        row(identity = "8801811123456@s.whatsapp.net")
        assertThat(
            gateway.launchVoiceCall(target)
        ).isEqualTo(WhatsAppLaunchResult.Failed(WhatsAppCallFailure.STALE_TARGET))
        assertThat(shadowOf(app).nextStartedActivity).isNull()
    }

    @Test fun `missing handler and rejected launch are distinct from missing package`() = runTest {
        row()
        val target = target()
        assertThat(
            gateway.launchVoiceCall(target)
        ).isEqualTo(WhatsAppLaunchResult.Failed(WhatsAppCallFailure.NO_HANDLER))
        handler(target)
        val rejected = AndroidWhatsAppGateway(object : ContextWrapper(app) {
            override fun startActivity(intent: Intent) {
                throw SecurityException("blocked")
            }
        })
        assertThat(
            rejected.launchVoiceCall(target)
        ).isEqualTo(WhatsAppLaunchResult.Failed(WhatsAppCallFailure.LAUNCH_DENIED))
    }

    @Test fun `chat handoff is package scoped and never opens generic browser fallback`() {
        assertThat(gateway.launchChat(number, WhatsAppVariant.BUSINESS))
            .isEqualTo(WhatsAppLaunchResult.Failed(WhatsAppCallFailure.APP_NOT_INSTALLED))
        assertThat(gateway.openChat("")).isFalse()
        assertThat(shadowOf(app).nextStartedActivity).isNull()
    }

    data class Row(
        val id: Long,
        val mime: String,
        val data1: String?,
        val data3: String?,
        val contactId: Long,
        val rawId: Long,
    )

    /** Enforces URI, selection and ownership scopes, rather than accepting arbitrary broad queries. */
    class FakeContactsProvider : ContentProvider() {
        override fun onCreate() = true
        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor? {
            queryThreads += Thread.currentThread().name
            when (failure) {
                "null" -> return null
                "denied" -> throw SecurityException()
                "error" -> error("provider")
            }
            val columns = requireNotNull(projection)
            val cursor = MatrixCursor(columns)
            when (uri.pathSegments.first()) {
                "phone_lookup" -> {
                    require(uri.lastPathSegment?.startsWith('+') == true)
                    require(selection == null && selectionArgs == null)
                    phones.forEach { (id, phone) ->
                        cursor.addRow(
                            columns.map { column ->
                                when (column) {
                                    ContactsContract.PhoneLookup._ID -> id
                                    ContactsContract.PhoneLookup.NUMBER,
                                    ContactsContract.PhoneLookup.NORMALIZED_NUMBER -> phone
                                    else -> error("Unexpected PhoneLookup projection")
                                }
                            }
                        )
                    }
                }
                "data" -> {
                    val selected = if (uri.pathSegments.size > 1) {
                        require(selection == null && selectionArgs == null)
                        rows.filter { it.id == uri.lastPathSegment!!.toLong() }
                    } else {
                        require(selection?.startsWith("contact_id IN (") == true && "mimetype IN (" in selection)
                        val args = requireNotNull(selectionArgs)
                        rows.filter { it.contactId.toString() in args && it.mime in args }
                    }
                    selected.forEach { row ->
                        cursor.addRow(
                            columns.map { column ->
                                when (column) {
                                    ContactsContract.Data._ID -> row.id
                                    ContactsContract.Data.MIMETYPE -> row.mime
                                    ContactsContract.Data.DATA1 -> row.data1
                                    ContactsContract.Data.DATA3 -> row.data3
                                    ContactsContract.Data.RAW_CONTACT_ID -> row.rawId
                                    else -> error("Unexpected Data projection")
                                }
                            }
                        )
                    }
                }
                "raw_contacts" -> {
                    require(selection == "_id=?" && selectionArgs?.size == 1)
                    require(columns.contentEquals(arrayOf(ContactsContract.RawContacts.ACCOUNT_TYPE)))
                    accounts[selectionArgs[0].toLong()]?.let { cursor.addRow(arrayOf(it)) }
                }
                else -> error("Unexpected provider URI")
            }
            return cursor
        }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
        companion object {
            val rows = mutableListOf<Row>()
            val accounts = mutableMapOf<Long, String>()
            var phones = listOf(100L to "+8801711123456")
            val queryThreads = mutableListOf<String>()
            var failure: String? = null
            fun reset() {
                rows.clear()
                accounts.clear()
                phones = listOf(100L to "+8801711123456")
                queryThreads.clear()
                failure = null
            }
        }
    }
}
