fun delayMillis(minutes: Int): Long {
    require(minutes in 0..40_000) { "Minutes must be 0..40000" }
    return 60_000L * minutes
}

fun normalizedTitle(raw: String?): String {
    val trimmed = raw?.trim()
    return if (trimmed.isNullOrEmpty()) "Reminder" else trimmed
}

fun parseMinutes(text: String?): Int? =
    text?.trim()?.toIntOrNull()?.takeIf { it in 0..10_080 }

fun main() {
    check(delayMillis(5) == 300_000L)
    check(delayMillis(40_000) == 2_400_000_000L)
    check(normalizedTitle(null) == "Reminder")
    check(normalizedTitle("  ") == "Reminder")
    check(normalizedTitle(" Water ") == "Water")
    check(parseMinutes(" 15 ") == 15)
    check(parseMinutes("0") == 0)
    check(parseMinutes("10080") == 10_080)
    for (invalid in listOf(null, "", "abc", "-1", "10081", "9999999999999")) {
        check(parseMinutes(invalid) == null)
    }
    val total = listOf(0, 5, 10, -1).filter { it > 0 }.sum()
    check(total == 15)
    println("${normalizedTitle(" Water ")}: $total positive minutes")
    println("All Kotlin basics checks passed")
}
