package com.akib.mycompanion.system

import android.app.Application
import android.content.pm.ShortcutManager
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.domain.model.Contact
import com.akib.mycompanion.presentation.call.CallActivity
import com.akib.mycompanion.presentation.call.CallToken
import com.akib.mycompanion.presentation.shortcuts.AndroidHomeShortcuts
import com.google.common.truth.Truth.assertThat
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** Pinned call icons, through Robolectric's ShortcutManager. */
@RunWith(AndroidJUnit4::class)
class HomeShortcutsTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val manager = app.getSystemService(ShortcutManager::class.java)
    private val shortcuts = AndroidHomeShortcuts(app)
    private val mum = Contact(id = 1, name = "Mum", phone = "+8801711123456")
    private val dad = Contact(id = 2, name = "Dad", phone = "+8801811123456")

    @Before
    fun setUp() {
        shadowOf(manager).setIsRequestPinShortcutSupported(true)
        mockkStatic(ShortcutManagerCompat::class)
    }

    @After
    fun tearDown() = unmockkStatic(ShortcutManagerCompat::class)

    @Test
    fun `a pinned icon opens the call screen with the token`() {
        assertThat(shortcuts.pin(mum)).isTrue()

        val intent = pinned("call-1").intent!!
        assertThat(intent.component!!.className).isEqualTo(CallActivity::class.java.name)
        assertThat(intent.getLongExtra(CallActivity.EXTRA_CONTACT_ID, -1)).isEqualTo(1L)
        assertThat(CallToken.isValid(app, intent.getStringExtra(CallActivity.EXTRA_TOKEN))).isTrue()
    }

    // Robolectric keeps disabled shortcuts in a separate list without clearing isEnabled, so the
    // disable/enable calls are checked on a spy (mockkStatic still runs the real methods).

    @Test
    fun `refresh re-issues icons and greys out people who are gone`() {
        shortcuts.pin(mum)
        shortcuts.pin(dad)

        shortcuts.refreshPinned(listOf(mum.copy(name = "Ammu")))

        assertThat(pinned("call-1").shortLabel.toString()).isEqualTo("Ammu")
        verify { ShortcutManagerCompat.disableShortcuts(app, listOf("call-2"), AndroidHomeShortcuts.REMOVED) }
        verify(exactly = 0) { ShortcutManagerCompat.disableShortcuts(app, listOf("call-1"), any()) }
    }

    @Test
    fun `removing disables the icon and undo enables it`() {
        shortcuts.pin(mum)

        shortcuts.disable(mum.id)
        verify { ShortcutManagerCompat.disableShortcuts(app, listOf("call-1"), AndroidHomeShortcuts.REMOVED) }

        shortcuts.enable(mum)
        verify { ShortcutManagerCompat.enableShortcuts(app, match { list -> list.single().id == "call-1" }) }
    }

    private fun pinned(id: String) = manager.pinnedShortcuts.single { it.id == id }
}
