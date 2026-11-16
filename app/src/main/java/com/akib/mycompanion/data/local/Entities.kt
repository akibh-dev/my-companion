package com.akib.mycompanion.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.akib.mycompanion.domain.model.Contact
import com.akib.mycompanion.domain.model.Reminder
import com.akib.mycompanion.domain.model.ReminderSound
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
    @ColumnInfo(defaultValue = "'DEFAULT'") val sound: String = "DEFAULT",
    @ColumnInfo(defaultValue = "0") val paused: Boolean = false,
) {
    fun toDomain() = Reminder(
        id = id,
        title = title,
        message = message,
        triggerAtMillis = triggerAtMillis,
        repeat = Repeat.decode(repeat),
        contactId = contactId,
        delivered = delivered,
        sound = ReminderSound.decode(sound),
        paused = paused,
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
            sound = reminder.sound.encode(),
            paused = reminder.paused,
        )
    }
}

/** Version 5 adds per-reminder pause. Existing reminders start unpaused. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reminders ADD COLUMN paused INTEGER NOT NULL DEFAULT 0")
    }
}
