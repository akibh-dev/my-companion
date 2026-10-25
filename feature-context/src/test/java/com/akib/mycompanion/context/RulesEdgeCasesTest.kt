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
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Test

class RulesEdgeCasesTest {

    /** Sunday 25 Oct 2026. */
    private fun at(hour: Int, minute: Int = 0, day: Int = 25) =
        ContextSnapshot(LocalDateTime.of(2026, 10, day, hour, minute))

    private val allDays = DayOfWeek.entries.toSet()

    @Test
    fun `a time window includes its start and excludes its end`() {
        val window = Trigger.TimeWindow(allDays, LocalTime.of(9, 0), LocalTime.of(17, 0))

        assertThat(RuleEvaluator.matches(window, at(9, 0))).isTrue()
        assertThat(RuleEvaluator.matches(window, at(16, 59))).isTrue()
        assertThat(RuleEvaluator.matches(window, at(17, 0))).isFalse()
        assertThat(RuleEvaluator.matches(window, at(8, 59))).isFalse()
    }

    @Test
    fun `a window with no days or no length never matches`() {
        val noDays = Trigger.TimeWindow(emptySet(), LocalTime.of(0, 0), LocalTime.of(23, 59))
        val empty = Trigger.TimeWindow(allDays, LocalTime.of(12, 0), LocalTime.of(12, 0))

        (0..23).forEach { hour ->
            assertThat(RuleEvaluator.matches(noDays, at(hour))).isFalse()
            assertThat(RuleEvaluator.matches(empty, at(hour))).isFalse()
        }
    }

    @Test
    fun `a night window on saturday only runs into sunday morning`() {
        val saturdayNight = Trigger.TimeWindow(setOf(DayOfWeek.SATURDAY), LocalTime.of(22, 0), LocalTime.of(7, 0))

        assertThat(RuleEvaluator.matches(saturdayNight, at(6, 59))).isTrue() // Sunday morning
        assertThat(RuleEvaluator.matches(saturdayNight, at(7, 0))).isFalse()
        assertThat(RuleEvaluator.matches(saturdayNight, at(23, 0))).isFalse() // Sunday night
        assertThat(RuleEvaluator.matches(saturdayNight, at(23, 0, day = 24))).isTrue() // Saturday night
    }

    @Test
    fun `charging false matches while not charging`() {
        assertThat(RuleEvaluator.matches(Trigger.Charging(false), at(10))).isTrue()
        assertThat(RuleEvaluator.matches(Trigger.Charging(false), at(10).copy(charging = true))).isFalse()
    }

    @Test
    fun `wifi needs a connected network, and names ignore case`() {
        val home = Trigger.WifiNetwork("Home")

        assertThat(RuleEvaluator.matches(home, at(10))).isFalse()
        assertThat(RuleEvaluator.matches(home, at(10).copy(wifiSsid = "HOME"))).isTrue()
        assertThat(RuleEvaluator.matches(home, at(10).copy(wifiSsid = "Home-Guest"))).isFalse()
    }

    @Test
    fun `away from a place matches while not inside it`() {
        val away = Trigger.AtPlace(7, inside = false)

        assertThat(RuleEvaluator.matches(away, at(10))).isTrue()
        assertThat(RuleEvaluator.matches(away, at(10).copy(insidePlaces = setOf(7)))).isFalse()
        assertThat(RuleEvaluator.matches(away, at(10).copy(insidePlaces = setOf(8)))).isTrue()
    }

    @Test
    fun `a disabled rule never applies even when its triggers match`() {
        val rule = Rule(name = "x", enabled = false, triggers = listOf(Trigger.Charging(false)))

        assertThat(RuleEvaluator.applies(rule, at(10))).isFalse()
        assertThat(RuleEvaluator.applies(rule.copy(enabled = true), at(10))).isTrue()
    }

    @Test
    fun `each action owns one setting`() {
        assertThat(RuleAction.Sound(SoundMode.SILENT).let { it.key to it.value }).isEqualTo("ringer" to "SILENT")
        assertThat(RuleAction.DoNotDisturb(DndMode.ALARMS).let { it.key to it.value }).isEqualTo("dnd" to "ALARMS")
        assertThat(RuleAction.Volume(VolumeStream.RING, 30).let { it.key to it.value }).isEqualTo("volume.RING" to "30")
        assertThat(RuleAction.Wifi(false).let { it.key to it.value }).isEqualTo("wifi" to "false")
        assertThat(RuleAction.Bluetooth(true).let { it.key to it.value }).isEqualTo("bluetooth" to "true")
        assertThat(RuleAction.Brightness(80).let { it.key to it.value }).isEqualTo("brightness" to "80")
    }

