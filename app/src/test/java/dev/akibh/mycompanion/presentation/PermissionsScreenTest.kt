package dev.akibh.mycompanion.presentation

import android.content.Intent
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.akibh.mycompanion.core.permissions.Requirement
import dev.akibh.mycompanion.presentation.health.PermissionsScreen
import dev.akibh.mycompanion.ui.theme.CompanionTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun requirement(id: String, granted: Boolean) = Requirement(
        id = id,
        title = "Permission $id",
        why = "Needed for $id",
        feature = null,
        isGranted = { granted },
        settingsIntent = { Intent() },
    )

    @Test
    fun `when everything is granted, let's go takes you on`() {
        var ready = 0
        compose.setContent {
            CompanionTheme {
                PermissionsScreen(
                    listOf(requirement("a", granted = true), requirement("b", granted = true)),
                    onReady = { ready++ },
                )
            }
        }

        compose.onNodeWithText("Ready to roll.").assertExists()
        compose.onNodeWithText("Let's go").performClick()

        assertThat(ready).isEqualTo(1)
    }

    @Test
    fun `the ready button stays hidden while something is missing`() {
        compose.setContent {
            CompanionTheme {
                PermissionsScreen(
                    listOf(requirement("a", granted = true), requirement("b", granted = false)),
                    onReady = {},
                )
            }
        }

        compose.onNodeWithText("Let's go").assertDoesNotExist()
        compose.onNodeWithText("Granted 1 of 2. Each feature only works with its own.").assertExists()
    }
}
