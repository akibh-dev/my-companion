package com.akib.mycompanion.di

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.akib.mycompanion.Screen
import com.akib.mycompanion.core.permissions.Requirement
import com.akib.mycompanion.presentation.home.HomeLink
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds

/** The app shell's own requirements, and empty sets the feature screens are added to. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ShellModule {
    @Multibinds abstract fun screens(): Set<Screen>

    @Multibinds abstract fun homeLinks(): Set<HomeLink>

    companion object {
        @Provides
        @IntoSet
        fun notifications() = Requirement(
            id = "post_notifications",
            title = "Notifications",
            why = "Reminders, auto-reply status and the background-features notice are notifications.",
            feature = null,
            isGranted = {
                ContextCompat.checkSelfPermission(it, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            },
            settingsIntent = {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, it.packageName)
            },
        )

        @Provides
        @IntoSet
        fun battery() = Requirement(
            id = "battery",
            title = "Unrestricted battery use",
            why = "Without it, Android or the phone maker may stop rules, voice mode and auto-reply in the background.",
            feature = null,
            isGranted = {
                it.getSystemService(
                    PowerManager::class.java
                ).isIgnoringBatteryOptimizations(it.packageName)
            },
            settingsIntent = { Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) },
            steps = listOf("Find My Companion in the list", "Choose \"Don't optimise\" (or \"Unrestricted\")"),
        )
    }
}
