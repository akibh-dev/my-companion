package com.akib.mycompanion.core.rules

/**
 * What one active rule wants: device settings by key ("ringer" → "silent", "dnd" → "on").
 * Lower [order] wins when two active rules want the same setting (plan Phase 1: "an order for conflicts").
 */
data class Wanted(val ruleId: Long, val order: Int, val values: Map<String, String>)

/**
 * Settings the rules have taken over. [before] is each setting's value before the first rule changed
 * it, so it can be put back when no rule wants it any more (FR-3.4, "undo when the context ends").
 * [applied] is what we set, to notice when the user changed it by hand in the meantime.
 */
data class TakenOver(
    val before: Map<String, String> = emptyMap(),
    val applied: Map<String, String> = emptyMap(),
)

/** One setting to change, and why. */
data class Change(val key: String, val value: String, val ruleId: Long?)

data class Reconciled(val changes: List<Change>, val takenOver: TakenOver)

/**
 * Works out which device settings to change so the device matches the active rules, and what to
 * put back. Pure, so the whole rule engine can be tested without a phone.
 */
object Reconciler {

    /**
     * @param active rules whose triggers all match right now
     * @param takenOver what earlier runs changed
     * @param read the setting's current value on the device (null when it can't be read)
     */
    fun reconcile(active: List<Wanted>, takenOver: TakenOver, read: (String) -> String?): Reconciled {
        val winner = mutableMapOf<String, Pair<String, Long>>()
        active.sortedWith(compareBy<Wanted> { it.order }.thenBy { it.ruleId }).forEach { rule ->
            rule.values.forEach { (key, value) -> winner.putIfAbsent(key, value to rule.ruleId) }
        }

        val before = takenOver.before.toMutableMap()
        val applied = takenOver.applied.toMutableMap()
        val changes = mutableListOf<Change>()

        winner.forEach { (key, wanted) ->
            val (value, ruleId) = wanted
            val current = read(key)
            if (key !in before && current != null) before[key] = current
            if (current != value) changes += Change(key, value, ruleId)
            applied[key] = value
        }

        // No active rule wants these any more: put them back, unless the user changed them by hand.
        (before.keys - winner.keys).forEach { key ->
            val original = before.getValue(key)
            val current = read(key)
            if (current == applied[key] && current != original) changes += Change(key, original, null)
            before.remove(key)
            applied.remove(key)
        }
        return Reconciled(changes, TakenOver(before, applied))
    }
}
