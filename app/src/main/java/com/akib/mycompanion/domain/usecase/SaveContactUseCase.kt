package com.akib.mycompanion.domain.usecase

import com.akib.mycompanion.domain.model.Contact
import com.akib.mycompanion.domain.model.PhoneNumbers
import com.akib.mycompanion.domain.repository.ContactRepository
import javax.inject.Inject

sealed interface SaveContactResult {
    data class Saved(val contact: Contact) : SaveContactResult

    data object MissingName : SaveContactResult

    data object InvalidPhone : SaveContactResult

    /** Another saved person already has this number. */
    data class DuplicatePhone(val existing: Contact) : SaveContactResult
}

class SaveContactUseCase @Inject constructor(private val contacts: ContactRepository) {

    /** Adds a new contact when [id] is null, otherwise updates that contact. */
    suspend operator fun invoke(id: Long?, name: String, phone: String): SaveContactResult {
        val cleanName = name.trim().replace(Regex("\\s+"), " ")
        if (cleanName.isEmpty()) return SaveContactResult.MissingName
        val normalized = PhoneNumbers.normalize(phone) ?: return SaveContactResult.InvalidPhone

        val existing = contacts.findByPhone(normalized)
        if (existing != null && existing.id != id) return SaveContactResult.DuplicatePhone(existing)

        val saved = contacts.save(Contact(id = id ?: 0, name = cleanName, phone = normalized))
        return SaveContactResult.Saved(saved)
    }
}
