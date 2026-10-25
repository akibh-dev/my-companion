package com.akib.mycompanion

import android.app.Application
import com.akib.mycompanion.core.health.HeartbeatWorker
import com.akib.mycompanion.core.host.ServiceHost
import com.akib.mycompanion.core.log.AppLog
import com.akib.mycompanion.core.settings.SettingsStore
import com.akib.mycompanion.data.notification.AndroidReminderNotifier
import com.akib.mycompanion.di.ApplicationScope
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class CompanionApp : Application() {

    @Inject lateinit var settingsStore: SettingsStore

    @Inject lateinit var serviceHost: ServiceHost

    @Inject lateinit var log: AppLog

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        AndroidReminderNotifier.ensureChannel(this)
        log.i("App", "process started")
        HeartbeatWorker.schedule(this)
        // Start the background service whenever a feature that needs it is switched on.
        scope.launch { settingsStore.settings.collect(serviceHost::sync) }
    }
}
