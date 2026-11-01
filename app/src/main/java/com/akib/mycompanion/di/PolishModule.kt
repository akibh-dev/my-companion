package com.akib.mycompanion.di

import com.akib.mycompanion.Screen
import com.akib.mycompanion.export.ExportRoute
import com.akib.mycompanion.presentation.home.HomeLink
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** Export & backup screens (NFR-11). */
@Module
@InstallIn(SingletonComponent::class)
abstract class PolishModule {
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
