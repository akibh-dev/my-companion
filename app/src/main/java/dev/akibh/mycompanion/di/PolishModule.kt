package dev.akibh.mycompanion.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.akibh.mycompanion.Screen
import dev.akibh.mycompanion.export.ExportRoute
import dev.akibh.mycompanion.presentation.home.HomeLink

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
