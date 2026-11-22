package dev.akibh.mycompanion.presentation

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.akibh.mycompanion.domain.model.Contact
import dev.akibh.mycompanion.presentation.reminders.ChoiceChip
import dev.akibh.mycompanion.presentation.reminders.DayChip
import dev.akibh.mycompanion.presentation.reminders.EveryChip
import dev.akibh.mycompanion.presentation.reminders.ReminderForm
import dev.akibh.mycompanion.presentation.reminders.RemindersScreen
import dev.akibh.mycompanion.presentation.reminders.RemindersUiState
import dev.akibh.mycompanion.presentation.reminders.RepeatChoice
import dev.akibh.mycompanion.presentation.reminders.RepeatUnit
import dev.akibh.mycompanion.presentation.reminders.UpcomingReminder
import dev.akibh.mycompanion.presentation.reminders.WhenChoice
import dev.akibh.mycompanion.ui.theme.CompanionTheme
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

    private val title = TextFieldState()
    private val message = TextFieldState()

    private fun show(
        state: RemindersUiState,
        notificationsAllowed: Boolean = true,
        titleText: String = "",
        openEditor: Boolean = true,
    ) {
        title.setTextAndPlaceCursorAtEnd(titleText)
        compose.setContent {
            CompanionTheme {
                RemindersScreen(
                    state = state,
                    title = title,
                    message = message,
                    notificationsAllowed = notificationsAllowed,
                    onAllowNotifications = { events += "allow" },
                    onChoiceChange = { events += "choice:$it" },
                    onPickTime = { events += "pickTime" },
                    onRepeatChange = { events += "repeat:$it" },
                    onDayToggle = { events += "day:$it" },
                    onEveryChange = { events += "every:$it" },
                    onCustomNumberChange = { events += "custom:$it" },
                    onCustomUnitChange = { events += "unit:$it" },
                    onContactChange = { events += "contact:$it" },
                    onSubmit = { events += "submit" },
                    onCancelReminder = { events += "cancel:$it" },
                    onEditReminder = { events += "edit:$it" },
                    onTogglePause = { events += "pause:$it" },
                )
            }
        }
        if (openEditor) compose.onNodeWithContentDescription("Add reminder").performClick()
    }

    @Test
    fun `send button needs a title or message`() {
        show(RemindersUiState(presets = presets))

        compose.onNodeWithText("Send now").assertIsNotEnabled()
    }

    @Test
    fun `typing goes into the text state and enables sending`() {
        show(RemindersUiState(presets = presets))

        compose.onNodeWithText("Title").performTextInput("Stay hydrated")
        compose.onNodeWithText("Message").performTextInput("Drink water")

        assertThat(title.text.toString()).isEqualTo("Stay hydrated")
        assertThat(message.text.toString()).isEqualTo("Drink water")
        compose.onNodeWithText("Send now").assertIsEnabled()
    }

    @Test
    fun `send now with content submits`() {
        show(RemindersUiState(presets = presets), titleText = "Hello")

        compose.onNodeWithText("Send now").assertIsEnabled().performClick()

        assertThat(events).containsExactly("submit")
    }

    @Test
    fun `a later time changes the button to set reminder`() {
        val form = ReminderForm(choice = WhenChoice.InMinutes(15))
        show(RemindersUiState(form = form, presets = presets), titleText = "Hi")

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
        val form = ReminderForm(
            choice = WhenChoice.At(8, 0),
            repeat = RepeatChoice.DAYS,
            days = setOf(DayOfWeek.SUNDAY),
        )
        val dayChips = listOf(
            DayChip(DayOfWeek.SUNDAY, "Sun", selected = true),
            DayChip(DayOfWeek.MONDAY, "Mon", selected = false),
        )
        show(
            RemindersUiState(
                form = form,
                pickedTimeLabel = "8:00 AM",
                dayChips = dayChips,
                repeatSummary = "Every Sun",
            ),
            titleText = "Hi",
        )

        compose.onNodeWithText("Every Sun").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Mon").performScrollTo().performClick()
        compose.onNodeWithText("Every day").performScrollTo().performClick()

        assertThat(events).containsExactly("day:MONDAY", "repeat:DAILY").inOrder()
    }

    @Test
    fun `every few hours offers intervals`() {
        val form = ReminderForm(repeat = RepeatChoice.EVERY, everyMinutes = 60)
        val everyChips = listOf(EveryChip(60, "1 h", selected = true), EveryChip(120, "2 h", selected = false))
        show(
            RemindersUiState(form = form, presets = presets, everyChips = everyChips, repeatSummary = "Every 1 h"),
            titleText = "Water",
        )

        compose.onNodeWithText("2 h").performScrollTo().performClick()

        assertThat(events).containsExactly("every:120")
    }

    @Test
    fun `a one-off shows no repeat summary`() {
        show(RemindersUiState(presets = presets))

        compose.onNodeWithText("Never").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Pick at least one day").assertDoesNotExist()
    }

    @Test
    fun `a call button can be attached when people exist`() {
        val mum = Contact(id = 3, name = "Mum", phone = "+8801711123456")
        show(RemindersUiState(presets = presets, contacts = listOf(mum)))

        compose.onNodeWithText("Add a call button (optional)").performScrollTo().performClick()
        compose.onNodeWithText("Mum").performClick()

        assertThat(events).containsExactly("contact:3")
    }

    @Test
    fun `upcoming reminders show when and can be paused`() {
        val upcoming = UpcomingReminder(
            id = 9,
            title = "Medicine",
            message = "After breakfast",
            whenText = "Tomorrow, 8:00 AM",
            repeatText = "Every day",
            contactName = "Mum",
        )
        show(RemindersUiState(presets = presets, upcoming = listOf(upcoming)), openEditor = false)

        compose.onNodeWithText("TOMORROW").assertIsDisplayed()
        compose.onNodeWithText("8:00 AM").assertIsDisplayed()
        compose.onNodeWithText("Every day").assertIsDisplayed()
        compose.onNodeWithText("Call Mum").assertIsDisplayed()
        compose.onNodeWithText("Medicine").performClick()
        compose.onNodeWithContentDescription("Pause Medicine").performClick()

        assertThat(events).containsExactly("edit:9", "pause:9")
    }

    @Test
    fun `nothing scheduled is said plainly`() {
        show(RemindersUiState(presets = presets), openEditor = false)

        compose.onNodeWithText("Nothing scheduled").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a banner asks to allow notifications when they are off`() {
        show(RemindersUiState(presets = presets), notificationsAllowed = false, openEditor = false)

        compose.onNodeWithText("Allow").performClick()

        assertThat(events).containsExactly("allow")
    }

    @Test
    fun `custom repeat exposes editable number and all units`() {
        show(
            RemindersUiState(form = ReminderForm(repeat = RepeatChoice.CUSTOM, customNumber = "")),
            titleText = "Water"
        )
        compose.onNodeWithText("Repeat every").performScrollTo().performTextInput("12")
        assertThat(events).contains("custom:12")
        compose.onNodeWithText("months").performScrollTo().performClick()
        assertThat(events).contains("unit:${RepeatUnit.MONTHS}")
        compose.onNodeWithText("Send now").assertIsNotEnabled()
    }

    @Test
    fun `list opens first and editor can be dismissed without losing the draft`() {
        show(RemindersUiState(presets = presets), openEditor = false)
        compose.onNodeWithText("Nothing scheduled").assertIsDisplayed()
        compose.onNodeWithText("Title").assertDoesNotExist()
        compose.onNodeWithContentDescription("Add reminder").performClick()
        compose.onNodeWithText("Title").performTextInput("A little break")
        compose.onNodeWithContentDescription("Drag handle").performSemanticsAction(SemanticsActions.Dismiss)
        compose.onNodeWithText("Title").assertDoesNotExist()
        compose.onNodeWithContentDescription("Add reminder").performClick()
        compose.onNodeWithText("A little break").assertIsDisplayed()
    }
}
