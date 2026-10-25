package com.akib.mycompanion.context

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Process
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.context.data.ContextDatabase
import com.akib.mycompanion.context.data.ContextRepository
import com.akib.mycompanion.context.domain.RuleAction
import com.akib.mycompanion.context.domain.SoundMode
import com.akib.mycompanion.context.domain.Trigger
import com.akib.mycompanion.context.ui.RuleEditorRoute
import com.akib.mycompanion.context.ui.RuleEditorViewModel
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** Building a rule with the "App in use" trigger in the real editor screen (FR-3.7). */
@RunWith(AndroidJUnit4::class)
class RuleEditorAppTriggerTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val direct = Executor { it.run() }
    private val db = Room.inMemoryDatabaseBuilder(context, ContextDatabase::class.java)
        .allowMainThreadQueries()
        .setQueryExecutor(direct)
        .setTransactionExecutor(direct)
        .build()
    private val repository = ContextRepository(db.dao())

    @Before
    fun installApps() {
        val pm = shadowOf(context.packageManager)
        val launcher = IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        listOf(MAPS to "Maps", "org.example.notes" to "Notes").forEach { (pkg, label) ->
            val component = ComponentName(pkg, "$pkg.Main")
            val info = pm.addActivityIfNotPresent(component)
            info.nonLocalizedLabel = label
            pm.addOrUpdateActivity(info)
            pm.addIntentFilterForActivity(component, launcher)
        }
    }

    @After
    fun tearDown() = db.close()

    private fun usageAccess(mode: Int) = shadowOf(context.getSystemService(AppOpsManager::class.java))
        .setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName, mode)

    private fun showEditor(onDone: () -> Unit = {}) {
        val viewModel = RuleEditorViewModel(SavedStateHandle(), repository)
        compose.setContent { RuleEditorRoute(onDone = onDone, viewModel = viewModel) }
    }

    @Test
    fun `a rule with an app trigger is built and saved`() {
        usageAccess(AppOpsManager.MODE_ALLOWED)
        var done = false
        showEditor { done = true }

        compose.onNodeWithText("Rule name").performTextInput("Navigation")
        compose.onNodeWithText("Add a \"when\"").performClick()
        compose.onNodeWithText("App in use").performClick()
        compose.onNodeWithText("OK").assertIsNotEnabled() // nothing picked yet
        compose.waitUntil(5_000) { compose.onAllNodes(hasTextExactly("Maps")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Maps").performClick()
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText("Using Maps").assertIsDisplayed()

        compose.onNodeWithText("Add a change").performClick()
        compose.onNodeWithText("Sound mode").performClick()
        compose.onNodeWithText("OK").performClick()
        // The editor is a lazy list: Save is composed only once scrolled into view.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Save"))
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5_000) { done }

        val saved = runBlocking { repository.rules.first() }.single()
        assertThat(saved.name).isEqualTo("Navigation")
        assertThat(saved.triggers).containsExactly(Trigger.AppInForeground(MAPS, "Maps"))
        assertThat(saved.actions).containsExactly(RuleAction.Sound(SoundMode.VIBRATE))
    }

    @Test
    fun `the app list can be searched and warns about missing usage access`() {
        usageAccess(AppOpsManager.MODE_IGNORED)
        showEditor()

        compose.onNodeWithText("Add a \"when\"").performClick()
        compose.onNodeWithText("App in use").performClick()
        compose.onNodeWithText("Allow usage access").assertIsDisplayed()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTextExactly("Notes")).fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithText("Search apps").performTextInput("not")
        compose.onNodeWithText("Notes").assertIsDisplayed()
        compose.onNodeWithText("Maps").assertDoesNotExist()
        compose.onNodeWithText("Search apps").performTextInput("zzz")
        compose.onNodeWithText("No app matches").assertIsDisplayed()
    }

    private fun hasTextExactly(text: String) = hasText(text, substring = false)

    private companion object {
        const val MAPS = "com.google.android.apps.maps"
    }
}
