package com.akib.mycompanion.core.host

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.akib.mycompanion.core.R
import com.akib.mycompanion.core.log.AppLog
import com.akib.mycompanion.core.settings.Settings
import com.akib.mycompanion.core.settings.SettingsStore
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Something that has to keep running in the background while its feature is on: context rules
 * listening for charging and Bluetooth, voice mode waiting for messages. Features add theirs with
 * `@IntoSet`; [HostService] runs the wanted ones and stops itself when none are.
 */
interface HostedComponent {
    val name: String

    fun isWanted(settings: Settings): Boolean

    /** Runs until [scope] is cancelled. */
    fun start(scope: CoroutineScope)
}

/** Starts the host when a feature that needs it is on; the "not running" warning reads [running]. */
@Singleton
class ServiceHost @Inject constructor(
    @ApplicationContext private val context: Context,
    private val components: Set<@JvmSuppressWildcards HostedComponent>,
) {
    private val mutableRunning = MutableStateFlow(false)
    val running: StateFlow<Boolean> = mutableRunning.asStateFlow()

    fun isNeeded(settings: Settings): Boolean = components.any { it.isWanted(settings) }

    /** Call when settings change or after boot. Starting from the background is allowed after boot. */
    fun sync(settings: Settings) {
        if (isNeeded(settings) && !running.value) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, HostService::class.java)) }
        }
    }

    internal fun components(): Set<HostedComponent> = components

    internal fun setRunning(value: Boolean) {
        mutableRunning.value = value
    }
}

@AndroidEntryPoint
class HostService : Service() {

    @Inject lateinit var host: ServiceHost

    @Inject lateinit var settingsStore: SettingsStore

    @Inject lateinit var log: AppLog

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val running = mutableMapOf<String, Job>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        host.setRunning(true)
        log.i(TAG, "started")
        scope.launch {
            settingsStore.settings
                .map { settings -> host.components().filter { it.isWanted(settings) }.map { it.name }.toSet() }
                .distinctUntilChanged()
                .collect(::run)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun run(wanted: Set<String>) {
        (running.keys - wanted).forEach { name ->
            running.remove(name)?.cancel()
            log.i(TAG, "stopped $name")
        }
        host.components().filter { it.name in wanted && it.name !in running }.forEach { component ->
            val job = SupervisorJob(scope.coroutineContext[Job])
            component.start(CoroutineScope(scope.coroutineContext + job))
            running[component.name] = job
            log.i(TAG, "started ${component.name}")
        }
        if (wanted.isEmpty()) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        host.setRunning(false)
        log.i(TAG, "stopped")
        super.onDestroy()
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Background features", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Shown while context rules or voice mode are running"
            },
        )
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_host)
            .setContentTitle("My Companion is running")
            .setContentText("Context rules and voice mode work in the background")
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    private companion object {
        const val TAG = "ServiceHost"
        const val CHANNEL_ID = "host"
        const val NOTIFICATION_ID = 41
    }
}
