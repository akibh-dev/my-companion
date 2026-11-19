package dev.akibh.mycompanion.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.akibh.mycompanion.data.repository.RoomPlaybackHistoryRepository
import dev.akibh.mycompanion.domain.repository.PlaybackHistoryRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class HistoryModule {
    @Binds abstract fun history(impl: RoomPlaybackHistoryRepository): PlaybackHistoryRepository
}
