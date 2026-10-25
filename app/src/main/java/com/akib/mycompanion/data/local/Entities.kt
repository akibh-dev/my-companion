package com.akib.mycompanion.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.akib.mycompanion.domain.model.Contact
import com.akib.mycompanion.domain.model.Reminder
import com.akib.mycompanion.domain.model.Repeat

@Entity(tableName = "contacts", indices = [Index(value = ["phone"], unique = true)])
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
) {
    fun toDomain() = Contact(id = id, name = name, phone = phone)

    companion object {
        fun from(contact: Contact) = ContactEntity(id = contact.id, name = contact.name, phone = contact.phone)
    }
}

@Entity(tableName = "reminders", indices = [Index("trigger_at")])
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val message: String,
    @ColumnInfo(name = "trigger_at") val triggerAtMillis: Long,
    val repeat: String,
    @ColumnInfo(name = "contact_id") val contactId: Long?,
    val delivered: Boolean,
) {
    fun toDomain() = Reminder(
        id = id,
        title = title,
        message = message,
        triggerAtMillis = triggerAtMillis,
        repeat = Repeat.decode(repeat),
        contactId = contactId,
        delivered = delivered,
    )

    companion object {
        fun from(reminder: Reminder) = ReminderEntity(
            id = reminder.id,
            title = reminder.title,
            message = reminder.message,
            triggerAtMillis = reminder.triggerAtMillis,
            repeat = reminder.repeat.encode(),
            contactId = reminder.contactId,
            delivered = reminder.delivered,
        )
    }
}
