package dev.akibh.mycompanion.core.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import dev.akibh.mycompanion.core.host.HostedComponent
import dev.akibh.mycompanion.core.permissions.Requirement
import dev.akibh.mycompanion.core.settings.DataStoreSettingsStore
import dev.akibh.mycompanion.core.settings.SettingsStore
import dev.akibh.mycompanion.core.time.Clock
import dev.akibh.mycompanion.core.time.SystemClock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CoreModule {
    @Binds
    @Singleton
    abstract fun settings(impl: DataStoreSettingsStore): SettingsStore

    @Binds
    abstract fun clock(impl: SystemClock): Clock

    /** Empty until features add theirs. */
    @Multibinds
    abstract fun hostedComponents(): Set<HostedComponent>

    @Multibinds
    abstract fun requirements(): Set<Requirement>
}
