data class CliReminder(val id: Long, val title: String, val minutes: Int, val done: Boolean = false)

interface CliStore {
    fun all(): List<CliReminder>
    fun add(title: String, minutes: Int): CliReminder
    fun markDone(id: Long): Boolean
}

class MemoryCliStore : CliStore {
    private val items = linkedMapOf<Long, CliReminder>()
    private var nextId = 1L

    override fun all(): List<CliReminder> = items.values.toList()

    override fun add(title: String, minutes: Int): CliReminder {
        require(title.isNotBlank())
        require(minutes in 0..10_080)
        val reminder = CliReminder(nextId++, title.trim(), minutes)
        items[reminder.id] = reminder
        return reminder
    }

    override fun markDone(id: Long): Boolean {
        val current = items[id] ?: return false
        items[id] = current.copy(done = true)
        return true
    }
}

sealed interface CommandResult {
    data class Message(val text: String) : CommandResult
    data object Quit : CommandResult
}

class ReminderCommands(private val store: CliStore) {
    fun execute(line: String): CommandResult {
        val parts = line.trim().split(Regex("\\s+"), limit = 3)
        return when (parts.firstOrNull()) {
            "add" -> {
                val minutes = parts.getOrNull(1)?.toIntOrNull()
                val title = parts.getOrNull(2)?.trim().orEmpty()
                if (minutes == null || minutes !in 0..10_080 || title.isBlank()) {
                    CommandResult.Message("Use: add <minutes 0..10080> <title>")
                } else {
                    val reminder = store.add(title, minutes)
                    CommandResult.Message("Added ${reminder.id}: ${reminder.title}")
                }
            }
            "list" -> {
                if (parts.size != 1) return CommandResult.Message("Use: list")
                val text = store.all().joinToString("\n") {
                    val status = if (it.done) "done" else "pending"
                    "${it.id}: ${it.title} | ${it.minutes} min | $status"
                }.ifEmpty { "No reminders" }
                CommandResult.Message(text)
            }
            "done" -> {
                val id = parts.getOrNull(1)?.toLongOrNull()
                if (parts.size != 2 || id == null || id <= 0) {
                    CommandResult.Message("Use: done <positive ID>")
                } else if (store.markDone(id)) {
                    CommandResult.Message("Marked $id done")
                } else {
                    CommandResult.Message("Unknown ID: $id")
                }
            }
            "quit" -> if (parts.size == 1) CommandResult.Quit else CommandResult.Message("Use: quit")
            "help" -> CommandResult.Message("Commands: add <minutes> <title>, list, done <id>, help, quit")
            else -> CommandResult.Message("Unknown command. Type help.")
        }
    }
}

fun selfTest() {
    val store = MemoryCliStore()
    val commands = ReminderCommands(store)
    check(commands.execute("list") == CommandResult.Message("No reminders"))
    for (invalid in listOf("", "add", "add abc Water", "add -1 Water", "add 10081 Water", "add 2   ")) {
        check(commands.execute(invalid) is CommandResult.Message)
        check(store.all().isEmpty())
    }
    check(commands.execute("add 0 Water") == CommandResult.Message("Added 1: Water"))
    check(commands.execute("add 10080 Tea break") == CommandResult.Message("Added 2: Tea break"))
    check(store.all().map { it.id } == listOf(1L, 2L))
    check(commands.execute("done 999") == CommandResult.Message("Unknown ID: 999"))
    check(store.all().none { it.done })
    check(commands.execute("done 1") == CommandResult.Message("Marked 1 done"))
    check(store.all().first().done)
    check(!store.all().last().done)
    check(commands.execute("done 2 extra") == CommandResult.Message("Use: done <positive ID>"))
    check(commands.execute("quit") == CommandResult.Quit)
    println("All CLI checks passed")
}

fun main(args: Array<String>) {
    if (args.contentEquals(arrayOf("--self-test"))) {
        selfTest()
        return
    }
    val commands = ReminderCommands(MemoryCliStore())
    println("Reminder catalogue. Type help for commands. Data lasts for this process only.")
    while (true) {
        val line = readlnOrNull() ?: break
        when (val result = commands.execute(line)) {
            is CommandResult.Message -> println(result.text)
            CommandResult.Quit -> return
        }
    }
}
