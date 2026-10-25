package com.akib.mycompanion

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real activity, Hilt graph, Room and DataStore on a device; no fake repositories or models. */
@RunWith(AndroidJUnit4::class)
class FeatureDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun enterApp() {
        if (compose.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Skip").performClick()
        }
        compose.onNodeWithText("Home").assertExists()
    }

    @Test
    fun masterPauseCanBeChangedAndRestored() {
        val paused = compose.onAllNodesWithText("Paused").fetchSemanticsNodes().isNotEmpty()
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        compose.onNodeWithText(if (paused) "Running" else "Paused").assertIsDisplayed()
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        compose.onNodeWithText(if (paused) "Paused" else "Running").assertIsDisplayed()
    }

    @Test
    fun peopleCanSaveNormalizedNumberAndRemovePerson() {
        val name = "Device person ${System.nanoTime()}"
        compose.onNodeWithText("People").performClick()
        val addPerson = hasText("Add person") or hasContentDescription("Add person")
        compose.waitUntil(10_000) { compose.onAllNodes(addPerson).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(addPerson).performClick()
        compose.onNodeWithText("Name").performTextInput(name)
        compose.onNodeWithText("WhatsApp number").performTextInput("01711987654")
        compose.onNodeWithText("Save").performClick()
        awaitText(name)
        compose.onNodeWithText("+8801711987654").assertExists()
        compose.onNodeWithContentDescription("More options for $name").performClick()
        compose.onNodeWithText("Remove").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText(name).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun reminderFieldsKeepRapidInputAfterFocusChanges() {
        compose.onNodeWithText("Reminders").performClick()
        repeat(5) { attempt ->
            val title = "Typing replay $attempt"
            val message = "Stay hydrated $attempt"
            compose.onNodeWithText("Title").performTextReplacement(title)
            compose.onNodeWithText("Message").performTextReplacement(message)
            compose.onNodeWithText("Title").assertTextContains(title)
            compose.onNodeWithText("Message").assertTextContains(message)
        }
    }

    @Test
    fun playerRejectsInvalidLink() {
        compose.onNodeWithText("Player").performClick()
        compose.onNodeWithText("YouTube link").performTextInput("not-a-video")
        compose.onNodeWithText("Open link").performClick()
        awaitText("That's not a YouTube video link")
    }

    @Test
    fun playlistCanBeCreatedOpenedAndDeleted() {
        val name = "Device playlist ${System.nanoTime()}"
        compose.onNodeWithText("Player").performClick()
        compose.onNodeWithText("Playlists").performClick()
        compose.onNodeWithText("New playlist").performClick()
        compose.onNodeWithText("Name").performTextInput(name)
        compose.onNodeWithText("Create").performClick()
        awaitText(name)
        compose.onNodeWithText(name).performClick()
        compose.onNodeWithText("Play all").assertExists()
        compose.onNodeWithContentDescription("Playlist options").performClick()
        compose.onNodeWithText("Delete playlist").performClick()
        compose.onNodeWithText("Delete").performClick()
        awaitText("New playlist")
    }

    @Test
    fun contextRulesPlacesAndHistoryAreReachable() {
        openHome("Context rules")
        compose.onNodeWithText("Saved places").performClick()
        compose.onNodeWithContentDescription("Add place").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("History").performClick()
        compose.onNodeWithText("Rule history").assertIsDisplayed()
    }

    @Test
    fun autoReplyHasBusyControlsAndTemplateEditor() {
        openHome("Auto-reply")
        compose.onNodeWithText("Auto-reply to chats").assertExists()
        compose.onNodeWithText("30 min").performClick()
        compose.onNodeWithText("Busy until", substring = true).assertExists()
        compose.onNodeWithText("Off").performClick()
        compose.onNodeWithText("Not busy").assertExists()
        scrollTo("Add reply")
        compose.onNodeWithText("Add reply").performClick()
        compose.onNodeWithText("Reply text").assertExists()
        compose.onNodeWithText("Cancel").performClick()
    }

    @Test
    fun missedCallSmsAndChatDiagnosticsAreReachable() {
        openHome("Missed-call SMS")
        compose.onNodeWithText("Text back after a missed call").assertExists()
        scrollTo("Chat app self-test")
        compose.onNodeWithText("Chat app self-test").performClick()
        compose.onNodeWithText("WhatsApp").assertExists()
        compose.onNodeWithText("Signal").assertExists()
        compose.onNodeWithText("Telegram").assertExists()
    }

    @Test
    fun voiceModeShowsPrivacyAndTestControls() {
        openHome("Voice mode")
        compose.onNodeWithText("Read messages aloud").assertExists()
        compose.onNodeWithText("Where").assertExists()
        scrollTo("Read a test message")
        compose.onNodeWithText("Read a test message").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Rahim", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun captureSpaceShowsCaptureAndSearchControls() {
        openHome("Quick capture")
        compose.onNodeWithText("New capture").assertIsDisplayed()
        compose.onNodeWithText("Search").performTextInput("missing-device-fixture")
        compose.onNodeWithText("Search").assertTextContains("missing-device-fixture")
        compose.onNodeWithText("Voice").assertExists()
    }

    @Test
    fun captureNoteCanBeSavedOpenedAndDeleted() {
        val note = "Device note ${System.nanoTime()}"
        openHome("Quick capture")
        compose.onNodeWithText("New capture").performClick()
        compose.onNodeWithText("Note").performClick()
        compose.onNodeWithText("Note").performTextInput(note)
        compose.onNodeWithText("Save").performClick()
        awaitText("New capture")
        scrollTo(note)
        compose.onNodeWithText(note).performClick()
        compose.onNodeWithText("Note").assertTextContains(note)
        compose.onNodeWithText("Delete").performScrollTo().performClick()
        compose.onAllNodesWithText("Delete").onLast().performClick()
        awaitText("New capture")
    }

    @Test
    fun screenOnHasSensitivityChoices() {
        openHome("Keep screen on")
        compose.onNodeWithText("Keep screen on outdoors").assertExists()
        compose.onNodeWithText("Sensitivity").assertExists()
        compose.onNodeWithText("Strict").performScrollTo().performClick()
        compose.onNodeWithText("Normal").performScrollTo().performClick()
    }

    @Test
    fun vaultRequiresDeviceAuthentication() {
        openHome("Vault")
        compose.waitUntil(10_000) {
            listOf("Set a screen lock first", "Set up", "Unlock").any {
                compose.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test
    fun exportAndSettingsBackupControlsAreAvailable() {
        openHome("Export & backup")
        compose.onNodeWithText("Export my data…").assertExists()
        compose.onNodeWithText("Save settings…").assertExists()
        compose.onNodeWithText("Restore settings…").assertExists()
    }

    @Test
    fun aiEnginesExposeAllFourModelSwitches() {
        openHome("AI engines")
        listOf("Banglish to Bangla", "Speech (voice)", "Speech to text", "Summaries").forEach {
            scrollTo(it)
            compose.onNodeWithText(it).assertIsDisplayed()
        }
    }

    @Test
    fun permissionGuideAndHealthLogOpen() {
        openHome("Permissions & health")
        compose.onNodeWithText("Permissions & health").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        openHome("Log")
        compose.onNodeWithText("Refresh").performClick()
        compose.onNodeWithText("Heartbeat gaps").assertExists()
    }

    private fun openHome(label: String) {
        scrollTo(label)
        compose.onNodeWithText(label).performClick()
        compose.waitForIdle()
    }

    private fun scrollTo(label: String) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label))
    }

    private fun awaitText(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