    @Test
    fun `stored percentages are kept between 0 and 100`() {
        val actions = RuleCodec.decodeActions("VOLUME|MEDIA|150\nBRIGHTNESS|-20")

        assertThat(actions).containsExactly(RuleAction.Volume(VolumeStream.MEDIA, 100), RuleAction.Brightness(0))
            .inOrder()
    }

    @Test
    fun `damaged or unknown stored lines are skipped, the rest still loads`() {
        val triggers = RuleCodec.decodeTriggers(
            "TELEPORT|mars\nCHARGING|true\nPLACE|notanumber|true\nTIME|FUNDAY|09:00|10:00\n\nWIFI|Home",
        )
        val actions = RuleCodec.decodeActions("SOUND|LOUD\nDND|PRIORITY\nVOLUME|MEDIA\n")

        assertThat(triggers).containsExactly(Trigger.Charging(true), Trigger.WifiNetwork("Home")).inOrder()
        assertThat(actions).containsExactly(RuleAction.DoNotDisturb(DndMode.PRIORITY))
        assertThat(RuleCodec.decodeTriggers("")).isEmpty()
        assertThat(RuleCodec.decodeActions("")).isEmpty()
    }

    @Test
    fun `empty lists are stored as empty text`() {
        assertThat(RuleCodec.encodeTriggers(emptyList())).isEmpty()
        assertThat(RuleCodec.encodeActions(emptyList())).isEmpty()
    }

    @Test
    fun `a place's edge counts as inside`() {
        val place = Place(name = "Office", latitude = 23.8103, longitude = 90.4125, radiusMeters = 150)
        val north = 23.8103 + 149.0 / 111_195.0
        val further = 23.8103 + 151.0 / 111_195.0

        assertThat(place.contains(23.8103, 90.4125)).isTrue()
        assertThat(place.contains(north, 90.4125)).isTrue()
        assertThat(place.contains(further, 90.4125)).isFalse()
    }

    @Test
    fun `a place needs a name, real coordinates and a sensible radius`() {
        val ok = Place(name = "Home", latitude = 23.8, longitude = 90.4)

        assertThat(ok.isValid()).isTrue()
        assertThat(ok.copy(name = " ").isValid()).isFalse()
        assertThat(ok.copy(latitude = 91.0).isValid()).isFalse()
        assertThat(ok.copy(longitude = -181.0).isValid()).isFalse()
        assertThat(ok.copy(radiusMeters = Place.MIN_RADIUS - 1).isValid()).isFalse()
        assertThat(ok.copy(radiusMeters = Place.MAX_RADIUS + 1).isValid()).isFalse()
        assertThat(ok.copy(radiusMeters = Place.MIN_RADIUS).isValid()).isTrue()
        assertThat(ok.copy(radiusMeters = Place.MAX_RADIUS).isValid()).isTrue()
    }

    @Test
    fun `pasted coordinates must be two numbers on the globe`() {
        assertThat(Geo.parse("  23.8103,90.4125 ")).isEqualTo(23.8103 to 90.4125)
        assertThat(Geo.parse("-33.86 151.21")).isEqualTo(-33.86 to 151.21)
        assertThat(Geo.parse("23.8")).isNull()
        assertThat(Geo.parse("23.8, 90.4, 10")).isNull()
        assertThat(Geo.parse("north, east")).isNull()
        assertThat(Geo.parse("95, 90")).isNull()
        assertThat(Geo.parse("23, 200")).isNull()
        assertThat(Geo.parse("")).isNull()
    }

    @Test
    fun `distance is zero to itself and symmetric`() {
        assertThat(Geo.distanceMeters(23.8, 90.4, 23.8, 90.4)).isEqualTo(0.0)
        val there = Geo.distanceMeters(23.8, 90.4, 22.3, 91.8)
        val back = Geo.distanceMeters(22.3, 91.8, 23.8, 90.4)
        assertThat(there).isWithin(0.001).of(back)
        assertThat(there).isWithin(10_000.0).of(220_000.0)
    }
}
