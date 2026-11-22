package dev.akibh.mycompanion.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.akibh.mycompanion.core.settings.Feature
import dev.akibh.mycompanion.domain.model.Contact
import dev.akibh.mycompanion.presentation.contacts.ContactsScreen
import dev.akibh.mycompanion.presentation.contacts.ContactsUiState
import dev.akibh.mycompanion.presentation.home.HomeScreen
import dev.akibh.mycompanion.presentation.home.HomeUiState
import dev.akibh.mycompanion.ui.components.DotText
import dev.akibh.mycompanion.ui.theme.CompanionTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApprovedDesignTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `dotted branding exposes readable text and unsupported scripts remain readable`() {
        compose.setContent {
            CompanionTheme {
                androidx.compose.foundation.layout.Column {
                    DotText("My\nCompanion")
                    DotText("বাংলা")
                }
            }
        }
        compose.onNodeWithText("My Companion").assertIsDisplayed()
        compose.onNodeWithText("বাংলা").assertIsDisplayed()
    }

    @Test
    fun `tapping a home tile toggles it and its arrow opens the feature`() {
        val routes = mutableListOf<String>()
        val toggles = mutableListOf<Pair<Feature, Boolean>>()
        compose.setContent {
            CompanionTheme(darkTheme = true) {
                HomeScreen(
                    state = HomeUiState(),
                    featureRoute = { it.name },
                    onPauseChange = {},
                    onFeatureChange = { feature, enabled -> toggles += feature to enabled },
                    onOpen = { routes += it },
                )
            }
        }
        compose.onNodeWithContentDescription("Open Voice mode").performClick()
        compose.onNodeWithText("Voice mode").assertIsOff().performClick()
        compose.onNodeWithText("Quran").performScrollTo().performClick()
        // AI engines is a developer tool: it lives in Settings, not on Home.
        compose.onNodeWithText("AI engines").assertDoesNotExist()
        compose.onNodeWithContentDescription("Open Missed-call SMS").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Settings").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Pause all automatic actions").performScrollTo().assertIsOff()
        assertThat(routes)
            .containsExactly(Feature.VOICE_MODE.name, "quran", Feature.MISSED_CALL_SMS.name, "settings")
            .inOrder()
        assertThat(toggles).containsExactly(Feature.VOICE_MODE to true)
    }

    @Test
    fun `people search filters names and numbers and still calls the selected person`() {
        val calls = mutableListOf<String>()
        compose.setContent {
            CompanionTheme {
                ContactsScreen(
                    state = ContactsUiState(
                        contacts = listOf(Contact(1, "Amma", "+8801711123456"), Contact(2, "Nadia", "+8801811123456")),
                        loading = false,
                    ),
                    onAddClick = {},
                    onCall = { calls += it.name },
                    onEdit = {},
                    onDelete = {},
                    onPinShortcut = {},
                )
            }
        }
        compose.onNodeWithContentDescription("Find a person").performTextInput("Nadia")
        compose.onNodeWithText("Amma").assertDoesNotExist()
        compose.onNodeWithContentDescription("Call Nadia on WhatsApp").performClick()
        assertThat(calls).containsExactly("Nadia")
    }
}
