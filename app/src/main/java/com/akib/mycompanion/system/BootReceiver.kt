package com.akib.mycompanion.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.akib.mycompanion.di.ApplicationScope
import com.akib.mycompanion.presentation.shortcuts.RefreshHomeShortcuts
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Android forgets every alarm on reboot (and on app update); this puts them back.
 * After an update it also refreshes pinned call icons, so ones made by an older version keep working.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var events: ReminderEvents

    @Inject lateinit var refreshHomeShortcuts: RefreshHomeShortcuts

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        val pending: PendingResult? = goAsync()
        scope.launch {
            try {
                events.restoreAll()
                if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) refreshHomeShortcuts()
            } finally {
                pending?.finish()
            }
        }
    }

    companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
        )
    }
}
