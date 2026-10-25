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
import com.akib.mycompanion.presentation.reminders.DayChip
import com.akib.mycompanion.presentation.reminders.EveryChip
import com.akib.mycompanion.presentation.reminders.ReminderForm
import com.akib.mycompanion.presentation.reminders.RemindersScreen
import com.akib.mycompanion.presentation.reminders.RemindersUiState
import com.akib.mycompanion.presentation.reminders.RepeatChoice
import com.akib.mycompanion.presentation.reminders.UpcomingReminder
import com.akib.mycompanion.presentation.reminders.WhenChoice
import com.akib.mycompanion.ui.theme.CompanionTheme
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
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
                onRepeatChange = { events += "repeat:$it" },
                onDayToggle = { events += "day:$it" },
                onEveryChange = { events += "every:$it" },
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
    fun `repeat choices are offered and on days shows the weekdays`() {
        val form =
            ReminderForm(
                title = "Hi",
                choice = WhenChoice.At(8, 0),
                repeat = RepeatChoice.DAYS,
                days = setOf(DayOfWeek.SUNDAY)
            )
        val dayChips = listOf(
            DayChip(DayOfWeek.SUNDAY, "Sun", selected = true),
            DayChip(DayOfWeek.MONDAY, "Mon", selected = false),
        )
        show(
            RemindersUiState(form = form, pickedTimeLabel = "8:00 AM", dayChips = dayChips, repeatSummary = "Every Sun")
        )

        compose.onNodeWithText("Every Sun").assertIsDisplayed()
        compose.onNodeWithText("Mon").performClick()
        compose.onNodeWithText("Every day").performClick()

        assertThat(events).containsExactly("day:MONDAY", "repeat:DAILY").inOrder()
    }

    @Test
    fun `every few hours offers intervals`() {
        val form = ReminderForm(title = "Water", repeat = RepeatChoice.EVERY, everyMinutes = 60)
        val everyChips = listOf(EveryChip(60, "1 h", selected = true), EveryChip(120, "2 h", selected = false))
        show(RemindersUiState(form = form, presets = presets, everyChips = everyChips, repeatSummary = "Every 1 h"))

        compose.onNodeWithText("2 h").performClick()

        assertThat(events).containsExactly("every:120")
    }

    @Test
    fun `a one-off shows no repeat summary`() {
        show(RemindersUiState(presets = presets))

        compose.onNodeWithText("Never").assertIsDisplayed()
        compose.onNodeWithText("Pick at least one day").assertDoesNotExist()
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
            repeatText = "Every day",
            contactName = "Mum",
        )
        show(RemindersUiState(presets = presets, upcoming = listOf(upcoming)))

        compose.onNodeWithText("Tomorrow, 8:00 AM · Every day · Call Mum").performScrollTo().assertIsDisplayed()
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
