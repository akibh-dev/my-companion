package dev.akibh.mycompanion.messaging

import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import dev.akibh.mycompanion.core.permissions.Requirement
import dev.akibh.mycompanion.core.settings.Feature

@Module
@InstallIn(SingletonComponent::class)
abstract class MessagingModule {
    @Multibinds abstract fun consumers(): Set<MessageConsumer>

    @Multibinds abstract fun observers(): Set<NotificationObserver>

    @Multibinds abstract fun notices(): Set<NoticeConsumer>

    companion object {
        /** Sideloaded apps must first "Allow restricted settings" (Android 13+, plan §3). */
        @Provides @IntoSet
        fun notificationAccess() = Requirement(
            id = "notification_access",
            title = "Notification access",
            why = "Auto-reply reads chat notifications and replies through them; voice mode reads notifications aloud.",
            feature = Feature.AUTO_REPLY,
            isGranted = { NotificationManagerCompat.getEnabledListenerPackages(it).contains(it.packageName) },
            settingsIntent = { Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) },
            steps = listOf(
                "If it's greyed out: open App info → ⋮ (top right) → \"Allow restricted settings\", then come back",
                "Switch on Buddy",
            ),
        )
    }
}
