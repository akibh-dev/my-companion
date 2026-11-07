package com.akib.mycompanion.domain.model

/** Stable persisted identities. Platform URIs and decoding stay in the data layer. */
sealed interface ReminderSound {
    data object Default : ReminderSound
    data object Silent : ReminderSound
    data class Preset(val id: String) : ReminderSound
    data class Imported(val id: String) : ReminderSound

    fun encode(): String = when (this) {
        Default -> "DEFAULT"
        Silent -> "SILENT"
        is Preset -> "PRESET:$id"
        is Imported -> "IMPORTED:$id"
    }

    companion object {
        fun decode(value: String): ReminderSound = when {
            value == "SILENT" -> Silent
            value.startsWith("PRESET:") -> value.removePrefix("PRESET:")
                .takeIf { id -> SoundPreset.entries.any { it.id == id } }?.let(::Preset) ?: Default
            value.startsWith("IMPORTED:") -> value.removePrefix("IMPORTED:")
                .takeIf { it.matches(Regex("[a-f0-9]{64}")) }?.let(::Imported) ?: Default
            else -> Default
        }
    }
}

enum class SoundPreset(val id: String, val title: String) {
    BELL("bell", "Bell"),
    CHIME("chime", "Chime"),
    BEEP("beep", "Beep"),
}

data class ImportedReminderSound(
    val id: String,
    val name: String,
    val durationMillis: Long,
    val available: Boolean,
)
