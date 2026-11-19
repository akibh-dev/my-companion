package dev.akibh.mycompanion.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.akibh.mycompanion.data.alarm.AndroidAlarmScheduler
import dev.akibh.mycompanion.data.local.AppDatabase
import dev.akibh.mycompanion.data.local.ContactDao
import dev.akibh.mycompanion.data.local.MIGRATION_1_2
import dev.akibh.mycompanion.data.local.MIGRATION_2_3
import dev.akibh.mycompanion.data.local.MIGRATION_3_4
import dev.akibh.mycompanion.data.local.MIGRATION_4_5
import dev.akibh.mycompanion.data.local.PlaybackHistoryDao
import dev.akibh.mycompanion.data.local.PlaylistDao
import dev.akibh.mycompanion.data.local.ReminderDao
import dev.akibh.mycompanion.data.local.ReminderSoundDao
import dev.akibh.mycompanion.data.notification.AndroidReminderNotifier
import dev.akibh.mycompanion.data.phonebook.AndroidPhonebookSource
import dev.akibh.mycompanion.data.repository.RoomContactRepository
import dev.akibh.mycompanion.data.repository.RoomPlaylistRepository
import dev.akibh.mycompanion.data.repository.RoomReminderRepository
import dev.akibh.mycompanion.data.whatsapp.AndroidWhatsAppGateway
import dev.akibh.mycompanion.data.youtube.AudioStreamSource
import dev.akibh.mycompanion.data.youtube.NewPipeYouTube
import dev.akibh.mycompanion.domain.port.AlarmScheduler
import dev.akibh.mycompanion.domain.port.PhonebookSource
import dev.akibh.mycompanion.domain.port.Playback
import dev.akibh.mycompanion.domain.port.ReminderNotifier
import dev.akibh.mycompanion.domain.port.VideoInfoSource
import dev.akibh.mycompanion.domain.port.WhatsAppGateway
import dev.akibh.mycompanion.domain.repository.ContactRepository
import dev.akibh.mycompanion.domain.repository.PlaylistRepository
import dev.akibh.mycompanion.domain.repository.ReminderRepository
import dev.akibh.mycompanion.player.MediaControllerPlayback
import dev.akibh.mycompanion.presentation.shortcuts.AndroidHomeShortcuts
import dev.akibh.mycompanion.presentation.shortcuts.HomeShortcuts
import dev.akibh.mycompanion.system.DefaultReminderEvents
import dev.akibh.mycompanion.system.ReminderEvents
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()

    @Provides
    fun contactDao(db: AppDatabase): ContactDao = db.contactDao()

    @Provides
    fun reminderDao(db: AppDatabase): ReminderDao = db.reminderDao()

    @Provides
    fun playlistDao(db: AppDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun reminderSoundDao(db: AppDatabase): ReminderSoundDao = db.reminderSoundDao()

    @Provides
    fun playbackHistoryDao(db: AppDatabase): PlaybackHistoryDao = db.playbackHistoryDao()
}

@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {
    @Provides
    @IoDispatcher
    fun io(): CoroutineDispatcher = Dispatchers.IO

    /** Outlives any screen: used by broadcast receivers for short background work. */
    @Provides
    @Singleton
    @ApplicationScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    @Singleton
    abstract fun contacts(impl: RoomContactRepository): ContactRepository

    @Binds
    @Singleton
    abstract fun reminders(impl: RoomReminderRepository): ReminderRepository

    @Binds
    abstract fun whatsApp(impl: AndroidWhatsAppGateway): WhatsAppGateway

    @Binds
    abstract fun alarms(impl: AndroidAlarmScheduler): AlarmScheduler

    @Binds
    abstract fun notifier(impl: AndroidReminderNotifier): ReminderNotifier

    @Binds
    abstract fun phonebook(impl: AndroidPhonebookSource): PhonebookSource

    @Binds
    abstract fun shortcuts(impl: AndroidHomeShortcuts): HomeShortcuts

    @Binds
    @Singleton
    abstract fun playlists(impl: RoomPlaylistRepository): PlaylistRepository

    @Binds
    abstract fun videoInfo(impl: NewPipeYouTube): VideoInfoSource

    @Binds
    abstract fun audioStreams(impl: NewPipeYouTube): AudioStreamSource
}

/** On its own so UI tests can swap in a fake player instead of starting the media service. */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackModule {
    @Binds
    abstract fun playback(impl: MediaControllerPlayback): Playback
}

/** Kept on its own so receiver tests can replace it with @UninstallModules. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderEventsModule {
    @Binds
    abstract fun events(impl: DefaultReminderEvents): ReminderEvents
}
