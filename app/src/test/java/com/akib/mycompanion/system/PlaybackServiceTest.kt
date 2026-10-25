package com.akib.mycompanion.system

import android.app.Service
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.player.PlaybackService
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/** The real media service, created by Robolectric (ExoPlayer and the session work on the JVM). */
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PlaybackServiceTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Test
    fun `is not restarted after the process is killed`() {
        // Device test finding #9: START_STICKY brought back an idle service with an empty queue.
        val controller = Robolectric.buildService(PlaybackService::class.java).create()

        val result = controller.get().onStartCommand(null, 0, 1)

        assertThat(result).isEqualTo(Service.START_NOT_STICKY)
        controller.destroy()
    }
}
