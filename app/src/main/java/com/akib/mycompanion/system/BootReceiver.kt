package com.akib.mycompanion.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.akib.mycompanion.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Android forgets every alarm on reboot (and on app update); this puts them back. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var events: ReminderEvents

    @Inject
    @field:ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        val pending: PendingResult? = goAsync()
        scope.launch {
            try {
                events.restoreAll()
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
