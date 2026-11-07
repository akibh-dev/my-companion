package com.akib.mycompanion.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.data.local.AppDatabase
import com.akib.mycompanion.data.local.MIGRATION_1_2
import com.akib.mycompanion.data.local.MIGRATION_2_3
import com.akib.mycompanion.data.local.MIGRATION_3_4
import com.akib.mycompanion.domain.model.ReminderSound
import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Plan13MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `version two data survives both upgrades and old reminders keep default sound`() = runBlocking {
        migrateFrom(2)
    }

    @Test
    fun `version three sound choices survive the history upgrade`() = runBlocking {
        migrateFrom(3)
    }

    private suspend fun migrateFrom(version: Int) {
        val name = "plan13-${UUID.randomUUID()}.db"
        try {
            val config = SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE contacts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "name TEXT NOT NULL, " +
                                "phone TEXT NOT NULL)",
                        )
                        db.execSQL("CREATE UNIQUE INDEX index_contacts_phone ON contacts (phone)")
                        db.execSQL(
                            "CREATE TABLE reminders (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "title TEXT NOT NULL, " +
                                "message TEXT NOT NULL, trigger_at INTEGER NOT NULL, " +
                                "repeat TEXT NOT NULL, contact_id INTEGER, " +
                                "delivered INTEGER NOT NULL)",
                        )
                        db.execSQL("CREATE INDEX index_reminders_trigger_at ON reminders (trigger_at)")
                        MIGRATION_1_2.migrate(db)
                        db.execSQL("INSERT INTO contacts (id,name,phone) VALUES (1,'Fixture','123')")
                        db.execSQL(
                            "INSERT INTO reminders (id,title,message,trigger_at,repeat,contact_id,delivered) VALUES " +
                                "(1,'Keep reminder','Message',9999999999999,'EVERY:60',1,0)",
                        )
                        db.execSQL("INSERT INTO playlists (id,name,created_at) VALUES (1,'Keep playlist',100)")
                        db.execSQL(
                            "INSERT INTO playlist_items " +
                                "(playlist_id,video_id,title,author," +
                                "duration_seconds,thumbnail_url,position,added_at) " +
                                "VALUES " +
                                "(1,'abcdefghijk','Keep video','Author',60,NULL,0,100)",
                        )
                        if (version == 3) {
                            MIGRATION_2_3.migrate(db)
                            db.execSQL("UPDATE reminders SET sound='SILENT' WHERE id=1")
                            db.execSQL(
                                "INSERT INTO reminder_sounds (id,name,uri,mime_type,duration_ms,byte_count) VALUES " +
                                    "('fixture','Keep sound','content://fixture/audio','audio/ogg',1000,100)",
                            )
                        }
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
            FrameworkSQLiteOpenHelperFactory().create(config).use { it.writableDatabase }
            val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4).build()
            try {
                assertThat(db.contactDao().get(1)?.name).isEqualTo("Fixture")
                val reminder = db.reminderDao().get(1)!!.toDomain()
                assertThat(reminder.title).isEqualTo("Keep reminder")
                assertThat(reminder.repeat.encode()).isEqualTo("EVERY:60")
                assertThat(
                    reminder.sound
                ).isEqualTo(if (version == 3) ReminderSound.Silent else ReminderSound.Default)
                assertThat(db.playlistDao().items(1).single().title).isEqualTo("Keep video")
                assertThat(db.reminderSoundDao().count()).isEqualTo(if (version == 3) 1 else 0)
                db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM playback_history").use {
                    assertThat(it.moveToFirst()).isTrue()
                    assertThat(it.getInt(0)).isEqualTo(0)
                }
            } finally {
                db.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
