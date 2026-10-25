package com.akib.mycompanion.context.device

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.akib.mycompanion.context.domain.ContextSnapshot
import com.akib.mycompanion.core.time.Clock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/** The phone's state as rules see it, updated whenever any part changes (and every minute, for time windows). */
interface ContextSensors {
    fun snapshots(): Flow<ContextSnapshot>
}

class AndroidContextSensors @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
    private val places: PlaceDetector,
) : ContextSensors {

    override fun snapshots(): Flow<ContextSnapshot> =
        combine(minutes(), charging(), bluetooth(), wifi(), places.inside) { now, charging, devices, ssid, inside ->
            ContextSnapshot(now, charging, devices, ssid, inside)
        }.distinctUntilChanged()

    private fun minutes(): Flow<LocalDateTime> = flow {
        while (true) {
            val now = clock.nowMillis()
            emit(LocalDateTime.ofInstant(Instant.ofEpochMilli(now), clock.zone).withSecond(0).withNano(0))
            delay(MINUTE - now % MINUTE + 50)
        }
    }

    private fun charging(): Flow<Boolean> = broadcasts(
        Intent.ACTION_POWER_CONNECTED,
        Intent.ACTION_POWER_DISCONNECTED
    ) {
        context.getSystemService(BatteryManager::class.java).isCharging
    }.distinctUntilChanged()

    /** Names of connected Bluetooth devices (needs "Nearby devices"). */
    @SuppressLint("MissingPermission") // checked in hasBluetooth()
    private fun bluetooth(): Flow<Set<String>> = callbackFlow {
        val connected = mutableSetOf<String>()
        fun name(device: BluetoothDevice?) = if (hasBluetooth()) runCatching { device?.name }.getOrNull() else null
        val manager = context.getSystemService(BluetoothManager::class.java)
        trySend(emptySet())
        // Devices already connected when we start (car, headphones), through the audio profiles.
        listOf(BluetoothProfile.HEADSET, BluetoothProfile.A2DP).forEach { profile ->
            manager.adapter?.getProfileProxy(
                context,
                object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                        if (hasBluetooth()) connected += proxy.connectedDevices.mapNotNull(::name)
                        trySend(connected.toSet())
                        manager.adapter?.closeProfileProxy(p, proxy)
                    }

                    override fun onServiceDisconnected(p: Int) = Unit
                },
                profile,
            )
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                val deviceName = name(device) ?: return
                when (intent.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> connected += deviceName
                    else -> connected -= deviceName
                }
                trySend(connected.toSet())
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()

    /** The connected Wi-Fi network's name; Android only shares it with location access. */
    private fun wifi(): Flow<String?> = callbackFlow {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        trySend(null)
        val callback = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(ssidOf(caps.transportInfo as? WifiInfo))
            }

            override fun onLost(network: Network) {
                trySend(null)
            }
        }
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        connectivity.registerNetworkCallback(request, callback)
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun broadcasts(vararg actions: String, read: () -> Boolean): Flow<Boolean> = callbackFlow {
        trySend(read())
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                trySend(intent.action == Intent.ACTION_POWER_CONNECTED)
            }
        }
        val filter = IntentFilter().apply { actions.forEach(::addAction) }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitClose { context.unregisterReceiver(receiver) }
    }

    private fun hasBluetooth() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        private const val MINUTE = 60_000L

        /** WifiInfo wraps names in quotes and hides them as "<unknown ssid>" without location access. */
        fun ssidOf(info: WifiInfo?): String? =
            info?.ssid?.removeSurrounding("\"")?.takeIf { it.isNotBlank() && it != UNKNOWN_SSID }

        private const val UNKNOWN_SSID = "<unknown ssid>"
    }
}
