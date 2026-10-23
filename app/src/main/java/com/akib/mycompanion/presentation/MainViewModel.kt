package com.akib.mycompanion.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.akib.mycompanion.data.CallResult
import com.akib.mycompanion.data.SharedPrefsContactRepository
import com.akib.mycompanion.data.WhatsAppCaller
import com.akib.mycompanion.domain.model.Contact
import com.akib.mycompanion.domain.repository.ContactRepository
import com.akib.mycompanion.notifications.ReminderScheduler

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repository: ContactRepository = SharedPrefsContactRepository(app)

    val contacts = repository.contacts

    fun addContact(name: String, phone: String) = repository.add(name, phone)

    fun removeContact(contact: Contact) = repository.remove(contact.id)

    fun callOnWhatsApp(contact: Contact): CallResult =
        WhatsAppCaller.call(getApplication(), contact)

    fun notify(title: String, message: String, delayMinutes: Int) =
        ReminderScheduler.schedule(getApplication(), title, message, delayMinutes)
}
