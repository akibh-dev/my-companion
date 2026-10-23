package com.akib.mycompanion.domain.repository

import com.akib.mycompanion.domain.model.Contact
import kotlinx.coroutines.flow.Flow

interface ContactRepository {
    /** All contacts, sorted by name. */
    fun observeAll(): Flow<List<Contact>>

    suspend fun get(id: Long): Contact?

    suspend fun findByPhone(phone: String): Contact?

    /** Inserts when [Contact.id] is 0, otherwise updates. Returns the saved contact with its id. */
    suspend fun save(contact: Contact): Contact

    suspend fun delete(id: Long)
}
