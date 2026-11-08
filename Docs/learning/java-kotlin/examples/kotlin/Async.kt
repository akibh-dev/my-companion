import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

suspend fun loadCounts(): Int = coroutineScope {
    val reminders = async {
        delay(20)
        2
    }
    val contacts = async {
        delay(10)
        3
    }
    reminders.await() + contacts.await()
}

fun main() = runBlocking {
    check(loadCounts() == 5)
    var producerRuns = 0
    val updates = flow {
        producerRuns++
        emit("Loading")
        delay(10)
        emit("Ready")
        emit("Done")
    }
    check(updates.toList() == listOf("Loading", "Ready", "Done"))
    check(updates.toList().size == 3)
    check(producerRuns == 2)

    val counter = MutableStateFlow(0)
    coroutineScope {
        repeat(2) {
            launch { repeat(100) { counter.update { value -> value + 1 } } }
        }
    }
    check(counter.value == 200)

    val started = CompletableDeferred<Unit>()
    var cleanedUp = false
    val child = launch {
        try {
            started.complete(Unit)
            awaitCancellation()
        } finally {
            cleanedUp = true
        }
    }
    started.await()
    child.cancelAndJoin()
    check(cleanedUp)
    println("All coroutine and Flow checks passed")
}
