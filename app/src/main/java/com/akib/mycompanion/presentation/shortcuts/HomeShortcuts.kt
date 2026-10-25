package com.akib.mycompanion.presentation.shortcuts

import android.content.Context
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.akib.mycompanion.R
import com.akib.mycompanion.domain.model.Contact
import com.akib.mycompanion.domain.repository.ContactRepository
import com.akib.mycompanion.presentation.call.CallActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Home-screen icons that start a WhatsApp call to one person with a single tap. */
interface HomeShortcuts {
    val isSupported: Boolean

    /** Asks the launcher to pin an icon. Returns false if the launcher doesn't support it. */
    fun pin(contact: Contact): Boolean

    /** Refreshes the label of an already-pinned icon after an edit. */
    fun update(contact: Contact)

    /**
     * Re-issues every pinned icon with a current intent (e.g. one made before the call token existed),
     * and greys out icons of people who are no longer saved.
     */
    fun refreshPinned(contacts: List<Contact>)

    /** The person was removed: the launcher greys their icon out instead of it calling them. */
    fun disable(contactId: Long)

    /** Undo of a removal. */
    fun enable(contact: Contact)
}

class AndroidHomeShortcuts @Inject constructor(
    @ApplicationContext private val context: Context,
) : HomeShortcuts {

    override val isSupported: Boolean
        get() = ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    override fun pin(contact: Contact): Boolean {
        if (!isSupported) return false
        return ShortcutManagerCompat.requestPinShortcut(context, shortcut(contact), null)
    }

    override fun update(contact: Contact) {
        runCatching { ShortcutManagerCompat.updateShortcuts(context, listOf(shortcut(contact))) }
    }

    override fun refreshPinned(contacts: List<Contact>) {
        runCatching {
            val pinned = ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)
                .map { it.id }
                .toSet()
            val saved = contacts.filter { shortcutId(it.id) in pinned }
            if (saved.isNotEmpty()) ShortcutManagerCompat.updateShortcuts(context, saved.map(::shortcut))
            val orphans = pinned - saved.map { shortcutId(it.id) }.toSet()
            if (orphans.isNotEmpty()) ShortcutManagerCompat.disableShortcuts(context, orphans.toList(), REMOVED)
        }
    }

    override fun disable(contactId: Long) {
        runCatching { ShortcutManagerCompat.disableShortcuts(context, listOf(shortcutId(contactId)), REMOVED) }
    }

    override fun enable(contact: Contact) {
        runCatching { ShortcutManagerCompat.enableShortcuts(context, listOf(shortcut(contact))) }
    }

    private fun shortcut(contact: Contact): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(context, shortcutId(contact.id))
            .setShortLabel(contact.name.take(MAX_LABEL))
            .setLongLabel("Call ${contact.name}")
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_call))
            .setIntent(CallActivity.intent(context, contact.id))
            .build()

    companion object {
        private const val MAX_LABEL = 10
        const val REMOVED = "This person was removed from My Companion"

        fun shortcutId(contactId: Long) = "call-$contactId"
    }
}

/** Brings pinned icons up to date. Run at app start and after an app update. */
class RefreshHomeShortcuts @Inject constructor(
    private val contacts: ContactRepository,
    private val shortcuts: HomeShortcuts,
) {
    suspend operator fun invoke() = shortcuts.refreshPinned(contacts.observeAll().first())
}
