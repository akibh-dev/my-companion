package com.akib.mycompanion.context.ui

import com.akib.mycompanion.context.domain.DndMode
import com.akib.mycompanion.context.domain.Place
import com.akib.mycompanion.context.domain.RuleAction
import com.akib.mycompanion.context.domain.SoundMode
import com.akib.mycompanion.context.domain.Trigger
import com.akib.mycompanion.context.domain.VolumeStream
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Triggers and actions in plain words for the rule list and editor. */
object Describe {
    fun trigger(trigger: Trigger, places: Map<Long, Place>): String = when (trigger) {
        is Trigger.TimeWindow -> "${days(trigger.days)} ${trigger.start}–${trigger.end}"
        is Trigger.Charging -> if (trigger.charging) "While charging" else "While not charging"
        is Trigger.BluetoothDevice -> "Connected to ${trigger.name}"
        is Trigger.WifiNetwork -> "On Wi-Fi ${trigger.ssid}"
        is Trigger.AtPlace -> {
            val name = places[trigger.placeId]?.name ?: "a deleted place"
            if (trigger.inside) "At $name" else "Away from $name"
        }
    }

    fun action(action: RuleAction): String = when (action) {
        is RuleAction.Sound -> "Sound: ${sound(action.mode)}"
        is RuleAction.DoNotDisturb -> "Do Not Disturb: ${dnd(action.mode)}"
        is RuleAction.Volume -> "${stream(action.stream)} volume ${action.percent}%"
        is RuleAction.Wifi -> if (action.on) "Wi-Fi on" else "Wi-Fi off"
        is RuleAction.Bluetooth -> if (action.on) "Bluetooth on" else "Bluetooth off"
        is RuleAction.Brightness -> "Brightness ${action.percent}%"
    }

    fun sound(mode: SoundMode) = when (mode) {
        SoundMode.NORMAL -> "Ring"
        SoundMode.VIBRATE -> "Vibrate"
        SoundMode.SILENT -> "Silent"
    }

    fun dnd(mode: DndMode) = when (mode) {
        DndMode.OFF -> "Off"
        DndMode.PRIORITY -> "Priority only"
        DndMode.ALARMS -> "Alarms only"
        DndMode.TOTAL_SILENCE -> "Total silence"
    }

    fun stream(stream: VolumeStream) = when (stream) {
        VolumeStream.MEDIA -> "Media"
        VolumeStream.RING -> "Ring"
        VolumeStream.NOTIFICATION -> "Notification"
        VolumeStream.ALARM -> "Alarm"
    }

    /** "Every day", "Sun–Thu", "Fri, Sat". */
    fun days(days: Set<DayOfWeek>): String {
        if (days.size == 7) return "Every day"
        if (days == WORK_WEEK_BD) return "Sun–Thu"
        if (days == WORK_WEEK_EUROPE) return "Mon–Fri"
        return days.sorted().joinToString(", ") { short(it) }
    }

    fun short(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, Locale.getDefault())

    private val WORK_WEEK_EUROPE =
        setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

    /** Bangladesh's work week: weekend is Friday and Saturday. */
    val WORK_WEEK_BD =
        setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)
}
