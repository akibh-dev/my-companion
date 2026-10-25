package com.akib.mycompanion.di

import com.akib.mycompanion.Screen
import com.akib.mycompanion.context.screenon.ScreenOnRoute
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Keep the screen on outdoors (FR-3.5, FR-3.10). Opened from its feature card on Home, which
 * `featureRoute(Feature.KEEP_SCREEN_ON)` already points at "screen-on", so no extra Home link.
 */
@Module
@InstallIn(SingletonComponent::class)
object ScreenOnScreensModule {
    @Provides @IntoSet
    fun screenOn() = Screen("screen-on", "Keep screen on") { s -> ScreenOnRoute(s.modifier) }
}
