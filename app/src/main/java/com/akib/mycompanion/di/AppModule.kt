package com.akib.mycompanion.di

import android.content.Context
import androidx.room.Room
import com.akib.mycompanion.data.alarm.AndroidAlarmScheduler
import com.akib.mycompanion.data.local.AppDatabase
import com.akib.mycompanion.data.local.ContactDao
import com.akib.mycompanion.data.local.MIGRATION_1_2
import com.akib.mycompanion.data.local.PlaylistDao
import com.akib.mycompanion.data.local.ReminderDao
import com.akib.mycompanion.data.notification.AndroidReminderNotifier
import com.akib.mycompanion.data.phonebook.AndroidPhonebookSource
import com.akib.mycompanion.data.repository.RoomContactRepository
import com.akib.mycompanion.data.repository.RoomPlaylistRepository
import com.akib.mycompanion.data.repository.RoomReminderRepository
import com.akib.mycompanion.data.whatsapp.AndroidWhatsAppGateway
import com.akib.mycompanion.data.youtube.AudioStreamSource
import com.akib.mycompanion.data.youtube.NewPipeYouTube
import com.akib.mycompanion.domain.port.AlarmScheduler
import com.akib.mycompanion.domain.port.PhonebookSource
import com.akib.mycompanion.domain.port.Playback
import com.akib.mycompanion.domain.port.ReminderNotifier
import com.akib.mycompanion.domain.port.VideoInfoSource
import com.akib.mycompanion.domain.port.WhatsAppGateway
import com.akib.mycompanion.domain.repository.ContactRepository
import com.akib.mycompanion.domain.repository.PlaylistRepository
import com.akib.mycompanion.domain.repository.ReminderRepository
import com.akib.mycompanion.player.MediaControllerPlayback
import com.akib.mycompanion.presentation.shortcuts.AndroidHomeShortcuts
import com.akib.mycompanion.presentation.shortcuts.HomeShortcuts
import com.akib.mycompanion.system.DefaultReminderEvents
import com.akib.mycompanion.system.ReminderEvents
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun contactDao(db: AppDatabase): ContactDao = db.contactDao()

    @Provides
    fun reminderDao(db: AppDatabase): ReminderDao = db.reminderDao()

    @Provides
    fun playlistDao(db: AppDatabase): PlaylistDao = db.playlistDao()
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
