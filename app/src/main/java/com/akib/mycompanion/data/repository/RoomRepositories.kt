package com.akib.mycompanion.data.repository

import com.akib.mycompanion.data.local.ContactDao
import com.akib.mycompanion.data.local.ContactEntity
import com.akib.mycompanion.data.local.ReminderDao
import com.akib.mycompanion.data.local.ReminderEntity
import com.akib.mycompanion.domain.model.Contact
import com.akib.mycompanion.domain.model.Reminder
import com.akib.mycompanion.domain.repository.ContactRepository
import com.akib.mycompanion.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

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
}
