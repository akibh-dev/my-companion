package dev.akibh.mycompanion

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.akibh.mycompanion.core.health.HeartbeatWorker
import dev.akibh.mycompanion.core.host.ServiceHost
import dev.akibh.mycompanion.core.log.AppLog
import dev.akibh.mycompanion.core.settings.SettingsStore
import dev.akibh.mycompanion.data.notification.AndroidReminderNotifier
import dev.akibh.mycompanion.di.ApplicationScope
import dev.akibh.mycompanion.quran.data.QuranSummaries
import dev.akibh.mycompanion.widget.QuickActionsWidget
import dev.akibh.mycompanion.widget.QuranWidget
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class CompanionApp : Application() {

    @Inject lateinit var settingsStore: SettingsStore

    @Inject lateinit var serviceHost: ServiceHost

    @Inject lateinit var log: AppLog

    @Inject lateinit var quranSummaries: QuranSummaries

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
        // Keep the home-screen widget's Pause / Voice buttons in step with the settings.
        scope.launch { settingsStore.settings.collect { QuickActionsWidget.refresh(this@CompanionApp, it) } }
        // And the Quran widget with the place read and today's khatm pages.
        scope.launch { quranSummaries.summary.collect { QuranWidget.refresh(this@CompanionApp, it) } }
    }
}
