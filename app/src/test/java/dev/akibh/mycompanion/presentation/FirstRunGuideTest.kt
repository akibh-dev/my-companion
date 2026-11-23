package dev.akibh.mycompanion.presentation

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.akibh.mycompanion.MainActivity
import dev.akibh.mycompanion.presentation.home.LIBRARY_ROUTE
import dev.akibh.mycompanion.presentation.home.PERMISSIONS_ROUTE
import dev.akibh.mycompanion.presentation.onboarding.FirstRun
import dev.akibh.mycompanion.presentation.onboarding.FirstRunGuide
import dev.akibh.mycompanion.presentation.onboarding.guidePages
import dev.akibh.mycompanion.quran.domain.QuranReminder
import dev.akibh.mycompanion.system.QuranTileService
import dev.akibh.mycompanion.ui.theme.CompanionTheme
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
    fun `back goes to the previous page and skip jumps to the last page`() {
        show()

        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText(guidePages.first().title).assertIsDisplayed()
        compose.onNodeWithText("Skip").performClick()

        compose.onNodeWithText(guidePages.last().title).assertIsDisplayed()
        compose.onNodeWithText("Skip").assertDoesNotExist()
        assertThat(finished).isEmpty()

        compose.onNodeWithText("Start").performClick()
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

        val intent = MainActivity.openRouteIntent(app, "export")

        assertThat(intent.component!!.className).isEqualTo(MainActivity::class.java.name)
        assertThat(intent.getStringExtra(MainActivity.EXTRA_ROUTE)).isEqualTo("export")
    }

    @Test
    fun `an intent can only open the screens the app itself links to`() {
        // What the Quran widget/tile/reminder, the library widget and the guide send.
        listOf(QuranTileService.CONTINUE_ROUTE, QuranReminder.ROUTE, LIBRARY_ROUTE, "export", PERMISSIONS_ROUTE)
            .forEach { assertThat(MainActivity.isOpenableRoute(it)).isTrue() }

        // Anything else another app might put in the extra.
        listOf("quran/read/604", "ai", "reminders", "", "export/../ai")
            .forEach { assertThat(MainActivity.isOpenableRoute(it)).isFalse() }
    }
}
