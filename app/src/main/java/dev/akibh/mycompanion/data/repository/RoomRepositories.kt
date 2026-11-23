package dev.akibh.mycompanion.data.repository

import dev.akibh.mycompanion.data.local.ContactDao
import dev.akibh.mycompanion.data.local.ContactEntity
import dev.akibh.mycompanion.data.local.ReminderDao
import dev.akibh.mycompanion.data.local.ReminderEntity
import dev.akibh.mycompanion.domain.model.Contact
import dev.akibh.mycompanion.domain.model.Reminder
import dev.akibh.mycompanion.domain.repository.ContactRepository
import dev.akibh.mycompanion.domain.repository.ReminderRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomContactRepository @Inject constructor(private val dao: ContactDao) : ContactRepository {
    override fun observeAll(): Flow<List<Contact>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun get(id: Long): Contact? = dao.get(id)?.toDomain()

    override suspend fun findByPhone(phone: String): Contact? = dao.findByPhone(phone)?.toDomain()

    override suspend fun save(contact: Contact): Contact {
        val entity = ContactEntity.from(contact)
        return if (contact.id == 0L) {
            contact.copy(id = dao.insert(entity))
        } else {
            dao.upsert(entity)
            contact
        }
    }

    override suspend fun delete(id: Long) = dao.delete(id)
}

class RoomReminderRepository @Inject constructor(private val dao: ReminderDao) : ReminderRepository {
    override fun observeUpcoming(): Flow<List<Reminder>> =
        dao.observePending().map { list -> list.map { it.toDomain() } }

    override suspend fun get(id: Long): Reminder? = dao.get(id)?.toDomain()

    override suspend fun getPending(): List<Reminder> = dao.getPending().map { it.toDomain() }

    override suspend fun save(reminder: Reminder): Reminder {
        val entity = ReminderEntity.from(reminder)
        return if (reminder.id == 0L) {
            reminder.copy(id = dao.insert(entity))
        } else {
            dao.upsert(entity)
            reminder
        }
    }

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun deleteDeliveredBefore(beforeMillis: Long) = dao.deleteDeliveredBefore(beforeMillis)

    override fun observeMissedCount(): Flow<Int> = dao.observeMissedCount()
}
