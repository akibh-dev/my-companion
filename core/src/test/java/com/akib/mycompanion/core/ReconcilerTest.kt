package com.akib.mycompanion.core

import com.akib.mycompanion.core.rules.Change
import com.akib.mycompanion.core.rules.Reconciler
import com.akib.mycompanion.core.rules.TakenOver
import com.akib.mycompanion.core.rules.Wanted
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReconcilerTest {

    /** A pretend phone: settings by key, changed by applying the reconciler's changes. */
    private val device = mutableMapOf("ringer" to "normal", "dnd" to "off", "wifi" to "on")
    private var takenOver = TakenOver()

    private fun run(vararg active: Wanted): List<Change> {
        val result = Reconciler.reconcile(active.toList(), takenOver) { device[it] }
        result.changes.forEach { device[it.key] = it.value }
        takenOver = result.takenOver
        return result.changes
    }

    private val office = Wanted(ruleId = 1, order = 1, values = mapOf("ringer" to "vibrate", "wifi" to "on"))
    private val mosque = Wanted(ruleId = 2, order = 0, values = mapOf("ringer" to "silent", "dnd" to "on"))

    @Test
    fun `a rule changes settings and puts them back when it ends`() {
        assertThat(run(office)).containsExactly(Change("ringer", "vibrate", 1))
        assertThat(device["ringer"]).isEqualTo("vibrate")

        assertThat(run()).containsExactly(Change("ringer", "normal", null))
        assertThat(takenOver).isEqualTo(TakenOver())
    }

    @Test
    fun `the rule with the lower order wins, and the other takes over when it ends`() {
        run(office, mosque)
        assertThat(device).containsExactly("ringer", "silent", "dnd", "on", "wifi", "on")

        run(office)
        assertThat(device).containsExactly("ringer", "vibrate", "dnd", "off", "wifi", "on")

        run()
        assertThat(device).containsExactly("ringer", "normal", "dnd", "off", "wifi", "on")
    }

    @Test
    fun `a setting the user changed by hand is left alone when the rule ends`() {
        run(office)
        device["ringer"] = "normal-but-louder"

        assertThat(run()).isEmpty()
        assertThat(device["ringer"]).isEqualTo("normal-but-louder")
    }

    @Test
    fun `nothing changes while the device already matches`() {
        run(office)
        assertThat(run(office)).isEmpty()
    }

    @Test
    fun `a setting that can't be read is still applied but has nothing to restore`() {
        device.remove("dnd")
        run(mosque)
        assertThat(device["dnd"]).isEqualTo("on")

        assertThat(run()).containsExactly(Change("ringer", "normal", null))
    }

    @Test
    fun `with the same order, the older rule (lower id) wins`() {
        val a = Wanted(ruleId = 5, order = 0, values = mapOf("ringer" to "vibrate"))
        val b = Wanted(ruleId = 3, order = 0, values = mapOf("ringer" to "silent"))

        assertThat(run(a, b)).containsExactly(Change("ringer", "silent", 3))
    }

    @Test
    fun `a rule wanting what's already set changes nothing and has nothing to put back`() {
        val wifi = Wanted(ruleId = 9, order = 0, values = mapOf("wifi" to "on"))

        assertThat(run(wifi)).isEmpty()
        assertThat(takenOver.before).containsExactly("wifi", "on")
        assertThat(run()).isEmpty()
        assertThat(takenOver).isEqualTo(TakenOver())
    }

    @Test
    fun `the value from before the first rule is the one restored, through several rules`() {
        run(office)
        run(mosque)
        assertThat(takenOver.before).containsExactly("ringer", "normal", "dnd", "off")

        run()
        assertThat(device).containsExactly("ringer", "normal", "dnd", "off", "wifi", "on")
    }

    @Test
    fun `nothing active and nothing taken over is a no-op`() {
        val result = Reconciler.reconcile(emptyList(), TakenOver()) { error("shouldn't read $it") }

        assertThat(result.changes).isEmpty()
        assertThat(result.takenOver).isEqualTo(TakenOver())
    }
}
