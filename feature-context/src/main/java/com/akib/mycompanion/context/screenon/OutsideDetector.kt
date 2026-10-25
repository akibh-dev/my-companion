package com.akib.mycompanion.context.screenon

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import com.akib.mycompanion.context.data.ContextRepository
import com.akib.mycompanion.context.device.AndroidContextSensors
import com.akib.mycompanion.context.device.PlaceDetector
import com.akib.mycompanion.context.domain.Place
import com.akib.mycompanion.core.time.Clock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** Whether you're outside (FR-3.10); collected only while the screen is on. */
interface OutsideDetector {
    fun outside(): Flow<Boolean>
}

/**
 * Inside = a saved place [PlaceDetector] reports (live while keep-screen-on or context rules run), a saved place
 * around a recent last-known location (so it also works with context rules off), or a saved
 * place's Wi-Fi network scanned or connected nearby.
 */
class AndroidOutsideDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val places: PlaceDetector,
    private val repository: ContextRepository,
    private val clock: Clock,
) : OutsideDetector {

    override fun outside(): Flow<Boolean> =
        combine(places.inside, repository.places, wifiSeen()) { inside, saved, seen ->
            isOutside(inside + placesAtLastLocation(saved), savedWifiInRange(saved, seen))
        }.distinctUntilChanged()

    private fun wifiSeen(): Flow<Set<String>> =
        combine(connectedWifi(), scannedWifi()) { connected, scanned -> scanned + listOfNotNull(connected) }
            .distinctUntilChanged()

    /** The connected network's name (needs location access, like in context rules). */
    private fun connectedWifi(): Flow<String?> = callbackFlow {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        trySend(null)
        val callback = object : ConnectivityManager.NetworkCallback(
            ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO,
        ) {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(AndroidContextSensors.ssidOf(caps.transportInfo as? WifiInfo))
            }

            override fun onLost(network: Network) {
                trySend(null)
            }
        }
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        connectivity.registerNetworkCallback(request, callback)
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    /** Names from the latest Wi-Fi scan, updated when the system finishes a new one. */
    private fun scannedWifi(): Flow<Set<String>> = callbackFlow {
        trySend(scanNames())
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                trySend(scanNames())
            }
        }
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()

    @SuppressLint("MissingPermission") // checked above
    private fun scanNames(): Set<String> {
        if (!hasLocation()) return emptySet()
        return runCatching {
            context.getSystemService(WifiManager::class.java).scanResults
                .mapNotNull { result -> result.wifiSsid?.toString()?.removeSurrounding("\"") }
                .filter { it.isNotBlank() }
                .toSet()
        }.getOrDefault(emptySet())
    }

    @SuppressLint("MissingPermission") // checked above
    private fun placesAtLastLocation(saved: List<Place>): Set<Long> {
        if (saved.isEmpty() || !hasLocation()) return emptySet()
        val manager = context.getSystemService(LocationManager::class.java)
        val last = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.takeIf { clock.nowMillis() - it.time <= FRESH_LOCATION_MILLIS }
            ?: return emptySet()
        return placesAt(saved, last.latitude, last.longitude)
    }

    private fun hasLocation() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        /** An older fix may be from before you left the place. */
        const val FRESH_LOCATION_MILLIS = 10 * 60 * 1000L
    }
}
