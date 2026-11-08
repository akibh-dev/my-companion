package com.akib.mycompanion.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.domain.model.Contact
import com.akib.mycompanion.presentation.contacts.ContactsScreen
import com.akib.mycompanion.presentation.contacts.ContactsUiState
import com.akib.mycompanion.presentation.home.HomeScreen
import com.akib.mycompanion.presentation.home.HomeUiState
import com.akib.mycompanion.ui.components.DotText
import com.akib.mycompanion.ui.theme.CompanionTheme
import com.google.common.truth.Truth.assertThat
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
    fun `home cards open features and quick toggle is separate from navigation`() {
        val routes = mutableListOf<String>()
        val toggles = mutableListOf<Pair<Feature, Boolean>>()
        compose.setContent {
            CompanionTheme(darkTheme = true) {
                HomeScreen(
                    state = HomeUiState(),
                    featureRoute = { it.name },
                    links = emptyList(),
                    onPauseChange = {},
                    onFeatureChange = { feature, enabled -> toggles += feature to enabled },
                    onOpen = { routes += it },
                )
            }
        }
        compose.onNodeWithText("Voice mode").performClick()
        compose.onNodeWithContentDescription("Enable Voice mode").assertIsOff().performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithContentDescription("Pause all automatic actions").performScrollTo().assertIsOff()
        assertThat(routes).containsExactly(Feature.VOICE_MODE.name, "settings").inOrder()
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
