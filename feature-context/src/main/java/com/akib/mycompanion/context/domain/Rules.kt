package com.akib.mycompanion.context.domain

import java.net.URLDecoder
import java.net.URLEncoder
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/** When a rule applies. A rule applies while **all** its triggers match (FR-3.2, FR-3.7). */
sealed interface Trigger {
    /** Between [start] and [end] on [days]; a window like 22:00–07:00 runs past midnight. */
    data class TimeWindow(val days: Set<DayOfWeek>, val start: LocalTime, val end: LocalTime) : Trigger

    data class Charging(val charging: Boolean = true) : Trigger

    /** A Bluetooth device with this name is connected (car, headphones). */
    data class BluetoothDevice(val name: String) : Trigger

    /** Connected to this Wi-Fi network. */
    data class WifiNetwork(val ssid: String) : Trigger

    /** Inside (or, with [inside] false, away from) a saved place. */
    data class AtPlace(val placeId: Long, val inside: Boolean = true) : Trigger
}

enum class SoundMode { NORMAL, VIBRATE, SILENT }

enum class DndMode { OFF, PRIORITY, ALARMS, TOTAL_SILENCE }

enum class VolumeStream { MEDIA, RING, NOTIFICATION, ALARM }

/** What a rule changes. Each action owns one device setting ([key]) so rules can be reconciled. */
sealed interface RuleAction {
    val key: String
    val value: String

    data class Sound(val mode: SoundMode) : RuleAction {
        override val key get() = KEY_RINGER
        override val value get() = mode.name
    }

    data class DoNotDisturb(val mode: DndMode) : RuleAction {
        override val key get() = KEY_DND
        override val value get() = mode.name
    }

    data class Volume(val stream: VolumeStream, val percent: Int) : RuleAction {
        override val key get() = "volume.${stream.name}"
        override val value get() = percent.toString()
    }

    /** Apps can't switch Wi-Fi since Android 10 (D2): asks the user with a tap-to-confirm notification. */
    data class Wifi(val on: Boolean) : RuleAction {
        override val key get() = KEY_WIFI
        override val value get() = on.toString()
    }

    /** Like [Wifi]: Android 13+ doesn't let apps switch Bluetooth, so it asks. */
    data class Bluetooth(val on: Boolean) : RuleAction {
        override val key get() = KEY_BLUETOOTH
        override val value get() = on.toString()
    }

    /** Screen brightness, 0–100 %. */
    data class Brightness(val percent: Int) : RuleAction {
        override val key get() = KEY_BRIGHTNESS
        override val value get() = percent.toString()
    }

    companion object {
        const val KEY_RINGER = "ringer"
        const val KEY_DND = "dnd"
        const val KEY_WIFI = "wifi"
        const val KEY_BLUETOOTH = "bluetooth"
        const val KEY_BRIGHTNESS = "brightness"
    }
}

data class Rule(
    val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    /** Lower wins when two active rules change the same setting. */
    val order: Int = 0,
    val triggers: List<Trigger> = emptyList(),
    val actions: List<RuleAction> = emptyList(),
)

/** What the phone is doing right now, as far as rules care. */
data class ContextSnapshot(
    val now: LocalDateTime,
    val charging: Boolean = false,
    val bluetoothDevices: Set<String> = emptySet(),
    val wifiSsid: String? = null,
    val insidePlaces: Set<Long> = emptySet(),
)

object RuleEvaluator {
    /** A rule applies when it's on, has at least one trigger and all of them match. */
    fun applies(rule: Rule, snapshot: ContextSnapshot): Boolean =
        rule.enabled && rule.triggers.isNotEmpty() && rule.triggers.all { matches(it, snapshot) }

    fun matches(trigger: Trigger, snapshot: ContextSnapshot): Boolean = when (trigger) {
        is Trigger.TimeWindow -> inWindow(trigger, snapshot.now)
        is Trigger.Charging -> snapshot.charging == trigger.charging
        is Trigger.BluetoothDevice -> snapshot.bluetoothDevices.any { it.equals(trigger.name, ignoreCase = true) }
        is Trigger.WifiNetwork -> snapshot.wifiSsid?.equals(trigger.ssid, ignoreCase = true) == true
        is Trigger.AtPlace -> (trigger.placeId in snapshot.insidePlaces) == trigger.inside
    }

