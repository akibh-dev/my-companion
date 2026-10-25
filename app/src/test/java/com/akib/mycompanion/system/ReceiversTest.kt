package com.akib.mycompanion.system

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.di.ReminderEventsModule
import com.akib.mycompanion.presentation.shortcuts.RefreshHomeShortcuts
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Receivers are wired through Hilt; the reminder logic itself is swapped for a mock. */
@HiltAndroidTest
@UninstallModules(ReminderEventsModule::class)
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ReceiversTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val events: ReminderEvents = mockk(relaxed = true)

    @BindValue
    @JvmField
    val refreshHomeShortcuts: RefreshHomeShortcuts = mockk(relaxed = true)

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() = hiltRule.inject()

    @Test
    fun `alarm fires the reminder`() {
        ReminderReceiver().onReceive(context, ReminderReceiver.fireIntent(context, 5))

        coVerify(timeout = TIMEOUT) { events.fire(5) }
    }

    @Test
    fun `snooze action snoozes`() {
        ReminderReceiver().onReceive(context, ReminderReceiver.snoozeIntent(context, 6))

        coVerify(timeout = TIMEOUT) { events.snooze(6) }
    }

    @Test
    fun `done action dismisses`() {
        ReminderReceiver().onReceive(context, ReminderReceiver.doneIntent(context, 7))

        coVerify(timeout = TIMEOUT) { events.done(7) }
    }

    @Test
    fun `intents without a reminder id are ignored`() {
        ReminderReceiver().onReceive(context, Intent(ReminderReceiver.ACTION_FIRE))

        coVerify(exactly = 0) { events.fire(any()) }
    }

    @Test
    fun `boot restores every reminder`() {
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        coVerify(timeout = TIMEOUT) { events.restoreAll() }
        coVerify(exactly = 0) { refreshHomeShortcuts() }
    }

    @Test
    fun `app update restores every reminder and refreshes pinned icons`() {
        BootReceiver().onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))

        coVerify(timeout = TIMEOUT) { events.restoreAll() }
        coVerify(timeout = TIMEOUT) { refreshHomeShortcuts() }
    }

    @Test
    fun `boot receiver ignores other broadcasts`() {
        BootReceiver().onReceive(context, Intent(Intent.ACTION_SCREEN_ON))

        coVerify(exactly = 0) { events.restoreAll() }
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
