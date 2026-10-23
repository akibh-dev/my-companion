package com.akib.mycompanion.presentation.shortcuts

import android.content.Context
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.akib.mycompanion.R
import com.akib.mycompanion.domain.model.Contact
import com.akib.mycompanion.presentation.call.CallActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Home-screen icons that start a WhatsApp call to one person with a single tap. */
interface HomeShortcuts {
    val isSupported: Boolean

    /** Asks the launcher to pin an icon. Returns false if the launcher doesn't support it. */
    fun pin(contact: Contact): Boolean

    /** Refreshes the label of an already-pinned icon after an edit. */
    fun update(contact: Contact)
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

    private fun shortcut(contact: Contact): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(context, shortcutId(contact.id))
            .setShortLabel(contact.name.take(MAX_LABEL))
            .setLongLabel("Call ${contact.name}")
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_call))
            .setIntent(CallActivity.intent(context, contact.id))
            .build()

    companion object {
        private const val MAX_LABEL = 10

        fun shortcutId(contactId: Long) = "call-$contactId"
    }
}
