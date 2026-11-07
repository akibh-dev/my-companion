package com.akib.mycompanion.di

import com.akib.mycompanion.data.repository.RoomPlaybackHistoryRepository
import com.akib.mycompanion.domain.repository.PlaybackHistoryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class HistoryModule {
    @Binds abstract fun history(impl: RoomPlaybackHistoryRepository): PlaybackHistoryRepository
}
