package com.akib.mycompanion.messaging

import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.akib.mycompanion.core.permissions.Requirement
import com.akib.mycompanion.core.settings.Feature
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds

@Module
@InstallIn(SingletonComponent::class)
abstract class MessagingModule {
    @Multibinds abstract fun consumers(): Set<MessageConsumer>

    companion object {
        /** Sideloaded apps must first "Allow restricted settings" (Android 13+, plan §3). */
        @Provides @IntoSet
        fun notificationAccess() = Requirement(
            id = "notification_access",
            title = "Notification access",
            why = "Auto-reply and voice mode read chat notifications and reply through them.",
            feature = Feature.AUTO_REPLY,
            isGranted = { NotificationManagerCompat.getEnabledListenerPackages(it).contains(it.packageName) },
            settingsIntent = { Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) },
            steps = listOf(
                "If it's greyed out: open App info → ⋮ (top right) → \"Allow restricted settings\", then come back",
                "Switch on My Companion",
            ),
        )
    }
}
