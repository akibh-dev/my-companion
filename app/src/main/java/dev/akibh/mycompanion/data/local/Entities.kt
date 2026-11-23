package dev.akibh.mycompanion.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.akibh.mycompanion.domain.model.Contact
import dev.akibh.mycompanion.domain.model.Reminder
import dev.akibh.mycompanion.domain.model.ReminderSound
import dev.akibh.mycompanion.domain.model.Repeat
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

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
    val link: String? = null,
    /** ISO local time ("09:00") a daily/weekly/monthly reminder fires at; null for the others. */
    @ColumnInfo(name = "local_time") val localTime: String? = null,
    @ColumnInfo(defaultValue = "0") val missed: Boolean = false,
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
        link = link,
        localTime = localTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
        missed = missed,
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
            link = reminder.link,
            localTime = reminder.localTime?.toString(),
            missed = reminder.missed,
        )
    }
}

/** Version 5 adds per-reminder pause. Existing reminders start unpaused. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reminders ADD COLUMN paused INTEGER NOT NULL DEFAULT 0")
    }
}

/** Version 6 lets a reminder open a screen (the Quran reading reminder opens the reader). */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reminders ADD COLUMN link TEXT")
    }
}

/**
 * Version 7 stores the wanted clock time of daily, weekly and monthly reminders, so a timezone or
 * daylight-saving change can't shift them. Existing ones take it from their next trigger, read in
 * the zone the phone is in now (the one they were set in).
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reminders ADD COLUMN local_time TEXT")
        val zone = ZoneId.systemDefault()
        db.query("SELECT id, trigger_at, repeat FROM reminders").use { rows ->
            while (rows.moveToNext()) {
                if (!Repeat.decode(rows.getString(2)).keepsClockTime) continue
                val time = LocalTime.ofInstant(Instant.ofEpochMilli(rows.getLong(1)), zone)
                db.execSQL("UPDATE reminders SET local_time = ? WHERE id = ?", arrayOf(time.toString(), rows.getLong(0)))
            }
        }
    }
}

/** Version 8 flags reminders whose notification couldn't be shown, for the "missed" count on Home. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reminders ADD COLUMN missed INTEGER NOT NULL DEFAULT 0")
    }
}
