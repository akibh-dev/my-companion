package com.akib.mycompanion.context

import com.akib.mycompanion.context.domain.DndMode
import com.akib.mycompanion.context.domain.Place
import com.akib.mycompanion.context.domain.RuleAction
import com.akib.mycompanion.context.domain.SoundMode
import com.akib.mycompanion.context.domain.Trigger
import com.akib.mycompanion.context.domain.VolumeStream
import com.akib.mycompanion.context.ui.Describe
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.Locale
import org.junit.After
import org.junit.Before
import org.junit.Test

class DescribeTest {

    private lateinit var savedLocale: Locale

    @Before
    fun setUp() {
        savedLocale = Locale.getDefault()
        Locale.setDefault(Locale.UK)
    }

    @After
    fun tearDown() = Locale.setDefault(savedLocale)

    private val places = mapOf(1L to Place(id = 1, name = "Office", latitude = 23.8, longitude = 90.4))

    @Test
    fun `triggers read as plain words`() {
        val workdays = Trigger.TimeWindow(Describe.WORK_WEEK_BD, LocalTime.of(9, 0), LocalTime.of(17, 30))

        assertThat(Describe.trigger(workdays, places)).isEqualTo("Sun–Thu 09:00–17:30")
        assertThat(Describe.trigger(Trigger.Charging(), places)).isEqualTo("While charging")
        assertThat(Describe.trigger(Trigger.Charging(false), places)).isEqualTo("While not charging")
        assertThat(Describe.trigger(Trigger.BluetoothDevice("Car"), places)).isEqualTo("Connected to Car")
        assertThat(Describe.trigger(Trigger.WifiNetwork("Home"), places)).isEqualTo("On Wi-Fi Home")
        assertThat(Describe.trigger(Trigger.AtPlace(1), places)).isEqualTo("At Office")
        assertThat(Describe.trigger(Trigger.AtPlace(1, inside = false), places)).isEqualTo("Away from Office")
    }

    @Test
    fun `a rule pointing at a deleted place says so`() {
        assertThat(Describe.trigger(Trigger.AtPlace(42), places)).isEqualTo("At a deleted place")
    }

    @Test
    fun `actions read as plain words`() {
        assertThat(Describe.action(RuleAction.Sound(SoundMode.NORMAL))).isEqualTo("Sound: Ring")
        assertThat(Describe.action(RuleAction.Sound(SoundMode.SILENT))).isEqualTo("Sound: Silent")
        assertThat(Describe.action(RuleAction.DoNotDisturb(DndMode.TOTAL_SILENCE)))
            .isEqualTo("Do Not Disturb: Total silence")
        assertThat(Describe.action(RuleAction.Volume(VolumeStream.ALARM, 60))).isEqualTo("Alarm volume 60%")
        assertThat(Describe.action(RuleAction.Wifi(true))).isEqualTo("Wi-Fi on")
        assertThat(Describe.action(RuleAction.Bluetooth(false))).isEqualTo("Bluetooth off")
        assertThat(Describe.action(RuleAction.Brightness(25))).isEqualTo("Brightness 25%")
    }

    @Test
    fun `every mode and stream has a name`() {
        assertThat(SoundMode.entries.map(Describe::sound)).containsExactly("Ring", "Vibrate", "Silent").inOrder()
        assertThat(DndMode.entries.map(Describe::dnd))
            .containsExactly("Off", "Priority only", "Alarms only", "Total silence")
            .inOrder()
        assertThat(VolumeStream.entries.map(Describe::stream))
            .containsExactly("Media", "Ring", "Notification", "Alarm")
            .inOrder()
    }

    @Test
    fun `days are summed up when they can be`() {
        assertThat(Describe.days(DayOfWeek.entries.toSet())).isEqualTo("Every day")
        assertThat(Describe.days(Describe.WORK_WEEK_BD)).isEqualTo("Sun–Thu")
        assertThat(Describe.days(DayOfWeek.entries.take(5).toSet())).isEqualTo("Mon–Fri")
        assertThat(Describe.days(setOf(DayOfWeek.SATURDAY, DayOfWeek.FRIDAY))).isEqualTo("Fri, Sat")
        assertThat(Describe.days(emptySet())).isEmpty()
    }
}
