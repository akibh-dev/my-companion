package dev.akibh.mycompanion.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.akibh.mycompanion.data.local.AppDatabase
import dev.akibh.mycompanion.data.repository.RoomContactRepository
import dev.akibh.mycompanion.data.repository.RoomReminderRepository
import dev.akibh.mycompanion.domain.model.Contact
import dev.akibh.mycompanion.domain.model.Reminder
import dev.akibh.mycompanion.domain.model.Repeat
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Integration tests against a real (in-memory) Room database. */
@RunWith(AndroidJUnit4::class)
class RoomRepositoriesTest {

    private lateinit var db: AppDatabase
    private lateinit var contacts: RoomContactRepository
    private lateinit var reminders: RoomReminderRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        contacts = RoomContactRepository(db.contactDao())
        reminders = RoomReminderRepository(db.reminderDao())
    }

    @After
    fun tearDown() = db.close()

    // ---- Contacts ----

    @Test
    fun `saving a new contact assigns an id`() = runTest {
        val saved = contacts.save(Contact(name = "Mum", phone = "+8801711123456"))

        assertThat(saved.id).isGreaterThan(0L)
        assertThat(contacts.get(saved.id)).isEqualTo(saved)
        assertThat(contacts.findByPhone("+8801711123456")).isEqualTo(saved)
        assertThat(contacts.findByPhone("+8801700000000")).isNull()
    }

    @Test
    fun `contacts are listed by name ignoring case`() = runTest {
        contacts.save(Contact(name = "bob", phone = "+8801711000001"))
        contacts.save(Contact(name = "Alice", phone = "+8801711000002"))
        contacts.save(Contact(name = "carol", phone = "+8801711000003"))

        assertThat(contacts.observeAll().first().map { it.name }).containsExactly("Alice", "bob", "carol").inOrder()
    }

    @Test
    fun `saving an existing contact updates it`() = runTest {
        val saved = contacts.save(Contact(name = "Mum", phone = "+8801711123456"))

        contacts.save(saved.copy(name = "Mom"))

        assertThat(contacts.observeAll().first()).containsExactly(saved.copy(name = "Mom"))
    }

    @Test
    fun `a deleted contact can be restored with the same id`() = runTest {
        val saved = contacts.save(Contact(name = "Mum", phone = "+8801711123456"))

        contacts.delete(saved.id)
        assertThat(contacts.get(saved.id)).isNull()

        contacts.save(saved)
        assertThat(contacts.get(saved.id)).isEqualTo(saved)
    }

    // ---- Reminders ----

    @Test
    fun `reminders round-trip every field`() = runTest {
        val saved = reminders.save(
            Reminder(
                title = "Medicine",
                message = "After breakfast",
                triggerAtMillis = 1_000,
                repeat = Repeat.Weekly(setOf(DayOfWeek.SUNDAY, DayOfWeek.THURSDAY)),
                contactId = 3,
                localTime = LocalTime.of(9, 30),
                missed = true,
            ),
        )

        assertThat(reminders.get(saved.id)).isEqualTo(saved)
    }

    @Test
    fun `missed reminders are counted`() = runTest {
        reminders.save(reminder("On time", 1_000))
        val missed = reminders.save(reminder("Missed", 2_000).copy(missed = true))
        assertThat(reminders.observeMissedCount().first()).isEqualTo(1)

        reminders.save(missed.copy(missed = false))
        assertThat(reminders.observeMissedCount().first()).isEqualTo(0)
    }

    @Test
    fun `upcoming excludes delivered reminders and is soonest first`() = runTest {
        val later = reminders.save(reminder("later", 3_000))
        val sooner = reminders.save(reminder("sooner", 1_000))
        reminders.save(reminder("done", 2_000, delivered = true))

        assertThat(reminders.observeUpcoming().first()).containsExactly(sooner, later).inOrder()
        assertThat(reminders.getPending()).containsExactly(sooner, later).inOrder()
    }

    @Test
    fun `cleanup only removes old delivered reminders`() = runTest {
        val oldDone = reminders.save(reminder("old", 1_000, delivered = true))
        val newDone = reminders.save(reminder("new", 5_000, delivered = true))
        val pendingOld = reminders.save(reminder("pending", 1_000))

        reminders.deleteDeliveredBefore(2_000)

        assertThat(reminders.get(oldDone.id)).isNull()
        assertThat(reminders.get(newDone.id)).isEqualTo(newDone)
        assertThat(reminders.get(pendingOld.id)).isEqualTo(pendingOld)
    }

    @Test
    fun `saving an existing reminder updates it`() = runTest {
        val saved = reminders.save(reminder("x", 1_000))

        reminders.save(saved.copy(triggerAtMillis = 9_000, delivered = true))

        assertThat(reminders.get(saved.id)!!.triggerAtMillis).isEqualTo(9_000)
        assertThat(reminders.getPending()).isEmpty()
    }

    private fun reminder(title: String, trigger: Long, delivered: Boolean = false) =
        Reminder(title = title, message = "", triggerAtMillis = trigger, delivered = delivered)
}
