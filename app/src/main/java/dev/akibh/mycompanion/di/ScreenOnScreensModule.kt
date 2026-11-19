package dev.akibh.mycompanion.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.akibh.mycompanion.Screen
import dev.akibh.mycompanion.context.screenon.ScreenOnRoute

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
