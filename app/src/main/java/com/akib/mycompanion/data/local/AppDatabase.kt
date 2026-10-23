package com.akib.mycompanion.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ContactEntity::class, ReminderEntity::class, PlaylistEntity::class, PlaylistItemEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao

    abstract fun reminderDao(): ReminderDao

    abstract fun playlistDao(): PlaylistDao

    companion object {
        const val NAME = "companion.db"
    }
}
