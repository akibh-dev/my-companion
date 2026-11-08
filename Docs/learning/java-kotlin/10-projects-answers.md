# 10 — Practice projects and answers

[Previous](09-testing-debugging.md) · [Index](../README.md) · [Next](11-reference-study-plan.md)

Try each task before reading its answer. This chapter supplies solutions to language drills and a complete console capstone. The Android capstone is a project brief with acceptance criteria, intended for you to build using chapter 07 and the repository examples.

## Java basics answers

Place these methods inside your scratch Java class:

```java
static int hoursToMinutes(int hours) {
    if (hours < 0 || hours > 168) {
        throw new IllegalArgumentException("hours must be 0..168");
    }
    return hours * 60;
}

static int sumPositive(int[] values) {
    int total = 0;
    for (int value : values) {
        if (value > 0) total += value;
    }
    return total;
}
```

hoursToMinutes(0) = 0; (2) = 120; -1 and 169 throw. The supplied sum is 15. Equals compares String contents; == checks references. A new String object has distinct identity from the literal object. Integer division 7/3 = 2; remainder = 1; double division is approximately 2.3333333333333335.

The sum helper assumes its total fits in int. To support arbitrary large totals, change the return/accumulator to long and define overflow policy. Requirements determine the right type.

## Java objects answers

To reject duplicates in MemoryReminderStore.save, check before put:

```java
if (items.containsKey(reminder.id)) {
    throw new IllegalArgumentException("Duplicate reminder ID");
}
items.put(reminder.id, reminder);
```

This is a fragment for the supplied example's method, not a standalone class. A concurrent implementation would need atomic duplicate handling; this learning store is single-threaded.

The snapshot returned by List.copyOf does not grow when the source map grows. Since Reminder's fields are immutable values here, its entries are stable too. For search, trim/lowercase the query with Locale.ROOT, normalize titles the same way, and filter with contains. A fake store fulfills the same interface without real disk IO, allowing the caller's behavior to be tested independently.

## Kotlin basics answers

```kotlin
fun parseMinutes(text: String?): Int? =
    text?.trim()?.toIntOrNull()?.takeIf { it in 0..10_080 }

fun sumPositive(values: List<Int>): Int {
    var total = 0
    for (value in values) {
        if (value > 0) total += value
    }
    return total
}

fun sumPositiveWithCollections(values: List<Int>): Int =
    values.filter { it > 0 }.sum()
```

Elvis replaces null but does not replace an empty string. The three expressions return x, empty string, and x. A val binding cannot be reassigned, but its referenced MutableList still supports mutation. Parsing with toIntOrNull handles malformed and too-large integer text without throwing NumberFormatException.

## Modeling answers

Use sealed alternatives with payloads for distinct failures, then update exhaustive when branches. AssociateBy retains the last value for a duplicate key. GroupBy preserves all elements in each list. A useful count expression is `items.groupingBy { it.enabled }.eachCount()`.

Run this independent Kotlin program to observe shallow copying:

```kotlin
data class Bucket(val titles: MutableList<String>)

fun main() {
    val first = Bucket(mutableListOf("Tea"))
    val second = first.copy()
    second.titles.add("Water")
    check(first.titles == listOf("Tea", "Water"))
    check(first.titles === second.titles)
    println("Both copies share the nested list")
}
```

A nullable return tells you absence occurred. A sealed error result can tell you why. Choose based on what callers need rather than adding error subclasses for every theoretical possibility.

## Console capstone: reminder catalogue

Run [ReminderCli.kt](examples/kotlin/ReminderCli.kt):

```bash
mkdir -p /tmp/my-companion-learning/cli
kotlinc Docs/learning/java-kotlin/examples/kotlin/ReminderCli.kt -include-runtime -d /tmp/my-companion-learning/cli/reminders.jar
java -jar /tmp/my-companion-learning/cli/reminders.jar --self-test
java -jar /tmp/my-companion-learning/cli/reminders.jar
```

Commands: `add 15 Drink water`, `list`, `done 1`, `help`, `quit`. Validation rejects blank titles, bad minute values, and unknown IDs. The complete implementation has a sealed command result, stable IDs, an injected in-memory store, and an explicit parser. It keeps data only for that process and never schedules Android work.

Acceptance checklist:

- Empty input and unknown commands receive useful feedback.
- Zero and 10080 minutes are accepted; -1 and 10081 rejected.
- Two added reminders receive distinct IDs.
- Done marks only the requested reminder.
- Invalid input does not mutate stored reminders.
- EOF exits cleanly; self-test exits nonzero on a failed check.

Extend only after understanding the source. A file-persistence extension must define UTF-8 encoding, a versioned representation, escaping, invalid-file behavior, and safe replacement. Do not serialize unescaped titles by splitting arbitrary text on commas and assume all user text is representable.

## Android capstone: local reminder notebook

Build in a separate learning app. Begin with the chapter 07 preview form and add one increment per branch:

| Increment | Deliverable | Acceptance evidence |
|---|---|---|
| 1 | Input validation and preview | Boundaries/malformed text produce correct feedback |
| 2 | List with stable IDs | Adding two rows and marking one leaves the other intact |
| 3 | Repository interface and fake | ViewModel can be tested without Android storage |
| 4 | Room implementation | Data survives relaunch; tests verify queries |
| 5 | DataStore display setting | Preference survives relaunch and updates UI |
| 6 | Saved form inputs | Rotation and deliberate process recreation restore supported small input state |
| 7 | Optional notification integration | Permission denial is handled; a synthetic notification opens the intended screen |
| 8 | Polish and explanation | Font scaling, accessibility labels, empty/error states, and feature trace are documented |

Keep notification scheduling as a separate advanced increment: select AlarmManager/WorkManager based on actual timing requirements and current Android restrictions. Never promise exact delivery solely because a delay value exists in a model.

Before finishing, explain the state owner, failure handling, coroutine lifetime, persistence boundary, and tests. A feature that works once without that understanding is useful practice but not mastery.

## Final self-assessment

Can you build the console capstone from its requirements without opening its solution? Can you replace its store implementation without changing parsing? Can you explain platform nullability, shallow copy, cancellation, and ViewModel lifetime with examples? Can you trace one real app feature and identify what a unit test cannot prove? Use the answers to choose your next practice session.
