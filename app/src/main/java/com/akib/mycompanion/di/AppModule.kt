package com.akib.mycompanion.di

import android.content.Context
import androidx.room.Room
import com.akib.mycompanion.data.alarm.AndroidAlarmScheduler
import com.akib.mycompanion.data.local.AppDatabase
import com.akib.mycompanion.data.local.ContactDao
import com.akib.mycompanion.data.local.ReminderDao
import com.akib.mycompanion.data.notification.AndroidReminderNotifier
import com.akib.mycompanion.data.phonebook.AndroidPhonebookSource
import com.akib.mycompanion.data.repository.RoomContactRepository
import com.akib.mycompanion.data.repository.RoomReminderRepository
import com.akib.mycompanion.data.time.SystemClock
import com.akib.mycompanion.data.whatsapp.AndroidWhatsAppGateway
import com.akib.mycompanion.domain.port.AlarmScheduler
import com.akib.mycompanion.domain.port.PhonebookSource
import com.akib.mycompanion.domain.port.ReminderNotifier
import com.akib.mycompanion.domain.port.WhatsAppGateway
import com.akib.mycompanion.domain.repository.ContactRepository
import com.akib.mycompanion.domain.repository.ReminderRepository
import com.akib.mycompanion.domain.time.Clock
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
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

    @Provides
    fun contactDao(db: AppDatabase): ContactDao = db.contactDao()

    @Provides
    fun reminderDao(db: AppDatabase): ReminderDao = db.reminderDao()
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
    abstract fun clock(impl: SystemClock): Clock
}

/** Kept on its own so receiver tests can replace it with @UninstallModules. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderEventsModule {
    @Binds
    abstract fun events(impl: DefaultReminderEvents): ReminderEvents
}
