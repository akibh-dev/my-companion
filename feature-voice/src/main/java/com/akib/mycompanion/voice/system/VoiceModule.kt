package com.akib.mycompanion.voice.system

import com.akib.mycompanion.core.host.HostedComponent
import com.akib.mycompanion.messaging.MessageConsumer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceModule {
    @Binds @IntoSet
    abstract fun consumer(impl: VoiceConsumer): MessageConsumer

    @Binds @IntoSet
    abstract fun component(impl: VoiceComponent): HostedComponent
}
