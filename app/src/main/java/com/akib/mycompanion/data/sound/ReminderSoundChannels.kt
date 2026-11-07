package com.akib.mycompanion.data.sound

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import com.akib.mycompanion.data.local.ReminderSoundDao
import com.akib.mycompanion.domain.model.ReminderSound
import com.akib.mycompanion.domain.model.SoundPreset
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Resolves the synchronous notification port without ever accessing Room on Android's main thread. */
@Singleton
class ReminderSoundChannels @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ReminderSoundDao? = null,
) {
    data class Selection(val id: String, val name: String, val uri: Uri?)

    fun channel(sound: ReminderSound): String {
        val selection = runCatching {
            runBlocking(Dispatchers.IO) { withTimeoutOrNull(2_000) { resolve(sound) } }
        }.getOrNull() ?: default()
        val manager = context.getSystemService(NotificationManager::class.java)
        // Never mutate an existing channel: Android/user settings are authoritative.
        if (manager.getNotificationChannel(selection.id) == null) {
            val previous = if (selection.id.startsWith("reminders_imported_")) {
                val prefix = selection.id.substringBeforeLast('_') + "_"
                manager.notificationChannels.filter { it.id.startsWith(prefix) }.minByOrNull { it.importance }
            } else {
                null
            }
            manager.createNotificationChannel(
                NotificationChannel(
                    selection.id,
                    selection.name,
                    previous?.importance ?: NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Custom notifications you create in My Companion"
                    val overridden = previous != null && (
                        previous.sound == null ||
                            uriFingerprint(previous.sound.toString()) != previous.id.substringAfterLast('_')
                        )
                    setSound(if (overridden) previous?.sound else selection.uri, ATTRIBUTES)
                    if (previous != null) {
                        enableVibration(previous.shouldVibrate())
                        vibrationPattern = previous.vibrationPattern
                        setShowBadge(previous.canShowBadge())
                    }
                },
            )
        }
        return selection.id
    }

    suspend fun resolve(sound: ReminderSound): Selection = withContext(Dispatchers.IO) {
        when (sound) {
            ReminderSound.Default -> default()
            ReminderSound.Silent -> Selection("reminders_silent", "Reminders — silent", null)
            is ReminderSound.Preset -> {
                val preset = SoundPreset.entries.firstOrNull { it.id == sound.id }
                if (preset == null) {
                    default()
                } else {
                    Selection(
                        "reminders_preset_${preset.id}_v1",
                        "Reminders — ${preset.title}",
                        Uri.parse("android.resource://${context.packageName}/raw/reminder_${preset.id}"),
                    )
                }
            }
            is ReminderSound.Imported -> {
                val record = dao?.find(sound.id)
                if (record == null || !isReadable(context, Uri.parse(record.uri))) {
                    default()
                } else {
                    Selection(
                        importedChannelId(record.id, record.uri),
                        "Reminders — ${record.name}",
                        Uri.parse(record.uri),
                    )
                }
            }
        }
    }

    private fun default() = Selection(
        DEFAULT_CHANNEL,
        "Reminders",
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    )

    companion object {
        const val DEFAULT_CHANNEL = "reminders"
        val ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(
                AudioAttributes.USAGE_NOTIFICATION
            ).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()

        fun importedChannelId(id: String) = "reminders_imported_${id}_"
        fun importedChannelId(id: String, uri: String) = importedChannelId(id) + uriFingerprint(uri)
        private fun uriFingerprint(uri: String) = MessageDigest.getInstance("SHA-256")
            .digest(uri.toByteArray()).take(8).joinToString("") { "%02x".format(it) }

        fun isReadable(context: Context, uri: Uri): Boolean = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
        }.getOrDefault(false)
    }
}
