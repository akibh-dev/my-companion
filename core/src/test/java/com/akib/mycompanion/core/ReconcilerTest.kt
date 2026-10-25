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
}
