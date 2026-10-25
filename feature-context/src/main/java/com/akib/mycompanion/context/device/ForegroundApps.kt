package com.akib.mycompanion.context.device

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.os.Process
import com.akib.mycompanion.core.time.Clock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Which app is open on the screen, for the "App in use" trigger (FR-3.7).
 *
 * Android has no event for "the foreground app changed" that a normal app may listen to. The usage
 * statistics log (special access "Usage access") records every time an app comes to the front, so
 * [current] reads the newest entries every few seconds. Rules start it only while an enabled rule
 * uses the trigger, so it costs nothing otherwise.
 */
interface ForegroundApps {
    fun hasAccess(): Boolean

    /** Package of the app in front; null without usage access or while the screen is off. */
    fun current(): Flow<String?>
}

class UsageStatsForegroundApps @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) : ForegroundApps {

    override fun hasAccess(): Boolean = hasUsageAccess(context)

    override fun current(): Flow<String?> = flow {
        var app: String? = null
        var since = clock.nowMillis() - LOOK_BACK_MILLIS
        while (true) {
            val now = clock.nowMillis()
            if (!hasAccess() || !interactive()) {
                // Start over with a full look-back when the screen comes on again.
                app = null
                since = now - LOOK_BACK_MILLIS
            } else {
                app = ForegroundEvents.latest(read(since, now), app)
                since = now - OVERLAP_MILLIS
            }
            emit(app)
            delay(POLL_MILLIS)
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    private fun interactive() = context.getSystemService(PowerManager::class.java)?.isInteractive != false

    private fun read(from: Long, to: Long): List<ForegroundEvents.Event> {
        val usage = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val events = runCatching { usage.queryEvents(from, to) }.getOrNull() ?: return emptyList()
        val out = mutableListOf<ForegroundEvents.Event>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            out += ForegroundEvents.Event(event.timeStamp, event.eventType, event.packageName)
        }
        return out
    }

    companion object {
        const val POLL_MILLIS = 3_000L

        /** How far back the first read looks for the app that is already open. */
        const val LOOK_BACK_MILLIS = 15 * 60_000L

        /** Each read overlaps the last a little so an event on the boundary isn't missed. */
        private const val OVERLAP_MILLIS = 1_000L
    }
}

/** Picks the app in front from the usage log. Pure, so it's tested without a phone. */
object ForegroundEvents {
    data class Event(val time: Long, val type: Int, val packageName: String)

    /** UsageEvents.Event.ACTIVITY_RESUMED (also the older MOVE_TO_FOREGROUND, same value 1). */
    const val RESUMED = UsageEvents.Event.ACTIVITY_RESUMED

    /**
     * The app whose activity came to the front last. With nothing new in [events], the app that was
     * in front before ([previous]) still is.
     */
    fun latest(events: List<Event>, previous: String?): String? =
        events.filter { it.type == RESUMED && it.packageName.isNotBlank() }
            .maxByOrNull { it.time }
            ?.packageName
            ?: previous
}

/** "Usage access" is a special access: an app-op switched in settings, not a runtime permission. */
fun hasUsageAccess(context: Context): Boolean {
    val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
    return when (ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)) {
        AppOpsManager.MODE_ALLOWED -> true
        AppOpsManager.MODE_DEFAULT ->
            context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        else -> false
    }
}

/** An app the user can pick in the rule editor. */
data class InstalledApp(val packageName: String, val label: String)

/** Apps with a launcher icon, by name. Needs the launcher `<queries>` entry in the manifest. */
fun launchableApps(context: Context): List<InstalledApp> {
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val pm = context.packageManager
    return pm.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0))
        .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .filter { it.packageName != context.packageName }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}
