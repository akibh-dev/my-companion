package com.akib.mycompanion.di

import com.akib.mycompanion.Screen
import com.akib.mycompanion.core.sinks.ReminderSink
import com.akib.mycompanion.export.ExportRoute
import com.akib.mycompanion.presentation.home.HomeLink
import com.akib.mycompanion.system.AppReminderSink
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** Phase 5 polish: the app's reminders for other features (FR-2.16) and Export & backup (NFR-11). */
@Module
@InstallIn(SingletonComponent::class)
abstract class PolishModule {
    @Binds
    abstract fun reminderSink(impl: AppReminderSink): ReminderSink

    companion object {
        const val EXPORT_ROUTE = "export"

        @Provides
        @IntoSet
        fun export() = Screen(EXPORT_ROUTE, "Export & backup") { s -> ExportRoute(s.modifier) }

        @Provides
        @IntoSet
        fun exportLink() = HomeLink(
            "Export & backup",
            "Save a copy of your data, back up and restore settings",
            EXPORT_ROUTE,
        )
    }
}
