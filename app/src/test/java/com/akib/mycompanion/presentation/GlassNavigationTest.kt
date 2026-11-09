package com.akib.mycompanion.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.Tab
import com.akib.mycompanion.ui.components.GlassNavigation
import com.akib.mycompanion.ui.theme.CompanionTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlassNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `tab selection changes immediately while the highlight glides between destinations`() {
        var selected by mutableStateOf(Tab.HOME)
        compose.setContent {
            CompanionTheme(darkTheme = false) {
                GlassNavigation(selected, false, rememberGraphicsLayer(), Offset.Zero, onSelect = { selected = it })
            }
        }
        val start = compose.onNodeWithTag("navigation-selection").fetchSemanticsNode().boundsInRoot.left
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Player").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(80)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Player").assertIsSelected()
        val middle = compose.onNodeWithTag("navigation-selection").fetchSemanticsNode().boundsInRoot.left
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        val end = compose.onNodeWithTag("navigation-selection").fetchSemanticsNode().boundsInRoot.left
        assertThat(middle).isGreaterThan(start)
        assertThat(middle).isLessThan(end)
    }
}
