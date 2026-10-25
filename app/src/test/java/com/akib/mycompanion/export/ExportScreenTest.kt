package com.akib.mycompanion.export

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.core.settings.Settings
import com.akib.mycompanion.ui.theme.CompanionTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExportScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun show(state: ExportUiState) = compose.setContent {
        CompanionTheme {
            ExportScreen(
                state = state,
                onExportAll = { calls += "exportAll" },
                onExportSettings = { calls += "exportSettings" },
                onRestore = { calls += "restore" },
                onConfirmRestore = { calls += "confirm" },
                onCancelRestore = { calls += "cancel" },
            )
        }
    }

    @Test
    fun `export, save and restore are offered and the vault is explained`() {
        show(ExportUiState())

        compose.onNodeWithText("The vault isn't included", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Export my data…").performClick()
        compose.onNodeWithText("Save settings…").performClick()
        compose.onNodeWithText("Restore settings…").performClick()

        assertThat(calls).containsExactly("exportAll", "exportSettings", "restore").inOrder()
    }

    @Test
    fun `buttons wait while a file is being written`() {
        show(ExportUiState(busy = true, status = "Saving your data…"))

        compose.onNodeWithText("Saving your data…").assertIsDisplayed()
        compose.onNodeWithText("Export my data…").assertIsNotEnabled()
    }

    @Test
    fun `restoring asks first and says what will be switched on`() {
        val bundle = SettingsBundle(app = Settings(enabled = setOf(Feature.VOICE_MODE)))
        show(ExportUiState(pendingRestore = bundle))

        compose.onNodeWithText("Replace your settings?").assertIsDisplayed()
        compose.onNodeWithText("Features on: Voice mode", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Replace").performClick()

        assertThat(calls).containsExactly("confirm")
    }
}
