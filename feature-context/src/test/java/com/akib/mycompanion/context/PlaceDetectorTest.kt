package com.akib.mycompanion.context

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.context.data.ContextRepository
import com.akib.mycompanion.context.device.PlaceDetector
import com.akib.mycompanion.context.domain.Place
import com.akib.mycompanion.core.log.AppLog
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** Without location access (Android's proximity alerts aren't simulated by Robolectric). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PlaceDetectorTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val places = MutableStateFlow(
        listOf(Place(id = 3, name = "Office", latitude = 23.8, longitude = 90.4, wifiNames = listOf("Office-5G"))),
    )
    private val repository = mockk<ContextRepository> { every { places } returns this@PlaceDetectorTest.places }
    private val log = AppLog(Files.createTempDirectory("log").toFile(), mirrorToLogcat = false)
    private val detector = PlaceDetector(app, repository, log)

    @Test
    fun `arriving and leaving come from the proximity alerts`() {
        assertThat(detector.inside.value).isEmpty()

        detector.onProximity(3, entering = true)
        detector.onProximity(5, entering = true)
        assertThat(detector.inside.value).containsExactly(3L, 5L)

        detector.onProximity(3, entering = false)
        assertThat(detector.inside.value).containsExactly(5L)

        detector.onProximity(9, entering = false)
        assertThat(detector.inside.value).containsExactly(5L)
    }

    @Test
    fun `without location access it starts, stops and stays quiet`() = runTest {
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())

        detector.start(scope)
        runCurrent()
        places.value = places.value + Place(id = 4, name = "Gym", latitude = 23.7, longitude = 90.3)
        runCurrent()
        detector.checkWifi()

        assertThat(detector.inside.value).isEmpty()
        scope.cancel()
        runCurrent()
        assertThat(log.read().map { it.message }).contains("no location access: places are detected by Wi-Fi only")
    }
}
