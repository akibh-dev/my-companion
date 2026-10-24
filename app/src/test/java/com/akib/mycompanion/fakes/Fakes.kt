package com.akib.mycompanion.fakes

import com.akib.mycompanion.domain.model.Contact
import com.akib.mycompanion.domain.model.Reminder
import com.akib.mycompanion.domain.port.AlarmScheduler
import com.akib.mycompanion.domain.port.PhonebookSource
import com.akib.mycompanion.domain.port.PickedPhone
import com.akib.mycompanion.domain.port.ReminderNotifier
import com.akib.mycompanion.domain.port.VoiceCallTarget
import com.akib.mycompanion.domain.port.WhatsAppGateway
import com.akib.mycompanion.domain.repository.ContactRepository
import com.akib.mycompanion.domain.repository.ReminderRepository
import com.akib.mycompanion.domain.time.Clock
import com.akib.mycompanion.presentation.shortcuts.HomeShortcuts
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

val DHAKA: ZoneId = ZoneId.of("Asia/Dhaka")

/** Epoch millis for a local date-time in [zone]. */
fun millisAt(year: Int, month: Int, day: Int, hour: Int, minute: Int, zone: ZoneId = DHAKA): Long =
    ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

/** Friday 23 Oct 2026, 10:00 in Dhaka unless told otherwise. */
class FakeClock(
    var now: Long = millisAt(2026, 10, 23, 10, 0),
    override val zone: ZoneId = DHAKA,
) : Clock {
    override fun nowMillis(): Long = now

    fun advanceMinutes(minutes: Long) {
        now += minutes * 60_000
    }
}

class FakeContactRepository(initial: List<Contact> = emptyList()) : ContactRepository {
    private val state = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1

    val all: List<Contact> get() = state.value

    override fun observeAll(): Flow<List<Contact>> = state.map { list -> list.sortedBy { it.name.lowercase() } }

    override suspend fun get(id: Long): Contact? = state.value.firstOrNull { it.id == id }

    override suspend fun findByPhone(phone: String): Contact? = state.value.firstOrNull { it.phone == phone }

    override suspend fun save(contact: Contact): Contact {
        val saved = if (contact.id == 0L) contact.copy(id = nextId++) else contact
        state.value = state.value.filterNot { it.id == saved.id } + saved
        return saved
    }

    override suspend fun delete(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }
}

class FakeReminderRepository(initial: List<Reminder> = emptyList()) : ReminderRepository {
    private val state = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1

    val all: List<Reminder> get() = state.value

    override fun observeUpcoming(): Flow<List<Reminder>> =
        state.map { list -> list.filterNot { it.delivered }.sortedBy { it.triggerAtMillis } }

    override suspend fun get(id: Long): Reminder? = state.value.firstOrNull { it.id == id }

    override suspend fun getPending(): List<Reminder> =
        state.value.filterNot { it.delivered }.sortedBy { it.triggerAtMillis }

    override suspend fun save(reminder: Reminder): Reminder {
        val saved = if (reminder.id == 0L) reminder.copy(id = nextId++) else reminder
        state.value = state.value.filterNot { it.id == saved.id } + saved
        return saved
    }

    override suspend fun delete(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun deleteDeliveredBefore(beforeMillis: Long) {
        state.value = state.value.filterNot { it.delivered && it.triggerAtMillis < beforeMillis }
    }
}

class FakeAlarmScheduler : AlarmScheduler {
    /** reminder id → trigger time of the currently armed alarm. */
    val scheduled = mutableMapOf<Long, Long>()
    val cancelled = mutableListOf<Long>()

    override fun schedule(reminder: Reminder) {
        scheduled[reminder.id] = reminder.triggerAtMillis
    }

    override fun cancel(reminderId: Long) {
        scheduled.remove(reminderId)
        cancelled += reminderId
    }
}

class FakeNotifier(var enabled: Boolean = true) : ReminderNotifier {
    val shown = mutableListOf<Pair<Reminder, Contact?>>()
    val cancelled = mutableListOf<Int>()

    override fun show(reminder: Reminder, contact: Contact?): Boolean {
        if (!enabled) return false
        shown += reminder to contact
        return true
    }

    override fun cancel(notificationId: Int) {
        cancelled += notificationId
    }
}

class FakeWhatsAppGateway(
    var installed: Boolean = true,
    var target: VoiceCallTarget? = null,
    var callWorks: Boolean = true,
    var chatWorks: Boolean = true,
) : WhatsAppGateway {
    val calls = mutableListOf<VoiceCallTarget>()
    val chats = mutableListOf<String>()

    override fun isInstalled(): Boolean = installed

    override fun findVoiceCallTarget(digits: String): VoiceCallTarget? = target

    override fun startVoiceCall(target: VoiceCallTarget): Boolean {
        calls += target
        return callWorks
    }

    override fun openChat(digits: String): Boolean {
        chats += digits
        return chatWorks
    }
}

class FakePhonebook(var result: PickedPhone? = null) : PhonebookSource {
    val requested = mutableListOf<String>()

    override suspend fun read(uri: String): PickedPhone? {
        requested += uri
        return result
    }
}

class FakeHomeShortcuts(override var isSupported: Boolean = true) : HomeShortcuts {
    val pinned = mutableListOf<Contact>()
    val updated = mutableListOf<Contact>()

    override fun pin(contact: Contact): Boolean {
        if (!isSupported) return false
        pinned += contact
        return true
    }

    override fun update(contact: Contact) {
        updated += contact
    }
}
