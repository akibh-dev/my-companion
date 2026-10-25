package com.akib.mycompanion.core.di

import com.akib.mycompanion.core.host.HostedComponent
import com.akib.mycompanion.core.permissions.Requirement
import com.akib.mycompanion.core.settings.DataStoreSettingsStore
import com.akib.mycompanion.core.settings.SettingsStore
import com.akib.mycompanion.core.time.Clock
import com.akib.mycompanion.core.time.SystemClock
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
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
