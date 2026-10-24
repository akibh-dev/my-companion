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
import com.akib.mycompanion.data.repository.RoomContactRepository
import com.akib.mycompanion.data.repository.RoomPlaylistRepository
import com.akib.mycompanion.fakes.FakeClock
import com.akib.mycompanion.fakes.video
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistStorageTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var db: AppDatabase? = null

    @After
    fun tearDown() {
        db?.close()
        context.deleteDatabase(MIGRATED_DB)
    }

    private fun repository(): RoomPlaylistRepository {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        db = database
        return RoomPlaylistRepository(database.playlistDao(), FakeClock())
    }

    @Test
    fun `playlists list with counts and total length, by name`() = runTest {
        val repo = repository()
        val walk = repo.create("walk")
        val focus = repo.create("Focus")
        repo.addItem(walk.id, video("aaaaaaaaaaa", seconds = 120))
        repo.addItem(walk.id, video("bbbbbbbbbbb", seconds = 180))

        val all = repo.observePlaylists().first()

        assertThat(all.map { it.name }).containsExactly("Focus", "walk").inOrder()
        assertThat(all.first { it.id == walk.id }.itemCount).isEqualTo(2)
        assertThat(all.first { it.id == walk.id }.totalSeconds).isEqualTo(300)
        assertThat(all.first { it.id == focus.id }.itemCount).isEqualTo(0)
    }

    @Test
    fun `items keep insertion order and every field`() = runTest {
        val repo = repository()
        val list = repo.create("Mix")
        val a = video("aaaaaaaaaaa", "First")
        val b = video("bbbbbbbbbbb", "Second")

        repo.addItem(list.id, a)
        repo.addItem(list.id, b)

        val items = repo.observeItems(list.id).first()
        assertThat(items.map { it.video }).containsExactly(a, b).inOrder()
        assertThat(items.map { it.position }).containsExactly(0, 1).inOrder()
        assertThat(repo.contains(list.id, a.id)).isTrue()
        assertThat(repo.contains(list.id, "ccccccccccc")).isFalse()
    }

    @Test
    fun `removing an item and renaming`() = runTest {
        val repo = repository()
        val list = repo.create("Mix")
        val first = repo.addItem(list.id, video("aaaaaaaaaaa"))
        repo.addItem(list.id, video("bbbbbbbbbbb"))

        repo.removeItem(first.id)
        repo.rename(list.id, "Evening")

        assertThat(repo.items(list.id).map { it.video.id }).containsExactly("bbbbbbbbbbb")
        assertThat(repo.get(list.id)!!.name).isEqualTo("Evening")
        assertThat(repo.findByName("evening")!!.id).isEqualTo(list.id)
    }

    @Test
    fun `deleting a playlist deletes its items`() = runTest {
        val repo = repository()
        val list = repo.create("Mix")
        repo.addItem(list.id, video("aaaaaaaaaaa"))

        repo.delete(list.id)

        assertThat(repo.get(list.id)).isNull()
        assertThat(repo.items(list.id)).isEmpty()
    }

    @Test
    fun `upgrading from version 1 keeps contacts and adds playlists`() = runTest {
        createVersion1Database()

        val database = Room.databaseBuilder(context, AppDatabase::class.java, MIGRATED_DB)
            .addMigrations(MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        db = database

        val contacts = RoomContactRepository(database.contactDao()).observeAll().first()
        assertThat(contacts.map { it.name to it.phone }).containsExactly("Mum" to "+8801711123456")

        val playlists = RoomPlaylistRepository(database.playlistDao(), FakeClock())
        val list = playlists.create("After upgrade")
        playlists.addItem(list.id, video("aaaaaaaaaaa"))
        assertThat(playlists.items(list.id)).hasSize(1)
    }

    /** Recreates the schema shipped in the first release, with some data in it. */
    private fun createVersion1Database() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(MIGRATED_DB)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        VERSION_1_SCHEMA.forEach(db::execSQL)
                        db.execSQL("INSERT INTO contacts (name, phone) VALUES ('Mum', '+8801711123456')")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            )
            .build()
        FrameworkSQLiteOpenHelperFactory().create(config).apply {
            writableDatabase.close()
            close()
        }
    }

    private companion object {
        const val MIGRATED_DB = "migration-test.db"

        /** Exactly what Room created for version 1 (contacts + reminders). */
        val VERSION_1_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `contacts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `phone` TEXT NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_contacts_phone` ON `contacts` (`phone`)",
            "CREATE TABLE IF NOT EXISTS `reminders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, `message` TEXT NOT NULL, `trigger_at` INTEGER NOT NULL, " +
                "`repeat` TEXT NOT NULL, `contact_id` INTEGER, `delivered` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_reminders_trigger_at` ON `reminders` (`trigger_at`)",
        )
    }
}
