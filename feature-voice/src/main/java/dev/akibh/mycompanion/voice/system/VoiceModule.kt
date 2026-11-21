package dev.akibh.mycompanion.voice.system

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.akibh.mycompanion.core.host.HostedComponent
import dev.akibh.mycompanion.messaging.NoticeConsumer

@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceModule {
    @Binds @IntoSet
    abstract fun consumer(impl: VoiceConsumer): NoticeConsumer

    @Binds @IntoSet
    abstract fun component(impl: VoiceComponent): HostedComponent
}
