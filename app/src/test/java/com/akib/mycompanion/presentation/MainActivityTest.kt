package com.akib.mycompanion.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Smoke test: the real app (Hilt graph, Room, both tabs) starts and navigates. */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun `opens on People and switches to Reminders`() {
        compose.waitUntilAtLeastOneExists(hasText("No one here yet"), timeoutMillis = 10_000)
        compose.onNodeWithText("Call someone").assertIsDisplayed()

        compose.onNodeWithText("Reminders").performClick()

        compose.onNodeWithText("Send now").assertIsDisplayed()
        compose.onNodeWithText("Nothing scheduled").performScrollTo().assertIsDisplayed()
    }
}
