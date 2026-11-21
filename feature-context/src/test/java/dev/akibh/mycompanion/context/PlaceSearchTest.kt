package dev.akibh.mycompanion.context

import android.content.Context
import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.akibh.mycompanion.context.data.ContextDatabase
import dev.akibh.mycompanion.context.data.ContextRepository
import dev.akibh.mycompanion.context.maps.MapsKey
import dev.akibh.mycompanion.context.maps.PlaceSearch
import dev.akibh.mycompanion.context.maps.PlaceSuggestion
import dev.akibh.mycompanion.context.ui.PlaceEditorViewModel
import dev.akibh.mycompanion.context.ui.SearchState
import java.io.IOException
import java.util.concurrent.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** Searching for a place by name in the place editor (D21), with a fake instead of Google Places. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PlaceSearchTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val direct = Executor { it.run() }
    private val db = Room.inMemoryDatabaseBuilder(context, ContextDatabase::class.java)
        .allowMainThreadQueries()
        .setQueryExecutor(direct)
        .setTransactionExecutor(direct)
        .build()
    private val dispatcher = StandardTestDispatcher()

    private class FakeSearch(override val isAvailable: Boolean = true) : PlaceSearch {
        val asked = mutableListOf<String>()
        var fail = false
        override suspend fun suggest(query: String, near: Pair<Double, Double>?): List<PlaceSuggestion> {
            asked += query
            if (fail) throw IOException("offline")
            return listOf(PlaceSuggestion("bcity", "Bashundhara City", "Panthapath, Dhaka"))
        }

        override suspend fun locate(id: String): Pair<Double, Double>? = if (id == "bcity") 23.7509 to 90.3907 else null
    }

    private fun editor(search: PlaceSearch) = PlaceEditorViewModel(
        SavedStateHandle(),
        ContextRepository(db.dao()),
        search
    )

    @Before
    fun main() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun `typing waits for a pause, then shows suggestions for the last text only`() = runTest(dispatcher) {
        val search = FakeSearch()
        val editor = editor(search)

        editor.onQuery("Bas")
        advanceTimeBy(100)
        editor.onQuery("Bashundhara")
        advanceUntilIdle()

        assertThat(search.asked).containsExactly("Bashundhara")
        assertThat(
            (editor.results.value as SearchState.Results).suggestions.single().primary
        ).isEqualTo("Bashundhara City")
    }

    @Test
    fun `choosing a suggestion drops the pin, names the place and closes the list`() = runTest(dispatcher) {
        val editor = editor(FakeSearch())
        editor.onQuery("Bashundhara")
        advanceUntilIdle()

        editor.choose(PlaceSuggestion("bcity", "Bashundhara City", "Panthapath, Dhaka"))
        advanceUntilIdle()

        assertThat(editor.draft.value.point).isEqualTo(23.7509 to 90.3907)
        assertThat(editor.draft.value.name).isEqualTo("Bashundhara City")
        assertThat(editor.query.value).isEmpty()
        assertThat(editor.results.value).isEqualTo(SearchState.Idle)
    }

    @Test
    fun `a name already typed is kept`() = runTest(dispatcher) {
        val editor = editor(FakeSearch())
        editor.onName("Office")

        editor.choose(PlaceSuggestion("bcity", "Bashundhara City", ""))
        advanceUntilIdle()

        assertThat(editor.draft.value.name).isEqualTo("Office")
    }

    @Test
    fun `no internet or an unknown place says so, short text doesn't search`() = runTest(dispatcher) {
        val search = FakeSearch().apply { fail = true }
        val editor = editor(search)

        editor.onQuery("B")
        advanceUntilIdle()
        assertThat(search.asked).isEmpty()
        assertThat(editor.results.value).isEqualTo(SearchState.Idle)

        editor.onQuery("Bashundhara")
        advanceUntilIdle()
        assertThat(editor.results.value).isInstanceOf(SearchState.Failed::class.java)

        editor.choose(PlaceSuggestion("gone", "Closed shop", ""))
        advanceUntilIdle()
        assertThat(editor.results.value).isInstanceOf(SearchState.Failed::class.java)
        assertThat(editor.draft.value.point).isNull()
    }

    @Test
    fun `without a key there is no search`() = runTest(dispatcher) {
        val search = FakeSearch(isAvailable = false)
        val editor = editor(search)

        editor.onQuery("Bashundhara")
        advanceUntilIdle()

        assertThat(editor.canSearch).isFalse()
        assertThat(search.asked).isEmpty()
    }

    @Test
    fun `the key is read from the manifest, an unfilled placeholder counts as missing`() {
        fun withKey(value: String?) {
            shadowOf(context.packageManager).getInternalMutablePackageInfo(context.packageName).applicationInfo!!
                .metaData = value?.let { Bundle().apply { putString("com.google.android.geo.API_KEY", it) } }
        }
        withKey(null)
        assertThat(MapsKey.of(context)).isNull()
        withKey("\${mapsApiKey}")
        assertThat(MapsKey.of(context)).isNull()
        withKey("  ")
        assertThat(MapsKey.of(context)).isNull()
        withKey("AIzaTestKey")
        assertThat(MapsKey.of(context)).isEqualTo("AIzaTestKey")
    }
}
