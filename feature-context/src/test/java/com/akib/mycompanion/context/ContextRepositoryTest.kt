package com.akib.mycompanion.context

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.context.data.ContextDatabase
import com.akib.mycompanion.context.data.ContextRepository
import com.akib.mycompanion.context.data.TakenOverStore
import com.akib.mycompanion.context.domain.Place
import com.akib.mycompanion.context.domain.Rule
import com.akib.mycompanion.context.domain.RuleAction
import com.akib.mycompanion.context.domain.SoundMode
import com.akib.mycompanion.context.domain.Trigger
import com.akib.mycompanion.context.domain.VolumeStream
import com.akib.mycompanion.core.rules.TakenOver
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContextRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, ContextDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val repository = ContextRepository(db.dao())

    @After
    fun tearDown() = db.close()

    private fun rule(name: String) = Rule(
        name = name,
        triggers = listOf(Trigger.Charging()),
        actions = listOf(RuleAction.Sound(SoundMode.VIBRATE)),
    )

    @Test
    fun `new rules go last in the conflict order`() = runTest {
        val first = repository.save(rule("Office"))
        val second = repository.save(rule("Mosque"))
        val third = repository.save(rule("Night").copy(order = 99))

        assertThat(listOf(first.order, second.order, third.order)).containsExactly(0, 1, 2).inOrder()
        assertThat(repository.rules.first().map { it.name }).containsExactly("Office", "Mosque", "Night").inOrder()
    }

    @Test
    fun `a saved rule keeps its triggers and actions`() = runTest {
        val saved = repository.save(
            rule("Car").copy(
                triggers = listOf(Trigger.BluetoothDevice("Car | Audio"), Trigger.AtPlace(3, inside = false)),
                actions = listOf(RuleAction.Volume(VolumeStream.MEDIA, 70)),
            ),
        )

        val loaded = repository.rule(saved.id)!!
        assertThat(loaded).isEqualTo(saved)
        assertThat(repository.rule(9_999)).isNull()
    }

    @Test
    fun `editing a rule keeps its id and order`() = runTest {
        val saved = repository.save(rule("Office"))

        repository.save(saved.copy(name = "Office hours", enabled = false))

        val rules = repository.rules.first()
        assertThat(rules).hasSize(1)
        assertThat(rules.single().name).isEqualTo("Office hours")
        assertThat(rules.single().enabled).isFalse()
        assertThat(rules.single().id).isEqualTo(saved.id)
    }

    @Test
    fun `moving a rule swaps it with its neighbour`() = runTest {
        repository.save(rule("A"))
        repository.save(rule("B"))
        repository.save(rule("C"))
        var rules = repository.rules.first()

        repository.move(rules, rules[2], up = true)
        rules = repository.rules.first()
        assertThat(rules.map { it.name }).containsExactly("A", "C", "B").inOrder()

        repository.move(rules, rules[0], up = false)
        rules = repository.rules.first()
        assertThat(rules.map { it.name }).containsExactly("C", "A", "B").inOrder()
    }

    @Test
    fun `the first rule can't move up nor the last one down`() = runTest {
        repository.save(rule("A"))
        repository.save(rule("B"))
        val rules = repository.rules.first()

        repository.move(rules, rules.first(), up = true)
        repository.move(rules, rules.last(), up = false)

        assertThat(repository.rules.first()).isEqualTo(rules)
    }

    @Test
    fun `deleting a rule removes only that one`() = runTest {
        val a = repository.save(rule("A"))
        repository.save(rule("B"))

        repository.deleteRule(a.id)

        assertThat(repository.rules.first().map { it.name }).containsExactly("B")
    }

    @Test
    fun `places are sorted by name and keep their wifi names`() = runTest {
        val office = Place(name = "office", latitude = 23.75, longitude = 90.39, wifiNames = listOf("Office-5G", " "))
        repository.save(office)
        val home = repository.save(Place(name = "Home", latitude = 23.81, longitude = 90.41, radiusMeters = 200))

        val places = repository.places.first()
        assertThat(places.map { it.name }).containsExactly("Home", "office").inOrder()
        assertThat(places.last().wifiNames).containsExactly("Office-5G")
        assertThat(repository.place(home.id)).isEqualTo(home)

        repository.save(home.copy(radiusMeters = 300, wifiNames = listOf("Home", "Home-Guest")))
        assertThat(repository.place(home.id)!!.wifiNames).containsExactly("Home", "Home-Guest").inOrder()

        repository.deletePlace(home.id)
        assertThat(repository.place(home.id)).isNull()
    }

    @Test
    fun `history is newest first and kept for 30 days`() = runTest {
        val day = 24 * 60 * 60 * 1000L
        repository.recordRun(0, "Office", "ringer → VIBRATE")
        repository.recordRun(5 * day, "Mosque", "ringer → SILENT")
        repository.recordRun(31 * day, "Restored", "ringer → NORMAL")

        val runs = repository.runs.first()
        assertThat(runs.map { it.ruleName }).containsExactly("Restored", "Mosque").inOrder()
        assertThat(runs.first().changes).isEqualTo("ringer → NORMAL")
    }

    @Test
    fun `taken-over settings survive a restart, odd characters included`() = runTest {
        val store = TakenOverStore(context)
        assertThat(store.load()).isEqualTo(TakenOver())

        val takenOver = TakenOver(
            before = mapOf("ringer" to "NORMAL", "a&b=c" to "x=y&z", "volume.MEDIA" to "40"),
            applied = mapOf("ringer" to "SILENT", "a&b=c" to "ঢাকা"),
        )
        store.save(takenOver)

        assertThat(TakenOverStore(context).load()).isEqualTo(takenOver)

        store.save(TakenOver())
        assertThat(TakenOverStore(context).load()).isEqualTo(TakenOver())
    }
}
