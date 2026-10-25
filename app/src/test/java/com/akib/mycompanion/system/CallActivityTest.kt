package com.akib.mycompanion.system

import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.di.ApplicationScope
import com.akib.mycompanion.di.CoroutinesModule
import com.akib.mycompanion.di.IoDispatcher
import com.akib.mycompanion.domain.usecase.CallContactUseCase
import com.akib.mycompanion.domain.usecase.CallResult
import com.akib.mycompanion.presentation.call.CallActivity
import com.akib.mycompanion.presentation.contacts.CallMessages
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
@UninstallModules(CoroutinesModule::class)
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class CallActivityTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val callContact: CallContactUseCase = mockk()

    /** The real IO dispatcher would finish the call on another thread, after the test has looked. */
    @BindValue
    @JvmField
    @IoDispatcher
    val io: CoroutineDispatcher = Dispatchers.Main.immediate

    @BindValue
    @JvmField
    @ApplicationScope
    val appScope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate)

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

    @Test
    fun `another app can't make it call someone`() {
        // What device test finding #4 did: `am start -n …/CallActivity --el contact_id 2`.
        val forged = Intent(app, CallActivity::class.java).putExtra(CallActivity.EXTRA_CONTACT_ID, 5L)

        val activity = launch(forged)

        coVerify(exactly = 0) { callContact(any<Long>()) }
        assertThat(activity.isFinishing).isTrue()
        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo(CallActivity.OUTDATED_SHORTCUT)
    }

    @Test
    fun `a guessed token is refused too`() {
        val guessed = CallActivity.intent(app, 5L).putExtra(CallActivity.EXTRA_TOKEN, "not-the-token")

        launch(guessed)

        coVerify(exactly = 0) { callContact(any<Long>()) }
    }

    private fun launch(intent: Intent): CallActivity {
        val activity = Robolectric.buildActivity(CallActivity::class.java, intent).create().get()
        ShadowLooper.idleMainLooper()
        return activity
    }
}
