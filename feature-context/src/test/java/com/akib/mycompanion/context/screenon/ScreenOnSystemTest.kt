package com.akib.mycompanion.context.screenon

import android.app.Application
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.wifi.WifiInfo
import android.os.Looper
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.akib.mycompanion.context.data.ContextRepository
import com.akib.mycompanion.context.device.AndroidContextSensors
import com.akib.mycompanion.context.device.PlaceDetector
import com.akib.mycompanion.context.domain.Place
import com.akib.mycompanion.core.log.AppLog
import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.core.time.Clock
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import java.nio.file.Files
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.SensorEventBuilder
import org.robolectric.shadows.ShadowSensor
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowWifiInfo

@RunWith(AndroidJUnit4::class)
class ScreenOnSystemTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val log = AppLog(Files.createTempDirectory("log").toFile(), mirrorToLogcat = false)

    // ---- Overlay permission and the keep-awake window ----

    @Test
    fun `display over other apps is read from the system and opened for this app`() {
        ShadowSettings.setCanDrawOverlays(false)
        assertThat(OverlayPermission.granted(app)).isFalse()

        ShadowSettings.setCanDrawOverlays(true)
        assertThat(OverlayPermission.granted(app)).isTrue()

        val intent = OverlayPermission.settingsIntent(app)
        assertThat(intent.action).isEqualTo(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        assertThat(intent.data?.schemeSpecificPart).isEqualTo(app.packageName)
    }

    @Test
    fun `without the permission the screen can't be held, and letting go is harmless`() {
        ShadowSettings.setCanDrawOverlays(false)
        val keepAwake = OverlayKeepAwake(app, log)

        assertThat(keepAwake.canHold()).isFalse()
        assertThat(keepAwake.hold()).isFalse()
        keepAwake.release()
        keepAwake.release()
    }

    @Test
    fun `keep screen on asks for the overlay and for location`() {
        val overlay = ScreenOnModule.drawOverApps()
        val location = ScreenOnModule.screenOnLocation()

        assertThat(overlay.feature).isEqualTo(Feature.KEEP_SCREEN_ON)
        assertThat(location.feature).isEqualTo(Feature.KEEP_SCREEN_ON)
        assertThat(overlay.settingsIntent(app).action).isEqualTo(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        assertThat(location.settingsIntent(app).action).isEqualTo(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        assertThat(location.isGranted(app)).isFalse()
        assertThat(listOf(overlay.id, location.id)).containsNoDuplicates()
    }

    // ---- Screen state and motion ----

    @Test
    fun `the screen state comes first, then follows screen on and off`() = runTest {
        val sensors = AndroidScreenOnSensors(app)

        sensors.screenOn().test {
            assertThat(awaitItem()).isTrue()

            app.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
            shadowOf(Looper.getMainLooper()).idle()
            assertThat(awaitItem()).isFalse()

            app.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
            shadowOf(Looper.getMainLooper()).idle()
            assertThat(awaitItem()).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a phone without an accelerometer never sends motion`() = runTest {
        assertThat(AndroidScreenOnSensors(app).motion().toList()).isEmpty()
    }

    @Test
    fun `accelerometer readings come with gravity, sensors only while listened to`() = runTest {
        val manager = app.getSystemService(SensorManager::class.java)
        val accelerometer = ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER)
        shadowOf(manager).addSensor(accelerometer)

        AndroidScreenOnSensors(app).motion().test {
            val event = SensorEventBuilder.newBuilder()
                .setSensor(accelerometer)
                .setValues(floatArrayOf(0f, 6f, 8f))
                .setTimestamp(5_000_000_000L)
                .build()
            shadowOf(manager).sendSensorEventToListeners(event)

            val sample = awaitItem()
            assertThat(sample.timeMillis).isEqualTo(5_000L)
            assertThat(sample.acceleration).isEqualTo(Vec3(0f, 6f, 8f))
            // Without a gravity sensor the first reading is taken as gravity.
            assertThat(sample.gravity).isEqualTo(Vec3(0f, 6f, 8f))
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- Wi-Fi names and outside ----

    @Test
    fun `wi-fi names lose their quotes, hidden names read as none`() {
        fun info(ssid: String): WifiInfo = ShadowWifiInfo.newInstance().also { shadowOf(it).setSSID(ssid) }

        assertThat(AndroidContextSensors.ssidOf(null)).isNull()
        assertThat(AndroidContextSensors.ssidOf(info("Home"))).isEqualTo("Home")
        assertThat(AndroidContextSensors.ssidOf(info("<unknown ssid>"))).isNull()
    }

    @Test
    fun `outside follows the saved places you're at`() = runTest {
        val inside = MutableStateFlow<Set<Long>>(emptySet())
        val detector = mockk<PlaceDetector> { every { this@mockk.inside } returns inside }
        val home = Place(id = 1, name = "Home", latitude = 0.0, longitude = 0.0)
        val repository = mockk<ContextRepository> { every { places } returns MutableStateFlow(listOf(home)) }
        val clock = object : Clock {
            override fun nowMillis() = 0L
            override val zone: ZoneId = ZoneId.of("Asia/Dhaka")
        }

        AndroidOutsideDetector(app, detector, repository, clock).outside().test {
            assertThat(awaitItem()).isTrue()

            inside.value = setOf(1L)
            assertThat(awaitItem()).isFalse()

            inside.value = emptySet()
            assertThat(awaitItem()).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
