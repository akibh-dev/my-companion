package dev.akibh.mycompanion.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.akibh.mycompanion.ui.theme.CompanionTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LayoutTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun sectionLabelIsAHeadingReadAsWritten() {
        compose.setContent { CompanionTheme { Section("Where") { SwitchRow("Speaker", false, {}) } } }

        compose.onNodeWithText("Where")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun tappingAnywhereOnASwitchRowToggles() {
        compose.setContent {
            CompanionTheme {
                var on by remember { mutableStateOf(false) }
                SwitchRow("Read aloud", on, { on = it }, summary = "Also a tile")
            }
        }
        val row = compose.onNodeWithText("Read aloud", useUnmergedTree = false)
        row.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).assertIsOff()

        compose.onNodeWithText("Also a tile").performClick()

        row.assertIsOn()
    }

    @Test
    fun addButtonIsNamedForTalkBack() {
        var clicks = 0
        compose.setContent { CompanionTheme { AddButton("Add place", onClick = { clicks++ }) } }

        compose.onNodeWithContentDescription("Add place").performClick()

        assertThat(clicks).isEqualTo(1)
    }
}
