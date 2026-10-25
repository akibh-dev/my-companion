package com.akib.mycompanion.context

import com.akib.mycompanion.context.domain.ContextSnapshot
import com.akib.mycompanion.context.domain.DndMode
import com.akib.mycompanion.context.domain.Geo
import com.akib.mycompanion.context.domain.Place
import com.akib.mycompanion.context.domain.Rule
import com.akib.mycompanion.context.domain.RuleAction
import com.akib.mycompanion.context.domain.RuleCodec
import com.akib.mycompanion.context.domain.RuleEvaluator
import com.akib.mycompanion.context.domain.SoundMode
import com.akib.mycompanion.context.domain.Trigger
import com.akib.mycompanion.context.domain.VolumeStream
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Test

class RulesDomainTest {

    // Sunday 25 Oct 2026
    private fun at(
        day: Int,
        hour: Int,
        minute: Int = 0
    ) = ContextSnapshot(now = LocalDateTime.of(2026, 10, day, hour, minute))

    private val office = Trigger.TimeWindow(setOf(SUNDAY, MONDAY), LocalTime.of(9, 0), LocalTime.of(17, 0))
    private val night = Trigger.TimeWindow(setOf(THURSDAY), LocalTime.of(22, 0), LocalTime.of(7, 0))

    @Test
    fun `a time window matches on its days between start and end`() {
        assertThat(RuleEvaluator.matches(office, at(25, 9))).isTrue()
        assertThat(RuleEvaluator.matches(office, at(25, 16, 59))).isTrue()
        assertThat(RuleEvaluator.matches(office, at(25, 17))).isFalse()
        assertThat(RuleEvaluator.matches(office, at(24, 10))).isFalse() // Saturday
    }

    @Test
    fun `a window past midnight belongs to the day it started`() {
        assertThat(RuleEvaluator.matches(night, at(29, 23))).isTrue() // Thursday night
        assertThat(RuleEvaluator.matches(night, at(30, 6, 59))).isTrue() // Friday early morning
        assertThat(RuleEvaluator.matches(night, at(30, 7))).isFalse()
        assertThat(RuleEvaluator.matches(night, at(30, 23))).isFalse() // Friday night isn't ticked
    }

    @Test
    fun `other triggers read the snapshot`() {
        val snapshot = at(
            25,
            10
        ).copy(charging = true, bluetoothDevices = setOf("My Car"), wifiSsid = "Office-5G", insidePlaces = setOf(3))

        assertThat(RuleEvaluator.matches(Trigger.Charging(true), snapshot)).isTrue()
        assertThat(RuleEvaluator.matches(Trigger.BluetoothDevice("my car"), snapshot)).isTrue()
        assertThat(RuleEvaluator.matches(Trigger.WifiNetwork("office-5g"), snapshot)).isTrue()
        assertThat(RuleEvaluator.matches(Trigger.AtPlace(3), snapshot)).isTrue()
        assertThat(RuleEvaluator.matches(Trigger.AtPlace(4, inside = false), snapshot)).isTrue()
        assertThat(RuleEvaluator.matches(Trigger.WifiNetwork("Home"), snapshot)).isFalse()
    }

    @Test
    fun `a rule applies only when on, with triggers, all matching`() {
        val rule =
            Rule(
                name = "Office",
                triggers = listOf(office, Trigger.AtPlace(3)),
                actions = listOf(RuleAction.Sound(SoundMode.VIBRATE))
            )
        val there = at(25, 10).copy(insidePlaces = setOf(3))

        assertThat(RuleEvaluator.applies(rule, there)).isTrue()
        assertThat(RuleEvaluator.applies(rule, at(25, 10))).isFalse()
        assertThat(RuleEvaluator.applies(rule.copy(enabled = false), there)).isFalse()
        assertThat(RuleEvaluator.applies(rule.copy(triggers = emptyList()), there)).isFalse()
    }

    @Test
    fun `triggers and actions survive storage, including odd names`() {
        val triggers =
            listOf(
                office,
                night,
                Trigger.Charging(false),
                Trigger.BluetoothDevice("Car | Kit\nv2"),
                Trigger.WifiNetwork("Café"),
                Trigger.AtPlace(7, inside = false)
            )
        val actions = listOf(
            RuleAction.Sound(SoundMode.SILENT),
            RuleAction.DoNotDisturb(DndMode.ALARMS),
            RuleAction.Volume(VolumeStream.ALARM, 80),
            RuleAction.Wifi(false),
            RuleAction.Bluetooth(true),
            RuleAction.Brightness(40),
        )

        assertThat(RuleCodec.decodeTriggers(RuleCodec.encodeTriggers(triggers))).isEqualTo(triggers)
        assertThat(RuleCodec.decodeActions(RuleCodec.encodeActions(actions))).isEqualTo(actions)
        assertThat(RuleCodec.decodeTriggers("FUTURE|x\nCHARGING|true")).containsExactly(Trigger.Charging(true))
    }

    @Test
    fun `places use real distances and accept pasted coordinates`() {
        val dhaka = Place(name = "Dhaka", latitude = 23.8103, longitude = 90.4125, radiusMeters = 150)

        assertThat(Geo.distanceMeters(23.8103, 90.4125, 22.3569, 91.7832)).isWithin(5_000.0).of(213_000.0)
        assertThat(dhaka.contains(23.8110, 90.4125)).isTrue() // ~80 m north
        assertThat(dhaka.contains(23.8130, 90.4125)).isFalse() // ~300 m north
        assertThat(Geo.parse("23.8103, 90.4125")).isEqualTo(23.8103 to 90.4125)
        assertThat(Geo.parse("23.8103 90.4125")).isEqualTo(23.8103 to 90.4125)
        assertThat(Geo.parse("91, 10")).isNull()
        assertThat(Place(name = "", latitude = 0.0, longitude = 0.0).isValid()).isFalse()
    }

    @Test
    fun `fridays are not in the bangladeshi work week`() {
        assertThat(FRIDAY in com.akib.mycompanion.context.ui.Describe.WORK_WEEK_BD).isFalse()
    }
}
