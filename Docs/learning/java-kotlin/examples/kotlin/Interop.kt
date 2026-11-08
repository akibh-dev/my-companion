fun safeTitle(id: Int): String {
    val raw: String? = LegacyTitles.title(id)
    return raw?.trim()?.takeIf { it.isNotEmpty() } ?: "Reminder"
}

fun main() {
    check(safeTitle(0) == "Reminder")
    check(safeTitle(1) == "Water")
    println(safeTitle(0))
    println(safeTitle(1))
    println("All interoperability checks passed")
}
