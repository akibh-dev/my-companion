package com.akib.mycompanion.presentation

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.MainActivity
import com.akib.mycompanion.di.PlaybackModule
import com.akib.mycompanion.domain.port.Playback
import com.akib.mycompanion.fakes.FakePlayback
import com.akib.mycompanion.presentation.onboarding.FirstRun
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Smoke test: the real app (Hilt graph, Room, both tabs) starts and navigates. */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@UninstallModules(PlaybackModule::class)
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    /** The first-run guide was seen already, so the activity opens straight on Home. */
    @get:Rule(order = 1)
    val guideSeen = object : TestWatcher() {
        override fun starting(description: Description) {
            FirstRun.markDone(ApplicationProvider.getApplicationContext<Context>())
        }
    }

    @get:Rule(order = 2)
    val compose = createAndroidComposeRule<MainActivity>()

    /** Don't start the real media service in a UI test. */
    @BindValue
    @JvmField
    val playback: Playback = FakePlayback()

    @Test
    fun `opens on Home with the pause switch, and switches tabs`() {
        compose.waitUntilAtLeastOneExists(hasText("Running"), timeoutMillis = 10_000)
        compose.onNodeWithText("My Companion").assertIsDisplayed()

        compose.onNodeWithText("People").performClick()
        compose.waitUntilAtLeastOneExists(hasText("No one here yet"), timeoutMillis = 10_000)

        compose.onNodeWithText("Reminders").performClick()
        compose.onNodeWithText("Send now").assertIsDisplayed()
        compose.onNodeWithText("Nothing scheduled").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `player tab shows the link box`() {
        compose.waitUntilAtLeastOneExists(hasText("Running"), timeoutMillis = 10_000)

        compose.onNodeWithText("Player").performClick()

        compose.onNodeWithText("Open link").assertIsDisplayed()
        compose.onNodeWithText("Up next").assertIsDisplayed()
    }

    @Test
    fun `home opens the log and comes back`() {
        compose.waitUntilAtLeastOneExists(hasText("Running"), timeoutMillis = 10_000)

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Log"))
        compose.onNodeWithText("Log").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Heartbeat gaps"), timeoutMillis = 10_000)
        compose.onNodeWithContentDescription("Back").performClick()

        compose.waitUntilAtLeastOneExists(hasText("My Companion"), timeoutMillis = 10_000)
    }
}
