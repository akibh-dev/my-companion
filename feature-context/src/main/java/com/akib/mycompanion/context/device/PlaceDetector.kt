package com.akib.mycompanion.context.device

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import com.akib.mycompanion.context.data.ContextRepository
import com.akib.mycompanion.context.domain.Place
import com.akib.mycompanion.core.log.AppLog
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Which saved places you're at (FR-3.1, FR-3.10). Uses Android's own proximity alerts (GPS and
 * network, no Google account) plus the place's saved Wi-Fi names, so arrival is noticed even with
 * location off. Decision D1 stays open until spike S7 compares methods; this class is the seam.
 */
@Singleton
class PlaceDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ContextRepository,
    private val log: AppLog,
) {
    private val byLocation = MutableStateFlow<Set<Long>>(emptySet())
    private val byWifi = MutableStateFlow<Set<Long>>(emptySet())
    private val mutableInside = MutableStateFlow<Set<Long>>(emptySet())
    val inside: StateFlow<Set<Long>> = mutableInside.asStateFlow()
    private var watched: List<Place> = emptyList()

    /** Scopes of the features using the detector; the watcher runs in the first. */
    private val users = mutableListOf<CoroutineScope>()
    private var watcher: Job? = null

    /** Bumped for each new watcher, so a watcher that was handed over doesn't undo its successor. */
    private var generation = 0

    /**
     * Watches places for as long as [scope] lives (context rules, keep-screen-on). Shared: the
     * alerts are registered once however many features use it, survive one of them stopping, and
     * are removed only when the last one stops.
     */
    fun start(scope: CoroutineScope) {
        synchronized(users) {
            users += scope
            if (watcher?.isActive != true) watcher = launchWatcher(scope)
        }
        scope.launch {
            try {
                awaitCancellation()
            } finally {
                leave(scope)
            }
        }
    }

    /** How many features are using the detector now (for tests and the log). */
    val userCount: Int get() = synchronized(users) { users.size }

    private fun leave(scope: CoroutineScope) {
        synchronized(users) {
            users -= scope
            // The watcher lived in the scope that just ended: hand it to a feature still running.
            val next = users.firstOrNull { it.isActive }
            if (watcher?.isActive != true && next != null) watcher = launchWatcher(next)
        }
    }

    private fun launchWatcher(scope: CoroutineScope): Job {
        val mine = ++generation
        return scope.launch { watch(mine) }
    }

    /** Re-registers alerts whenever places change; cleans up when the feature hosting it stops. */
    private suspend fun watch(mine: Int) {
        try {
            repository.places.collect { places ->
                unregister()
                watched = places
                register(places)
                checkWifi()
            }
        } finally {
            val current = synchronized(users) { mine == generation }
            if (current) {
                unregister()
                watched = emptyList()
                if (userCount == 0) {
                    byLocation.value = emptySet()
                    byWifi.value = emptySet()
                    publish()
                }
            }
        }
    }

    internal fun onProximity(placeId: Long, entering: Boolean) {
        log.i(TAG, "${if (entering) "arrived at" else "left"} place $placeId")
        byLocation.update { if (entering) it + placeId else it - placeId }
        publish()
    }

    /** Saved Wi-Fi names in range (from the last scan; Android throttles new scans). */
    @SuppressLint("MissingPermission") // checked by hasLocation()
    fun checkWifi() {
        if (!hasLocation()) return
        val seen = runCatching {
            context.getSystemService(
                WifiManager::class.java
            ).scanResults.map { it.wifiSsid.toString().removeSurrounding("\"") }
        }
            .getOrDefault(emptyList()).toSet()
        byWifi.value = watched.filter { place -> place.wifiNames.any { it in seen } }.map { it.id }.toSet()
        publish()
    }

    private fun publish() {
        mutableInside.value = byLocation.value + byWifi.value
    }

    @SuppressLint("MissingPermission") // checked by hasLocation()
    private fun register(places: List<Place>) {
        if (!hasLocation()) {
            log.w(TAG, "no location access: places are detected by Wi-Fi only")
            return
        }
        val manager = context.getSystemService(LocationManager::class.java)
        val last = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        byLocation.value = places
            .filter { last != null && it.contains(last.latitude, last.longitude) }
            .map { it.id }
            .toSet()
        places.forEach { place ->
            manager.addProximityAlert(
                place.latitude,
                place.longitude,
                place.radiusMeters.toFloat(),
                NO_EXPIRY,
                pendingIntent(place.id)
            )
        }
        publish()
    }

    private fun unregister() {
        val manager = context.getSystemService(LocationManager::class.java)
        watched.forEach { runCatching { manager.removeProximityAlert(pendingIntent(it.id)) } }
    }

    // Mutable: the system adds KEY_PROXIMITY_ENTERING to the intent.
    private fun pendingIntent(placeId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        placeId.toInt(),
        Intent(context, ProximityReceiver::class.java).putExtra(EXTRA_PLACE, placeId),
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun hasLocation() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "Places"
        private const val NO_EXPIRY = -1L
        const val EXTRA_PLACE = "place_id"
    }
}

/** Receives Android's proximity alerts for saved places. */
@AndroidEntryPoint
class ProximityReceiver : BroadcastReceiver() {
    @Inject lateinit var detector: PlaceDetector

    override fun onReceive(context: Context, intent: Intent) {
        val placeId = intent.getLongExtra(PlaceDetector.EXTRA_PLACE, -1)
        if (placeId < 0) return
        detector.onProximity(placeId, intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, false))
    }
}
