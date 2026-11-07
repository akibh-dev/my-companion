package com.akib.mycompanion

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.core.crypto.FieldCipher
import com.akib.mycompanion.data.local.AppDatabase
import com.akib.mycompanion.data.local.MIGRATION_1_2
import com.akib.mycompanion.data.local.MIGRATION_2_3
import com.akib.mycompanion.data.local.MIGRATION_3_4
import com.akib.mycompanion.data.local.PlaylistEntity
import com.google.common.truth.Truth.assertThat
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/** Real SQLite, Android Keystore and Android crypto providers, rather than Robolectric shadows. */
@RunWith(AndroidJUnit4::class)
class DeviceStorageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun keystoreEncryptsBanglaTextAndRejectsTampering() {
        val alias = "device-test-${UUID.randomUUID()}"
        try {
            val cipher = FieldCipher.keystore(alias)
            val plain = "ব্যক্তিগত নোট — device fixture"
            val first = cipher.encryptText(plain)
            val second = cipher.encryptText(plain)
            assertThat(first).isNotEqualTo(second)
            assertThat(FieldCipher.keystore(alias).decryptText(first)).isEqualTo(plain)
            val bytes = cipher.encrypt(plain.toByteArray())
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
            assertThrows(Exception::class.java) { cipher.decrypt(bytes) }
        } finally {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        }
    }

    @Test
    fun versionOneDatabaseMigratesWithoutLosingPeopleOrReminders() = runBlocking {
        val name = "device-migration-${UUID.randomUUID()}.db"
        try {
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
                old.execSQL(
                    "CREATE TABLE contacts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, phone TEXT NOT NULL)",
                )
                old.execSQL("CREATE UNIQUE INDEX index_contacts_phone ON contacts (phone)")
                old.execSQL(
                    "CREATE TABLE reminders (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "title TEXT NOT NULL, message TEXT NOT NULL, trigger_at INTEGER NOT NULL, " +
                        "repeat TEXT NOT NULL, contact_id INTEGER, delivered INTEGER NOT NULL)",
                )
                old.execSQL("CREATE INDEX index_reminders_trigger_at ON reminders (trigger_at)")
                old.execSQL("INSERT INTO contacts (name, phone) VALUES ('Device fixture', '+8801711987654')")
                old.execSQL(
                    "INSERT INTO reminders (title, message, trigger_at, repeat, delivered) " +
                        "VALUES ('Migration fixture', 'Keep me', 9999999999999, 'NONE', 0)",
                )
                old.version = 1
            }
            val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
            try {
                assertThat(db.contactDao().get(1)?.name).isEqualTo("Device fixture")
                assertThat(db.reminderDao().get(1)?.message).isEqualTo("Keep me")
                val id = db.playlistDao().insert(PlaylistEntity(name = "After migration", createdAt = 1))
                assertThat(db.playlistDao().summary(id)?.name).isEqualTo("After migration")
            } finally {
                db.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
