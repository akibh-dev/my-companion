data class Reminder(val id: Long, val title: String, val minutes: Int, val enabled: Boolean = true)

sealed interface Validation {
    data class Valid(val reminder: Reminder) : Validation
    data class Invalid(val reason: String) : Validation
}

fun validate(id: Long, title: String, minutes: Int): Validation = when {
    id <= 0 -> Validation.Invalid("ID must be positive")
    title.isBlank() -> Validation.Invalid("Title is required")
    minutes !in 0..10_080 -> Validation.Invalid("Minutes must be 0..10080")
    else -> Validation.Valid(Reminder(id, title.trim(), minutes))
}

fun Validation.describe(): String = when (this) {
    is Validation.Valid -> "${reminder.title} in ${reminder.minutes} minutes"
    is Validation.Invalid -> reason
}

fun main() {
    val valid = validate(1, " Water ", 15)
    check(valid is Validation.Valid)
    check(valid.reminder.title == "Water")
    check(validate(2, " ", 15) is Validation.Invalid)
    check(validate(2, "Tea", -1) is Validation.Invalid)
    val items = listOf(valid.reminder, Reminder(2, "Tea", 5, enabled = false))
    check(items.filter { it.enabled }.map { it.title } == listOf("Water"))
    check(items.groupBy { it.enabled }.size == 2)
    val byId = (items + Reminder(1, "Updated", 10)).associateBy { it.id }
    check(byId.getValue(1).title == "Updated")
    val changed = valid.reminder.copy(enabled = false)
    check(valid.reminder.enabled)
    check(!changed.enabled)
    println(valid.describe())
    println("All Kotlin modeling checks passed")
}
