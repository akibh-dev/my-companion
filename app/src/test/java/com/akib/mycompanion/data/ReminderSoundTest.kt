package com.akib.mycompanion.data

import android.app.Application
import android.app.NotificationManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.data.local.ReminderSoundDao
import com.akib.mycompanion.data.local.ReminderSoundEntity
import com.akib.mycompanion.data.sound.ReminderSoundChannels
import com.akib.mycompanion.domain.model.ReminderSound
import com.akib.mycompanion.domain.model.SoundPreset
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderSoundTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val channels = ReminderSoundChannels(context)

    @Test fun `sound identities roundtrip and malformed imports fail safely`() {
        val options = listOf(
            ReminderSound.Default,
            ReminderSound.Silent,
            ReminderSound.Preset("bell"),
            ReminderSound.Imported("a".repeat(64))
        )
        options.forEach { assertThat(ReminderSound.decode(it.encode())).isEqualTo(it) }
        listOf("bad", "PRESET:unknown", "IMPORTED:", "IMPORTED:content://other", "IMPORTED:abc").forEach {
            assertThat(ReminderSound.decode(it)).isEqualTo(ReminderSound.Default)
        }
    }

    @Test fun `silent channel has no sound and presets have separate stable channels`() {
        assertThat(manager.getNotificationChannel(channels.channel(ReminderSound.Silent)).sound).isNull()
        val ids = SoundPreset.entries.map {
            val selection = ReminderSound.Preset(it.id)
            val id = channels.channel(selection)
            assertThat(channels.channel(selection)).isEqualTo(id)
            assertThat(manager.getNotificationChannel(id).sound)
                .isEqualTo(Uri.parse("android.resource://${context.packageName}/raw/reminder_${it.id}"))
            id
        }
        assertThat(ids).containsNoDuplicates()
    }

    @Test fun `unknown or missing sound uses the unchanged default channel`() {
        val id = channels.channel(ReminderSound.Default)
        val existing = manager.getNotificationChannel(id)
        existing.setSound(null, null)
        manager.deleteNotificationChannel(id)
        manager.createNotificationChannel(existing)
        assertThat(channels.channel(ReminderSound.Preset("unknown"))).isEqualTo(id)
        assertThat(channels.channel(ReminderSound.Imported("a".repeat(64)))).isEqualTo(id)
        assertThat(manager.getNotificationChannel(id).sound).isNull()
    }

    @Test fun `reimporting same bytes with repaired URI preserves muted channel preference`() {
        val id = "b".repeat(64)
        val source = File(context.cacheDir, "sound-source").apply { writeBytes(byteArrayOf(1, 2)) }
        val replacement = File(context.cacheDir, "sound-replacement").apply { writeBytes(byteArrayOf(3, 4)) }
        val dao = mockk<ReminderSoundDao>()
        val record = ReminderSoundEntity(id, "My sound", Uri.fromFile(source).toString(), "audio/wav", 200, 2)
        coEvery { dao.find(id) } returns record
        val resolved = ReminderSoundChannels(context, dao)
        val first = resolved.channel(ReminderSound.Imported(id))
        val old = manager.getNotificationChannel(first)
        // Simulate a user silencing a channel in system settings.
        old.setSound(null, null)
        manager.deleteNotificationChannel(first)
        manager.createNotificationChannel(old)
        coEvery { dao.find(id) } returns record.copy(uri = Uri.fromFile(replacement).toString())
        val repaired = resolved.channel(ReminderSound.Imported(id))
        assertThat(repaired).isNotEqualTo(first)
        assertThat(manager.getNotificationChannel(repaired).sound).isNull()
        assertThat(resolved.channel(ReminderSound.Imported(id))).isEqualTo(repaired)
    }
}
