package com.akib.mycompanion.context.screenon

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** The screen state and the motion sensors keep-screen-on listens to. */
interface ScreenOnSensors {
    /** True while the screen is on; emits the current state first. */
    fun screenOn(): Flow<Boolean>

    /** Accelerometer samples with gravity; the sensors are registered only while this is collected. */
    fun motion(): Flow<MotionSample>
}

class AndroidScreenOnSensors @Inject constructor(
    @ApplicationContext private val context: Context,
) : ScreenOnSensors {

    override fun screenOn(): Flow<Boolean> = callbackFlow {
        trySend(context.getSystemService(PowerManager::class.java).isInteractive)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                trySend(intent.action == Intent.ACTION_SCREEN_ON)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()

    override fun motion(): Flow<MotionSample> = callbackFlow {
        val manager = context.getSystemService(SensorManager::class.java)
        val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer == null) {
            // No accelerometer: the phone never counts as raised.
            close()
            return@callbackFlow
        }
        // The fused gravity sensor is steadier; without it, low-pass the accelerometer instead.
        val gravitySensor = manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val listener = object : SensorEventListener {
            private var gravity: Vec3? = null

            override fun onSensorChanged(event: SensorEvent) {
                val reading = Vec3(event.values[0], event.values[1], event.values[2])
                when (event.sensor.type) {
                    Sensor.TYPE_GRAVITY -> gravity = reading
                    Sensor.TYPE_ACCELEROMETER -> {
                        if (gravitySensor == null) gravity = lowPass(gravity, reading)
                        val current = gravity ?: return
                        trySend(MotionSample(event.timestamp / NANOS_PER_MILLI, reading, current))
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        gravitySensor?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        awaitClose { manager.unregisterListener(listener) }
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        const val SMOOTHING = 0.8f

        fun lowPass(previous: Vec3?, reading: Vec3): Vec3 {
            if (previous == null) return reading
            return Vec3(
                SMOOTHING * previous.x + (1 - SMOOTHING) * reading.x,
                SMOOTHING * previous.y + (1 - SMOOTHING) * reading.y,
                SMOOTHING * previous.z + (1 - SMOOTHING) * reading.z,
            )
        }
    }
}
