package com.akib.mycompanion.domain.repository

import com.akib.mycompanion.domain.model.Contact
import kotlinx.coroutines.flow.StateFlow

interface ContactRepository {
    val contacts: StateFlow<List<Contact>>

    fun add(name: String, phone: String)

    fun remove(id: String)
}
