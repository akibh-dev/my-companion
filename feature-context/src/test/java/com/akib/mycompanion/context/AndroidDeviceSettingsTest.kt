package com.akib.mycompanion.context

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.media.AudioManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.context.device.AndroidDeviceSettings
import com.akib.mycompanion.context.device.WriteResult
import com.akib.mycompanion.context.di.ContextModule
import com.akib.mycompanion.context.domain.RuleAction
import com.akib.mycompanion.core.settings.Feature
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class AndroidDeviceSettingsTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val notifications = app.getSystemService(NotificationManager::class.java)
    private val audio = app.getSystemService(AudioManager::class.java)
    private val device = AndroidDeviceSettings(app)

    @Test
    fun `sound mode is read and changed`() {
        assertThat(device.write(RuleAction.KEY_RINGER, "VIBRATE")).isEqualTo(WriteResult.Applied)
        assertThat(device.read(RuleAction.KEY_RINGER)).isEqualTo("VIBRATE")
        assertThat(audio.ringerMode).isEqualTo(AudioManager.RINGER_MODE_VIBRATE)

        assertThat(device.write(RuleAction.KEY_RINGER, "NORMAL")).isEqualTo(WriteResult.Applied)
        assertThat(device.read(RuleAction.KEY_RINGER)).isEqualTo("NORMAL")
    }

    @Test
    fun `silent mode needs Do Not Disturb access`() {
        shadowOf(notifications).setNotificationPolicyAccessGranted(false)
        assertThat(device.write(RuleAction.KEY_RINGER, "SILENT"))
            .isEqualTo(WriteResult.NotAllowed("Needs Do Not Disturb access"))

        shadowOf(notifications).setNotificationPolicyAccessGranted(true)
        assertThat(device.write(RuleAction.KEY_RINGER, "SILENT")).isEqualTo(WriteResult.Applied)
        assertThat(device.read(RuleAction.KEY_RINGER)).isEqualTo("SILENT")
    }

    @Test
    fun `do not disturb is changed only with access`() {
        assertThat(device.read(RuleAction.KEY_DND)).isEqualTo("OFF")
        shadowOf(notifications).setNotificationPolicyAccessGranted(false)
        assertThat(device.write(RuleAction.KEY_DND, "PRIORITY"))
            .isEqualTo(WriteResult.NotAllowed("Needs Do Not Disturb access"))

        shadowOf(notifications).setNotificationPolicyAccessGranted(true)
        listOf("PRIORITY", "ALARMS", "TOTAL_SILENCE", "OFF").forEach { mode ->
            assertThat(device.write(RuleAction.KEY_DND, mode)).isEqualTo(WriteResult.Applied)
            assertThat(device.read(RuleAction.KEY_DND)).isEqualTo(mode)
        }
    }

    @Test
    fun `volume is set and read in percent of the stream's maximum`() {
        assertThat(device.write("volume.MEDIA", "100")).isEqualTo(WriteResult.Applied)
        assertThat(device.read("volume.MEDIA")).isEqualTo("100")
        assertThat(audio.getStreamVolume(AudioManager.STREAM_MUSIC))
            .isEqualTo(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC))

        assertThat(device.write("volume.ALARM", "0")).isEqualTo(WriteResult.Applied)
        assertThat(device.read("volume.ALARM")).isEqualTo("0")
    }

    @Test
    fun `brightness is read in percent`() {
        Settings.System.putInt(app.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 255)
        assertThat(device.read(RuleAction.KEY_BRIGHTNESS)).isEqualTo("100")

        Settings.System.putInt(app.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 51)
        assertThat(device.read(RuleAction.KEY_BRIGHTNESS)).isEqualTo("20")
    }

    @Test
    fun `wi-fi can't be switched by apps, so you're asked with a notification`() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertThat(device.write(RuleAction.KEY_WIFI, "true")).isEqualTo(WriteResult.NotAllowed("Notifications are off"))

        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertThat(device.write(RuleAction.KEY_WIFI, "false")).isEqualTo(WriteResult.AskedUser)
        assertThat(device.write(RuleAction.KEY_BLUETOOTH, "true")).isEqualTo(WriteResult.AskedUser)

        val posted = shadowOf(notifications).allNotifications
        assertThat(posted).hasSize(2)
        val titles = posted.map { it.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString() }
        assertThat(titles).containsExactly("Turn Wi-Fi off?", "Turn Bluetooth on?")
    }

    @Test
    fun `bad keys and values are refused, never thrown`() {
        assertThat(device.write("teleport", "on")).isEqualTo(WriteResult.NotAllowed("Unknown setting teleport"))
        assertThat(device.write(RuleAction.KEY_RINGER, "LOUD")).isInstanceOf(WriteResult.NotAllowed::class.java)
        assertThat(device.write("volume.SPACE", "50")).isInstanceOf(WriteResult.NotAllowed::class.java)
        assertThat(device.read("teleport")).isNull()
        assertThat(device.read("volume.SPACE")).isNull()
    }

    @Test
    fun `context rules list what they need, with where to switch it on`() {
        val requirements = listOf(
            ContextModule.dndAccess(),
            ContextModule.location(),
            ContextModule.bluetooth(),
            ContextModule.writeSettings(),
        )

        assertThat(requirements.map { it.id })
            .containsExactly("dnd_access", "location", "bluetooth_connect", "write_settings")
        requirements.forEach { assertThat(it.feature).isEqualTo(Feature.CONTEXT_RULES) }
        assertThat(ContextModule.dndAccess().settingsIntent(app).action)
            .isEqualTo(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        assertThat(ContextModule.writeSettings().settingsIntent(app).action)
            .isEqualTo(Settings.ACTION_MANAGE_WRITE_SETTINGS)
    }

    @Test
    fun `do not disturb access is read from the system`() {
        val dnd = ContextModule.dndAccess()

        shadowOf(notifications).setNotificationPolicyAccessGranted(false)
        assertThat(dnd.isGranted(app)).isFalse()

        shadowOf(notifications).setNotificationPolicyAccessGranted(true)
        assertThat(dnd.isGranted(app)).isTrue()
    }

    @Test
    fun `location counts only with background access too`() {
        val location = ContextModule.location()

        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        assertThat(location.isGranted(app)).isFalse()

        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        assertThat(location.isGranted(app)).isTrue()
    }
}
