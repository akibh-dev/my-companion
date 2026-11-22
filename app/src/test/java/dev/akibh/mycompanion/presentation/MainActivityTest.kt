package dev.akibh.mycompanion.presentation

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import dev.akibh.mycompanion.MainActivity
import dev.akibh.mycompanion.di.PlaybackModule
import dev.akibh.mycompanion.domain.port.Playback
import dev.akibh.mycompanion.fakes.FakePlayback
import dev.akibh.mycompanion.presentation.onboarding.FirstRun
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
        compose.waitUntilAtLeastOneExists(hasText("Active"), timeoutMillis = 10_000)
        compose.onNodeWithText("My Companion").assertIsDisplayed()

        compose.onNodeWithContentDescription("People").performClick()
        compose.waitUntilAtLeastOneExists(hasText("No one here yet"), timeoutMillis = 10_000)

        compose.onNodeWithContentDescription("Reminders").performClick()
        compose.onNodeWithContentDescription("Add reminder").performClick()
        compose.onNodeWithText("Send now").assertIsDisplayed()
        compose.onNodeWithContentDescription("Drag handle").performSemanticsAction(SemanticsActions.Dismiss)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Nothing scheduled"))
        compose.onNodeWithText("Nothing scheduled").assertIsDisplayed()
    }

    @Test
    fun `the pause switch is labelled and pauses everything`() {
        compose.waitUntilAtLeastOneExists(hasText("Active"), timeoutMillis = 10_000)
        compose.onNodeWithContentDescription("Pause all automatic actions").assertIsDisplayed()

        compose.onNodeWithContentDescription("Pause all automatic actions").assertIsOff().performClick()
        compose.waitUntilAtLeastOneExists(hasText("Paused"), timeoutMillis = 10_000)
        compose.onNodeWithContentDescription("Pause all automatic actions").assertIsOn().performClick()
        compose.waitUntilAtLeastOneExists(hasText("Active"), timeoutMillis = 10_000)
    }

    @Test
    fun `player tab shows the link box`() {
        compose.waitUntilAtLeastOneExists(hasText("Active"), timeoutMillis = 10_000)

        compose.onNodeWithContentDescription("Player").performClick()

        compose.onNodeWithText("Open link").assertIsDisplayed()
        compose.onNodeWithText("Up next").assertIsDisplayed()
    }

    @Test
    fun `settings opens the log and comes back home`() {
        compose.waitUntilAtLeastOneExists(hasText("Active"), timeoutMillis = 10_000)

        // Tools such as Log live in Settings (top right of Home), not in Home's list.
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Log"))
        compose.onNodeWithText("Log").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Heartbeat gaps"), timeoutMillis = 10_000)
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Active"), timeoutMillis = 10_000)

        // Home restores its scroll position; branding is part of the list in the approved design.
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.waitUntilAtLeastOneExists(hasText("My Companion"), timeoutMillis = 10_000)
    }

    @Test
    fun `navigation compacts while scrolled and expands at the top without losing tab actions`() {
        compose.waitUntilAtLeastOneExists(hasText("Active"), timeoutMillis = 10_000)
        val expanded = compose.onNodeWithTag("companion-navigation").fetchSemanticsNode().boundsInRoot.width
        compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        compose.waitForIdle()
        val compact = compose.onNodeWithTag("companion-navigation").fetchSemanticsNode().boundsInRoot.width
        assertThat(compact).isLessThan(expanded)
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.waitForIdle()
        val restored = compose.onNodeWithTag("companion-navigation").fetchSemanticsNode().boundsInRoot.width
        assertThat(restored).isWithin(1f).of(expanded)
        compose.onNodeWithContentDescription("People").performClick()
        compose.waitUntilAtLeastOneExists(hasText("No one here yet"), timeoutMillis = 10_000)
    }
}
