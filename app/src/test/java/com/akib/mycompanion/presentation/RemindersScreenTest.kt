package com.akib.mycompanion.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.domain.model.Contact
import com.akib.mycompanion.presentation.reminders.ChoiceChip
import com.akib.mycompanion.presentation.reminders.ReminderForm
import com.akib.mycompanion.presentation.reminders.RemindersScreen
import com.akib.mycompanion.presentation.reminders.RemindersUiState
import com.akib.mycompanion.presentation.reminders.UpcomingReminder
import com.akib.mycompanion.presentation.reminders.WhenChoice
import com.akib.mycompanion.ui.theme.CompanionTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemindersScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()
    private val presets = listOf(
        ChoiceChip(WhenChoice.Now, "Now", selected = true),
        ChoiceChip(WhenChoice.InMinutes(15), "15 min", selected = false),
    )

    private fun show(state: RemindersUiState, notificationsAllowed: Boolean = true) = compose.setContent {
        CompanionTheme {
            RemindersScreen(
                state = state,
                notificationsAllowed = notificationsAllowed,
                onAllowNotifications = { events += "allow" },
                onTitleChange = { events += "title:$it" },
                onMessageChange = { events += "message:$it" },
                onChoiceChange = { events += "choice:$it" },
                onPickTime = { events += "pickTime" },
                onRepeatDailyChange = { events += "repeat:$it" },
                onContactChange = { events += "contact:$it" },
                onSubmit = { events += "submit" },
                onCancelReminder = { events += "cancel:$it" },
            )
        }
    }

    @Test
    fun `send button needs a title or message`() {
        show(RemindersUiState(presets = presets))

        compose.onNodeWithText("Send now").assertIsNotEnabled()
    }

    @Test
    fun `send now with content submits`() {
        show(RemindersUiState(form = ReminderForm(title = "Hello"), presets = presets))

        compose.onNodeWithText("Send now").assertIsEnabled().performClick()

        assertThat(events).containsExactly("submit")
    }

    @Test
    fun `a later time changes the button to set reminder`() {
        val form = ReminderForm(title = "Hi", choice = WhenChoice.InMinutes(15))
        show(RemindersUiState(form = form, presets = presets))

        compose.onNodeWithText("Set reminder").assertIsDisplayed()
    }

    @Test
    fun `chips choose when`() {
        show(RemindersUiState(presets = presets))

        compose.onNodeWithText("15 min").performClick()
        compose.onNodeWithText("Pick time").performClick()

        assertThat(events).containsExactly("choice:${WhenChoice.InMinutes(15)}", "pickTime").inOrder()
    }

    @Test
    fun `repeat daily only appears for a clock time`() {
        val form = ReminderForm(title = "Hi", choice = WhenChoice.At(8, 0))
        show(RemindersUiState(form = form, pickedTimeLabel = "8:00 AM"))

        compose.onNodeWithText("8:00 AM").assertIsDisplayed()
        compose.onNodeWithText("Repeat every day").assertIsDisplayed()
    }

    @Test
    fun `repeat daily is hidden for now`() {
        show(RemindersUiState(presets = presets))

        compose.onNodeWithText("Repeat every day").assertDoesNotExist()
    }

    @Test
    fun `a call button can be attached when people exist`() {
        val mum = Contact(id = 3, name = "Mum", phone = "+8801711123456")
        show(RemindersUiState(presets = presets, contacts = listOf(mum)))

        compose.onNodeWithText("Add a call button (optional)").performClick()
        compose.onNodeWithText("Mum").performClick()

        assertThat(events).containsExactly("contact:3")
    }

    @Test
    fun `upcoming reminders show when and can be cancelled`() {
        val upcoming = UpcomingReminder(
            id = 9,
            title = "Medicine",
            message = "After breakfast",
            whenText = "Tomorrow, 8:00 AM",
            daily = true,
            contactName = "Mum",
        )
        show(RemindersUiState(presets = presets, upcoming = listOf(upcoming)))

        compose.onNodeWithText("Tomorrow, 8:00 AM · Daily · Call Mum").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Cancel Medicine").performClick()

        assertThat(events).containsExactly("cancel:9")
    }

    @Test
    fun `nothing scheduled is said plainly`() {
        show(RemindersUiState(presets = presets))

        compose.onNodeWithText("Nothing scheduled").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a banner asks to allow notifications when they are off`() {
        show(RemindersUiState(presets = presets), notificationsAllowed = false)

        compose.onNodeWithText("Allow").performClick()

        assertThat(events).containsExactly("allow")
    }
}
