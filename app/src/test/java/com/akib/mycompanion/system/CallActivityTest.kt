package com.akib.mycompanion.system

import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.domain.usecase.CallContactUseCase
import com.akib.mycompanion.domain.usecase.CallResult
import com.akib.mycompanion.presentation.call.CallActivity
import com.akib.mycompanion.presentation.contacts.CallMessages
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast

/** The invisible activity behind home-screen shortcuts and the notification "Call" button. */
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class CallActivityTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val callContact: CallContactUseCase = mockk()

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `calls the person and closes`() {
        coEvery { callContact(5L) } returns CallResult.CallStarted

        val activity = launch(CallActivity.intent(app, 5L))

        coVerify { callContact(5L) }
        assertThat(activity.isFinishing).isTrue()
        assertThat(ShadowToast.getLatestToast()).isNull()
    }

    @Test
    fun `explains a chat fallback`() {
        coEvery { callContact(5L) } returns CallResult.ChatOpened

        launch(CallActivity.intent(app, 5L))

        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo(CallMessages.forResult(CallResult.ChatOpened))
    }

    @Test
    fun `clears the reminder notification it came from`() {
        coEvery { callContact(5L) } returns CallResult.CallStarted
        val manager = app.getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(app, "reminders")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Call Mum")
            .build()
        manager.notify(9, notification)

        launch(CallActivity.intent(app, 5L, notificationId = 9))

        assertThat(shadowOf(manager).getNotification(9)).isNull()
    }

    @Test
    fun `without a contact it just closes`() {
        val activity = launch(Intent(app, CallActivity::class.java))

        coVerify(exactly = 0) { callContact(any<Long>()) }
        assertThat(activity.isFinishing).isTrue()
    }

    private fun launch(intent: Intent): CallActivity {
        val activity = Robolectric.buildActivity(CallActivity::class.java, intent).create().get()
        ShadowLooper.idleMainLooper()
        return activity
    }
}
