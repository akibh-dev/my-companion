package com.akib.mycompanion.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ContactEntity::class, ReminderEntity::class, PlaylistEntity::class, PlaylistItemEntity::class,
        ReminderSoundEntity::class, PlaybackHistoryEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao

    abstract fun reminderDao(): ReminderDao

    abstract fun playlistDao(): PlaylistDao

    abstract fun reminderSoundDao(): ReminderSoundDao

    abstract fun playbackHistoryDao(): PlaybackHistoryDao

    companion object {
        const val NAME = "companion.db"
    }
}
