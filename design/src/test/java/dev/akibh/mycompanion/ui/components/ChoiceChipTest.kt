package dev.akibh.mycompanion.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.akibh.mycompanion.ui.theme.CompanionTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChoiceChipTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun onlyTheChosenOptionIsSelectedAndCarriesTheDot() {
        compose.setContent {
            CompanionTheme {
                var choice by remember { mutableStateOf("Headphones") }
                ChoiceRow {
                    listOf("Headphones", "Speaker").forEach { option ->
                        ChoiceChip(option, selected = choice == option, onClick = { choice = option })
                    }
                }
            }
        }
        compose.onNodeWithText("Headphones").assertIsSelected()
        compose.onNodeWithText("Speaker").assertIsNotSelected()
        compose.onAllNodesWithTag(CHOICE_DOT_TAG, useUnmergedTree = true).assertCountEquals(1)

        compose.onNodeWithText("Speaker").performClick()

        compose.onNodeWithText("Speaker").assertIsSelected()
        compose.onNodeWithText("Headphones").assertIsNotSelected()
        compose.onAllNodesWithTag(CHOICE_DOT_TAG, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun singleAndMultipleChoicesAnnounceTheRightRole() {
        compose.setContent {
            CompanionTheme {
                ChoiceRow {
                    ChoiceChip("One of", selected = false, onClick = {})
                    ChoiceChip("Any of", selected = true, onClick = {}, multiple = true)
                }
            }
        }
        compose.onNodeWithText(
            "One of"
        ).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        compose.onNodeWithText("Any of").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
    }
}
