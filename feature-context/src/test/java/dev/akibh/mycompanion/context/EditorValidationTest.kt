package dev.akibh.mycompanion.context

import android.content.Context
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.akibh.mycompanion.context.data.ContextDatabase
import dev.akibh.mycompanion.context.data.ContextRepository
import dev.akibh.mycompanion.context.domain.Place
import dev.akibh.mycompanion.context.domain.Rule
import dev.akibh.mycompanion.context.domain.RuleAction
import dev.akibh.mycompanion.context.domain.SoundMode
import dev.akibh.mycompanion.context.domain.Trigger
import dev.akibh.mycompanion.context.maps.PlaceSearch
import dev.akibh.mycompanion.context.maps.PlaceSuggestion
import dev.akibh.mycompanion.context.ui.PlaceEditorRoute
import dev.akibh.mycompanion.context.ui.PlaceEditorViewModel
import dev.akibh.mycompanion.context.ui.RuleEditorRoute
import dev.akibh.mycompanion.context.ui.RuleEditorViewModel
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule as JUnitRule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression: a new rule or place opened with a red "give it a name" and a Delete button. Problems
 * now show only after a tap on Save (or a cleared name), and Delete only for something already saved.
 */
@RunWith(AndroidJUnit4::class)
class EditorValidationTest {

    @get:JUnitRule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val direct = Executor { it.run() }
    private val db = Room.inMemoryDatabaseBuilder(context, ContextDatabase::class.java)
        .allowMainThreadQueries()
        .setQueryExecutor(direct)
        .setTransactionExecutor(direct)
        .build()
    private val repository = ContextRepository(db.dao())

    private val noSearch = object : PlaceSearch {
        override val isAvailable = false
        override suspend fun suggest(query: String, near: Pair<Double, Double>?) = emptyList<PlaceSuggestion>()
        override suspend fun locate(id: String): Pair<Double, Double>? = null
    }

    @After
    fun tearDown() = db.close()

    private fun scrollTo(text: String) = compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))

    private fun assertAbsent(text: String) =
        assertThat(compose.onAllNodes(hasText(text)).fetchSemanticsNodes()).isEmpty()

    private fun assertShown(text: String) =
        assertThat(compose.onAllNodes(hasText(text)).fetchSemanticsNodes()).isNotEmpty()

    private fun showRule(id: String) {
        val viewModel = RuleEditorViewModel(SavedStateHandle(mapOf("id" to id)), repository)
        compose.setContent { RuleEditorRoute(onDone = {}, viewModel = viewModel) }
    }

    private fun showPlace(id: String) {
        val viewModel = PlaceEditorViewModel(SavedStateHandle(mapOf("id" to id)), repository, noSearch)
        compose.setContent { PlaceEditorRoute(onDone = {}, viewModel = viewModel) }
    }

    @Test
    fun `a new rule shows no error and no Delete`() {
        showRule("new")

        scrollTo("Save")
        assertAbsent("Give the rule a name")
        assertAbsent("Delete")
    }

    @Test
    fun `tapping Save on an empty rule explains what is missing`() {
        showRule("new")

        scrollTo("Save")
        compose.onNodeWithText("Save").performClick()
        assertShown("Give the rule a name")
    }

    @Test
    fun `clearing a typed name flags it`() {
        showRule("new")

        compose.onNodeWithText("Rule name").performTextInput("Office")
        assertAbsent("Give the rule a name")
        compose.onNodeWithText("Rule name").performTextReplacement("")
        assertShown("Give the rule a name")
    }

    @Test
    fun `an existing rule can be deleted`() {
        val saved = runBlocking {
            repository.save(
                Rule(
                    name = "Office",
                    triggers = listOf(Trigger.Charging(true)),
                    actions = listOf(RuleAction.Sound(SoundMode.VIBRATE)),
                ),
            )
        }
        showRule(saved.id.toString())

        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Office")).fetchSemanticsNodes().isNotEmpty() }
        scrollTo("Delete")
        assertShown("Delete")
        assertAbsent("Give the rule a name")
    }

    @Test
    fun `a new place shows no error and no Delete`() {
        showPlace("new")

        scrollTo("Save")
        assertAbsent("Give the place a name")
        assertAbsent("Delete")

        compose.onNodeWithText("Save").performClick()
        assertShown("Give the place a name")
        assertThat(runBlocking { repository.places.first() }).isEmpty()
    }

    @Test
    fun `an existing place can be deleted`() {
        val saved = runBlocking { repository.save(Place(name = "Home", latitude = 23.81, longitude = 90.41)) }
        showPlace(saved.id.toString())

        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Home")).fetchSemanticsNodes().isNotEmpty() }
        scrollTo("Delete")
        assertShown("Delete")
    }
}
