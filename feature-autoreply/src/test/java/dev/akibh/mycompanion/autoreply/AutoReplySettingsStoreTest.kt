package dev.akibh.mycompanion.autoreply

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.akibh.mycompanion.autoreply.data.AutoReplySettingsStore
import dev.akibh.mycompanion.autoreply.domain.Audience
import dev.akibh.mycompanion.autoreply.domain.AutoReplySettings
import dev.akibh.mycompanion.autoreply.domain.GroupPolicy
import dev.akibh.mycompanion.messaging.ChatApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

/** One test on purpose: the DataStore is one per process, so a second test would see this one's file. */
@RunWith(AndroidJUnit4::class)
class AutoReplySettingsStoreTest {

    @Test
    fun `settings start at the defaults, are saved whole and can end busy`() = runTest {
        val store = AutoReplySettingsStore(ApplicationProvider.getApplicationContext<Context>())
        assertThat(store.settings.first()).isEqualTo(AutoReplySettings())

        val changed = AutoReplySettings(
            audience = Audience.ALLOW_LIST,
            allowList = listOf("Rahim", "Karim"),
            blockList = listOf("Boss"),
            groups = GroupPolicy.WHEN_MENTIONED,
            mentionWords = listOf("akib", "bhai"),
            cooldownMinutes = 45,
            label = "[Bot]",
            apps = setOf(ChatApp.WHATSAPP, ChatApp.SIGNAL),
            busy = true,
            busyUntil = 5_000_000L,
            missedCallText = "Driving, will call back",
            missedCallContactsOnly = false,
        )
        store.update { changed }

        // Lists come back sorted, since they're stored as sets.
        assertThat(store.settings.first()).isEqualTo(
            changed.copy(allowList = listOf("Karim", "Rahim"), mentionWords = listOf("akib", "bhai")),
        )

        store.update { it.copy(busy = false, busyUntil = null, apps = emptySet()) }
        val after = store.settings.first()
        assertThat(after.busy).isFalse()
        assertThat(after.busyUntil).isNull()
        assertThat(after.apps).isEmpty()
        assertThat(after.label).isEqualTo("[Bot]")
    }
}
