package com.akib.mycompanion.context.device

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.akib.mycompanion.context.R
import com.akib.mycompanion.context.domain.DndMode
import com.akib.mycompanion.context.domain.RuleAction
import com.akib.mycompanion.context.domain.SoundMode
import com.akib.mycompanion.context.domain.VolumeStream
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.math.roundToInt

sealed interface WriteResult {
    data object Applied : WriteResult

    /** Android doesn't let apps change it: a notification asks the user to tap (D2). */
    data object AskedUser : WriteResult

    data class NotAllowed(val reason: String) : WriteResult
}

/** The device settings rules can change, by [RuleAction] key. */
interface DeviceSettings {
    fun read(key: String): String?

    fun write(key: String, value: String): WriteResult
}

class AndroidDeviceSettings @Inject constructor(@ApplicationContext private val context: Context) : DeviceSettings {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)

    override fun read(key: String): String? = runCatching {
        when {
            key == RuleAction.KEY_RINGER -> when (audio.ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> SoundMode.SILENT
                AudioManager.RINGER_MODE_VIBRATE -> SoundMode.VIBRATE
                else -> SoundMode.NORMAL
            }.name
            key == RuleAction.KEY_DND -> when (notifications.currentInterruptionFilter) {
                NotificationManager.INTERRUPTION_FILTER_PRIORITY -> DndMode.PRIORITY
                NotificationManager.INTERRUPTION_FILTER_ALARMS -> DndMode.ALARMS
                NotificationManager.INTERRUPTION_FILTER_NONE -> DndMode.TOTAL_SILENCE
                else -> DndMode.OFF
            }.name
            key.startsWith(VOLUME) -> {
                val stream = stream(VolumeStream.valueOf(key.removePrefix(VOLUME)))
                (audio.getStreamVolume(stream) * 100f / audio.getStreamMaxVolume(stream)).roundToInt().toString()
            }
            key == RuleAction.KEY_WIFI -> context.getSystemService(WifiManager::class.java).isWifiEnabled.toString()
            key == RuleAction.KEY_BLUETOOTH ->
                (context.getSystemService(BluetoothManager::class.java).adapter?.isEnabled ?: false).toString()
            key == RuleAction.KEY_BRIGHTNESS -> {
                val level = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                (level * 100f / MAX_BRIGHTNESS).roundToInt().toString()
            }
            else -> null
        }
    }.getOrNull()

    override fun write(key: String, value: String): WriteResult = try {
        when {
            key == RuleAction.KEY_RINGER -> {
                val mode = SoundMode.valueOf(value)
                if (mode == SoundMode.SILENT && !notifications.isNotificationPolicyAccessGranted) {
                    WriteResult.NotAllowed("Needs Do Not Disturb access")
                } else {
                    audio.ringerMode = when (mode) {
                        SoundMode.NORMAL -> AudioManager.RINGER_MODE_NORMAL
                        SoundMode.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
                        SoundMode.SILENT -> AudioManager.RINGER_MODE_SILENT
                    }
                    WriteResult.Applied
                }
            }
            key == RuleAction.KEY_DND -> {
                if (!notifications.isNotificationPolicyAccessGranted) {
                    WriteResult.NotAllowed("Needs Do Not Disturb access")
                } else {
                    notifications.setInterruptionFilter(
                        when (DndMode.valueOf(value)) {
                            DndMode.OFF -> NotificationManager.INTERRUPTION_FILTER_ALL
                            DndMode.PRIORITY -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
                            DndMode.ALARMS -> NotificationManager.INTERRUPTION_FILTER_ALARMS
                            DndMode.TOTAL_SILENCE -> NotificationManager.INTERRUPTION_FILTER_NONE
                        },
                    )
                    WriteResult.Applied
                }
            }
            key.startsWith(VOLUME) -> {
                val stream = stream(VolumeStream.valueOf(key.removePrefix(VOLUME)))
                audio.setStreamVolume(stream, (value.toInt() / 100f * audio.getStreamMaxVolume(stream)).roundToInt(), 0)
                WriteResult.Applied
            }
            key == RuleAction.KEY_WIFI -> askUser(
                id = WIFI_PROMPT,
                title = if (value.toBoolean()) "Turn Wi-Fi on?" else "Turn Wi-Fi off?",
                intent = Intent(Settings.Panel.ACTION_WIFI),
            )
            key == RuleAction.KEY_BLUETOOTH -> askUser(
                id = BLUETOOTH_PROMPT,
                title = if (value.toBoolean()) "Turn Bluetooth on?" else "Turn Bluetooth off?",
                intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
            )
            key == RuleAction.KEY_BRIGHTNESS -> {
                if (!Settings.System.canWrite(context)) {
                    WriteResult.NotAllowed("Needs permission to change system settings")
                } else {
                    val resolver = context.contentResolver
                    Settings.System.putInt(
                        resolver,
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                    )
                    Settings.System.putInt(
                        resolver,
                        Settings.System.SCREEN_BRIGHTNESS,
                        (value.toInt() / 100f * MAX_BRIGHTNESS).roundToInt()
                    )
                    WriteResult.Applied
                }
            }
            else -> WriteResult.NotAllowed("Unknown setting $key")
        }
    } catch (e: SecurityException) {
        WriteResult.NotAllowed(e.message ?: "Not allowed")
    } catch (e: IllegalArgumentException) {
        WriteResult.NotAllowed(e.message ?: "Bad value")
    }

    @SuppressLint("MissingPermission") // checked first; lint doesn't follow the early return
    private fun askUser(id: Int, title: String, intent: Intent): WriteResult {
        val permission = Manifest.permission.POST_NOTIFICATIONS
        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
            return WriteResult.NotAllowed("Notifications are off")
        }
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, "Rule prompts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Settings a rule wants changed but Android only lets you change"
            },
        )
        val tap = PendingIntent.getActivity(
            context,
            id,
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_rule)
            .setContentTitle(title)
            .setContentText("A context rule wants this. Tap to change it.")
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
        return WriteResult.AskedUser
    }

    private fun stream(stream: VolumeStream) = when (stream) {
        VolumeStream.MEDIA -> AudioManager.STREAM_MUSIC
        VolumeStream.RING -> AudioManager.STREAM_RING
        VolumeStream.NOTIFICATION -> AudioManager.STREAM_NOTIFICATION
        VolumeStream.ALARM -> AudioManager.STREAM_ALARM
    }

    private companion object {
        const val VOLUME = "volume."
        const val MAX_BRIGHTNESS = 255
        const val CHANNEL = "rule_prompts"
        const val WIFI_PROMPT = 3101
        const val BLUETOOTH_PROMPT = 3102
    }
}
