package com.akib.mycompanion.presentation

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.MainActivity
import com.akib.mycompanion.presentation.onboarding.FirstRun
import com.akib.mycompanion.presentation.onboarding.FirstRunGuide
import com.akib.mycompanion.presentation.onboarding.guidePages
import com.akib.mycompanion.ui.theme.CompanionTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirstRunGuideTest {

    @get:Rule
    val compose = createComposeRule()

    private val finished = mutableListOf<Boolean>()

    private fun show() = compose.setContent { CompanionTheme { FirstRunGuide(onFinish = { finished += it }) } }

    @Test
    fun `four pages, the last one offers to open permissions`() {
        show()
        val pages = guidePages
        assertThat(pages).hasSize(4)

        pages.dropLast(1).forEach { page ->
            compose.onNodeWithText(page.title).assertIsDisplayed()
            compose.onNodeWithText("Next").performClick()
        }
        compose.onNodeWithText(pages.last().title).assertIsDisplayed()
        compose.onNodeWithText("Skip").assertDoesNotExist()
        compose.onNodeWithText("Open permissions").performClick()

        assertThat(finished).containsExactly(true)
    }

    @Test
    fun `back goes to the previous page and skip finishes without permissions`() {
        show()

        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText(guidePages.first().title).assertIsDisplayed()
        compose.onNodeWithText("Skip").performClick()

        assertThat(finished).containsExactly(false)
    }

    @Test
    fun `the guide is remembered once seen`() {
        val app: Application = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences(FirstRun.PREFS, 0).edit().clear().commit()
        assertThat(FirstRun.isDone(app)).isFalse()

        FirstRun.markDone(app)

        assertThat(FirstRun.isDone(app)).isTrue()
    }

    @Test
    fun `a route intent opens that screen in the main activity`() {
        val app: Application = ApplicationProvider.getApplicationContext()

        val intent = MainActivity.openRouteIntent(app, "vault")

        assertThat(intent.component!!.className).isEqualTo(MainActivity::class.java.name)
        assertThat(intent.getStringExtra(MainActivity.EXTRA_ROUTE)).isEqualTo("vault")
    }
}