    private fun inWindow(window: Trigger.TimeWindow, now: LocalDateTime): Boolean {
        val time = now.toLocalTime()
        return if (window.start <= window.end) {
            now.dayOfWeek in window.days && time >= window.start && time < window.end
        } else {
            // Past midnight: the part after midnight belongs to the day the window started.
            (now.dayOfWeek in window.days && time >= window.start) ||
                (now.dayOfWeek.minus(1) in window.days && time < window.end)
        }
    }
}

/**
 * Triggers and actions as text for the database: one per line, "KIND|field|field". Fields are
 * URL-encoded so names with "|" or new lines survive. Unknown lines are skipped, so a rule saved by a
 * newer version still loads.
 */
object RuleCodec {
    fun encodeTriggers(triggers: List<Trigger>): String = triggers.joinToString("\n") { trigger ->
        when (trigger) {
            is Trigger.TimeWindow -> line(
                "TIME",
                trigger.days.sorted().joinToString(",") {
                    it.name
                },
                trigger.start.toString(),
                trigger.end.toString()
            )
            is Trigger.Charging -> line("CHARGING", trigger.charging.toString())
            is Trigger.BluetoothDevice -> line("BLUETOOTH", trigger.name)
            is Trigger.WifiNetwork -> line("WIFI", trigger.ssid)
            is Trigger.AtPlace -> line("PLACE", trigger.placeId.toString(), trigger.inside.toString())
        }
    }

    fun decodeTriggers(text: String): List<Trigger> = fields(text).mapNotNull { f ->
        runCatching {
            when (f[0]) {
                "TIME" -> Trigger.TimeWindow(
                    f[1].split(',').filter { it.isNotBlank() }.map(DayOfWeek::valueOf).toSet(),
                    LocalTime.parse(f[2]),
                    LocalTime.parse(f[3]),
                )
                "CHARGING" -> Trigger.Charging(f[1].toBoolean())
                "BLUETOOTH" -> Trigger.BluetoothDevice(f[1])
                "WIFI" -> Trigger.WifiNetwork(f[1])
                "PLACE" -> Trigger.AtPlace(f[1].toLong(), f[2].toBoolean())
                else -> null
            }
        }.getOrNull()
    }

    fun encodeActions(actions: List<RuleAction>): String = actions.joinToString("\n") { action ->
        when (action) {
            is RuleAction.Sound -> line("SOUND", action.mode.name)
            is RuleAction.DoNotDisturb -> line("DND", action.mode.name)
            is RuleAction.Volume -> line("VOLUME", action.stream.name, action.percent.toString())
            is RuleAction.Wifi -> line("WIFI", action.on.toString())
            is RuleAction.Bluetooth -> line("BLUETOOTH", action.on.toString())
            is RuleAction.Brightness -> line("BRIGHTNESS", action.percent.toString())
        }
    }

    fun decodeActions(text: String): List<RuleAction> = fields(text).mapNotNull { f ->
        runCatching {
            when (f[0]) {
                "SOUND" -> RuleAction.Sound(SoundMode.valueOf(f[1]))
                "DND" -> RuleAction.DoNotDisturb(DndMode.valueOf(f[1]))
                "VOLUME" -> RuleAction.Volume(VolumeStream.valueOf(f[1]), f[2].toInt().coerceIn(0, 100))
                "WIFI" -> RuleAction.Wifi(f[1].toBoolean())
                "BLUETOOTH" -> RuleAction.Bluetooth(f[1].toBoolean())
                "BRIGHTNESS" -> RuleAction.Brightness(f[1].toInt().coerceIn(0, 100))
                else -> null
            }
        }.getOrNull()
    }

    private fun line(vararg fields: String) = fields.joinToString("|") { URLEncoder.encode(it, "UTF-8") }

    private fun fields(text: String): List<List<String>> =
        text.lines().filter { it.isNotBlank() }.map { line -> line.split('|').map { URLDecoder.decode(it, "UTF-8") } }
}
