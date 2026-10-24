package com.akib.mycompanion.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.akib.mycompanion.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Alarm fired, or Snooze / Done tapped on a reminder notification. */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var events: ReminderEvents

    @Inject
    @field:ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_REMINDER_ID, NO_ID)
        if (id == NO_ID) return
        val action = intent.action ?: return
        // goAsync keeps the process alive while the database work finishes (null when called directly in tests).
        val pending: PendingResult? = goAsync()
        scope.launch {
            try {
                when (action) {
                    ACTION_FIRE -> events.fire(id)
                    ACTION_SNOOZE -> events.snooze(id)
                    ACTION_DONE -> events.done(id)
                }
            } finally {
                pending?.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.akib.mycompanion.action.FIRE_REMINDER"
        const val ACTION_SNOOZE = "com.akib.mycompanion.action.SNOOZE_REMINDER"
        const val ACTION_DONE = "com.akib.mycompanion.action.DONE_REMINDER"
        const val EXTRA_REMINDER_ID = "reminder_id"
        private const val NO_ID = -1L

        fun fireIntent(context: Context, reminderId: Long) = intent(context, ACTION_FIRE, reminderId)

        fun snoozeIntent(context: Context, reminderId: Long) = intent(context, ACTION_SNOOZE, reminderId)

        fun doneIntent(context: Context, reminderId: Long) = intent(context, ACTION_DONE, reminderId)

        private fun intent(context: Context, action: String, reminderId: Long): Intent =
            Intent(context, ReminderReceiver::class.java)
                .setAction(action)
                .putExtra(EXTRA_REMINDER_ID, reminderId)
    }
}
