package com.akib.mycompanion.vault

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ContextWrapper
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.core.sinks.ReminderSink
import com.akib.mycompanion.vault.data.ItemType
import com.akib.mycompanion.vault.data.LinkPreviewer
import com.akib.mycompanion.vault.data.VaultItem
import com.akib.mycompanion.vault.data.VaultState
import com.akib.mycompanion.vault.ui.VaultAutoLock
import com.akib.mycompanion.vault.ui.VaultItemViewModel
import com.akib.mycompanion.vault.ui.VaultViewModel
import com.akib.mycompanion.vault.ui.findActivity
import com.akib.mycompanion.vault.ui.screenLockSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class VaultViewModelsTest {

    private val test = TestVault()
    private val vault = test.vault

    private class FakeReminders(var allowed: Boolean = true) : ReminderSink {
        val set = mutableListOf<Triple<String, String, Long>>()
        override suspend fun remind(title: String, message: String, atMillis: Long): Boolean {
            if (allowed) set += Triple(title, message, atMillis)
            return allowed
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        test.close()
    }

    /**
     * Vault work runs on Dispatchers.IO, so wait for it to show. No withTimeout: runTest would skip
     * the virtual delay at once, and runTest's own real-time timeout covers a hang.
     */
    private suspend fun awaitItems(count: Int): List<VaultItem> = vault.items.first { it.size == count }

    // ---- Auto-lock ----

    @Test
    fun `the vault locks itself a while after you leave`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        vault.setUp()
        val autoLock = VaultAutoLock(vault)

        autoLock.lockSoon(afterMillis = 1_000)
        advanceTimeBy(999)
        runCurrent()
        assertThat(vault.isUnlocked).isTrue()

        advanceTimeBy(2)
        runCurrent()
        assertThat(vault.isUnlocked).isFalse()
        assertThat(vault.state.value).isEqualTo(VaultState.Locked(0))
    }

    @Test
    fun `coming back before the timer ends keeps it open`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        vault.setUp()
        val autoLock = VaultAutoLock(vault)

        autoLock.lockSoon(afterMillis = 1_000)
        advanceTimeBy(500)
        autoLock.keepOpen()
        advanceTimeBy(5_000)
        runCurrent()

        assertThat(vault.isUnlocked).isTrue()
    }

    @Test
    fun `leaving again restarts the timer`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        vault.setUp()
        val autoLock = VaultAutoLock(vault)

        autoLock.lockSoon(afterMillis = 1_000)
        advanceTimeBy(800)
        autoLock.lockSoon(afterMillis = 1_000)
        advanceTimeBy(800)
        runCurrent()
        assertThat(vault.isUnlocked).isTrue()

        advanceTimeBy(300)
        runCurrent()
        assertThat(vault.isUnlocked).isFalse()
    }

    // ---- Vault list ----

    @Test
    fun `a screen lock set in system settings is noticed on coming back`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        test.wrapper.screenLock = false
        val viewModel = VaultViewModel(test.context, vault, LinkPreviewer(OkHttpClient()), VaultAutoLock(vault))
        backgroundScope.launch { viewModel.state.collect {} }
        assertThat(viewModel.state.first { it.vault == VaultState.NoScreenLock }.vault)
            .isEqualTo(VaultState.NoScreenLock)

        test.wrapper.screenLock = true // set in system settings meanwhile
        viewModel.refresh() // the screen resumes

        assertThat(viewModel.state.first { it.vault != VaultState.NoScreenLock }.vault).isEqualTo(VaultState.NotSetUp)
    }

    @Test
    fun `the screen-lock button opens Android's lock chooser`() {
        val intent = screenLockSettings()
        assertThat(intent.action).isEqualTo(DevicePolicyManager.ACTION_SET_NEW_PASSWORD)
        assertThat(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
    }

    @Test
    fun `a note without a title is named after its first line`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        vault.setUp()
        val viewModel = VaultViewModel(test.context, vault, LinkPreviewer(OkHttpClient()), VaultAutoLock(vault))

        viewModel.onAddNote(" ", "Gate code is 4321\nside door")
        viewModel.onAddNote("Wi-Fi", "router")

        val titles = awaitItems(2).map { it.title }
        assertThat(titles).containsExactly("Gate code is 4321", "Wi-Fi")
    }

    @Test
    fun `a long first line is cut for the title`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        vault.setUp()
        val viewModel = VaultViewModel(test.context, vault, LinkPreviewer(OkHttpClient()), VaultAutoLock(vault))

        viewModel.onAddNote("", "x".repeat(100))

        assertThat(awaitItems(1).single().title).hasLength(60)
    }

    @Test
    fun `an unlock that didn't go through says so`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        vault.setUp()
        vault.lock()
        test.wrapper.justUnlocked = false
        val viewModel = VaultViewModel(test.context, vault, LinkPreviewer(OkHttpClient()), VaultAutoLock(vault))
        backgroundScope.launch { viewModel.state.collect {} }

        viewModel.onUnlocked(firstTime = false)

        val state = viewModel.state.first { it.message != null }
        assertThat(state.message).isEqualTo("Unlock didn't go through. Try again.")

        viewModel.onMessage(null)
        assertThat(viewModel.state.first().message).isNull()
    }

    @Test
    fun `the list shows folders, tags and the trash count`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        vault.setUp()
        vault.add(VaultItem(type = ItemType.TEXT, title = "a", folder = "Work", tags = listOf("x", "y")))
        vault.add(VaultItem(type = ItemType.TEXT, title = "b", folder = "Home", tags = listOf("y")))
        vault.add(VaultItem(type = ItemType.TEXT, title = "c", folder = "Old", tags = listOf("z")))
        vault.trash(vault.items.value.first { it.title == "c" })
        val viewModel = VaultViewModel(test.context, vault, LinkPreviewer(OkHttpClient()), VaultAutoLock(vault))
        backgroundScope.launch { viewModel.state.collect {} }

        val state = viewModel.state.first { it.vault == VaultState.Unlocked }

        assertThat(state.folders).containsExactly("Home", "Work").inOrder()
        assertThat(state.tags).containsExactly("x", "y").inOrder()
        assertThat(state.trashCount).isEqualTo(1)
        assertThat(state.items.map { it.title }).containsExactly("a", "b")

        viewModel.onFilter { it.copy(folder = "home") }
        assertThat(viewModel.state.first().items.map { it.title }).containsExactly("b")
    }

    // ---- One item ----

    @Test
    fun `an item screen shows its item and sets reminders with the title only`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        vault.setUp()
        vault.add(VaultItem(type = ItemType.TEXT, title = "Passport renewal", text = "number A1234567"))
        val item = vault.items.value.single()
        val reminders = FakeReminders()
        val viewModel = VaultItemViewModel(
            SavedStateHandle(mapOf("id" to item.id.toString())),
            vault,
            reminders,
            VaultAutoLock(vault),
        )

        assertThat(viewModel.item.value).isEqualTo(item)
        assertThat(viewModel.unlocked.value).isTrue()

        viewModel.remind(item, atMillis = 5_000_000, whenText = "tomorrow 9:00")

        assertThat(reminders.set).containsExactly(Triple("Passport renewal", "From your vault", 5_000_000L))
        assertThat(viewModel.reminderStatus.value).isEqualTo("Reminder set for tomorrow 9:00")
    }

    @Test
    fun `a reminder that can't be set explains why, an untitled item has a stand-in title`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        vault.setUp()
        val reminders = FakeReminders(allowed = false)
        val viewModel = VaultItemViewModel(
            SavedStateHandle(mapOf("id" to "1")),
            vault,
            reminders,
            VaultAutoLock(vault),
        )

        viewModel.remind(VaultItem(type = ItemType.TEXT, title = ""), 1, "now")
        assertThat(viewModel.reminderStatus.value).startsWith("Couldn't set the reminder")

        reminders.allowed = true
        viewModel.remind(VaultItem(type = ItemType.TEXT, title = " "), 1, "now")
        assertThat(reminders.set.single().first).isEqualTo("Vault item")
    }

    @Test
    fun `an unknown id or a locked vault shows nothing`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        vault.setUp()
        vault.add(VaultItem(type = ItemType.TEXT, title = "x"))
        val viewModel = VaultItemViewModel(
            SavedStateHandle(mapOf("id" to "abc")),
            vault,
            FakeReminders(),
            VaultAutoLock(vault),
        )

        assertThat(viewModel.item.value).isNull()

        vault.lock()
        assertThat(viewModel.unlocked.value).isFalse()
    }

    // ---- Finding the activity for the unlock prompt ----

    @Test
    fun `the activity is found through wrapped contexts, never in the app context`() {
        val activity = Robolectric.buildActivity(Activity::class.java).get()

        assertThat(ContextWrapper(ContextWrapper(activity)).findActivity()).isSameInstanceAs(activity)
        assertThat(test.context.findActivity()).isNull()
    }
}
