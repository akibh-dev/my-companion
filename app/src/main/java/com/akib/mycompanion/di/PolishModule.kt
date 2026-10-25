package com.akib.mycompanion.di

import com.akib.mycompanion.core.sinks.ReminderSink
import com.akib.mycompanion.system.AppReminderSink
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Phase 5 polish: the app's reminders for other features (FR-2.16). */
@Module
@InstallIn(SingletonComponent::class)
abstract class PolishModule {
    @Binds
    abstract fun reminderSink(impl: AppReminderSink): ReminderSink
}
