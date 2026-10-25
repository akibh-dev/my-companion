package com.akib.mycompanion.autoreply

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.autoreply.data.ContactLookup
import com.akib.mycompanion.autoreply.di.AutoReplyModule
import com.akib.mycompanion.core.settings.Feature
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class ContactLookupTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val lookup = ContactLookup(app)

    /** Answers the two queries ContactLookup makes: phone lookup by number, contacts by name. */
    class FakeContacts : ContentProvider() {
        override fun onCreate() = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            val column = projection?.firstOrNull() ?: ContactsContract.PhoneLookup._ID
            val cursor = MatrixCursor(arrayOf(column))
            val matches = if (uri.pathSegments.firstOrNull() == "phone_lookup") {
                val digits = uri.lastPathSegment.orEmpty().filter { it.isDigit() }
                people.filter { (_, number) -> number.filter { it.isDigit() }.endsWith(digits.takeLast(SUFFIX)) }
            } else {
                people.filter { (name, _) -> name.equals(selectionArgs?.firstOrNull(), ignoreCase = true) }
            }
            matches.forEachIndexed { index, (name, _) ->
                cursor.addRow(arrayOf<Any>(if (column == ContactsContract.PhoneLookup.DISPLAY_NAME) name else index))
            }
            return cursor
        }

        override fun getType(uri: Uri): String? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) =
            0

        companion object {
            /** (name, number) */
            val people = mutableListOf<Pair<String, String>>()
            const val SUFFIX = 9
        }
    }

    @Before
    fun setUp() {
        FakeContacts.people.clear()
        FakeContacts.people += "Rahim Uddin" to "+8801711123456"
        Robolectric.setupContentProvider(FakeContacts::class.java, ContactsContract.AUTHORITY)
    }

    @Test
    fun `without contacts access nothing is known`() {
        shadowOf(app).denyPermissions(Manifest.permission.READ_CONTACTS)

        assertThat(lookup.isContact("Rahim Uddin")).isNull()
        assertThat(lookup.nameFor("+8801711123456")).isNull()
    }

    @Test
    fun `a saved name is a contact, ignoring case and spaces around it`() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS)

        assertThat(lookup.isContact("rahim uddin ")).isTrue()
        assertThat(lookup.isContact("Karim")).isFalse()
    }

    @Test
    fun `a number is looked up as a number, however it's written`() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS)

        assertThat(lookup.isContact("+880 1711-123456")).isTrue()
        assertThat(lookup.isContact("01711123456")).isTrue()
        assertThat(lookup.isContact("+880 1999 000000")).isFalse()
    }

    @Test
    fun `a short code is treated as a name, not a number`() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS)
        FakeContacts.people += "12345" to "+8801700000000"

        assertThat(lookup.isContact("12345")).isTrue()
    }

    @Test
    fun `the saved name for a number`() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS)

        assertThat(lookup.nameFor("+8801711123456")).isEqualTo("Rahim Uddin")
        assertThat(lookup.nameFor("+8801999000000")).isNull()
    }

    @Test
    fun `contacts are asked for auto-reply, phone access for missed-call texts`() {
        val contacts = AutoReplyModule.contacts()
        val phone = AutoReplyModule.missedCalls()

        assertThat(contacts.feature).isEqualTo(Feature.AUTO_REPLY)
        assertThat(contacts.runtimePermissions).containsExactly(Manifest.permission.READ_CONTACTS)
        assertThat(phone.feature).isEqualTo(Feature.MISSED_CALL_SMS)
        assertThat(phone.runtimePermissions).containsExactly(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.SEND_SMS,
        )
        assertThat(contacts.settingsIntent(app).action).isEqualTo(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        assertThat(contacts.settingsIntent(app).data?.schemeSpecificPart).isEqualTo(app.packageName)
    }

    @Test
    fun `phone access counts only when all three permissions are granted`() {
        val phone = AutoReplyModule.missedCalls()
        shadowOf(app).denyPermissions(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.SEND_SMS,
        )
        assertThat(phone.isGranted(app)).isFalse()

        shadowOf(app).grantPermissions(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG)
        assertThat(phone.isGranted(app)).isFalse()

        shadowOf(app).grantPermissions(Manifest.permission.SEND_SMS)
        assertThat(phone.isGranted(app)).isTrue()
    }
}
