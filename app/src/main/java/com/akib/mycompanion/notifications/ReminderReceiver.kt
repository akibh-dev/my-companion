package com.akib.mycompanion.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

/** Fires when a scheduled reminder is due, or when "Snooze" is tapped on one. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, 0)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()

        if (intent.getBooleanExtra(EXTRA_SNOOZE, false)) {
            NotificationManagerCompat.from(context).cancel(id)
            ReminderScheduler.schedule(context, title, message, NotificationHelper.SNOOZE_MINUTES, id)
        } else {
            NotificationHelper.show(context, id, title, message)
        }
    }

    companion object {
        private const val EXTRA_ID = "id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_MESSAGE = "message"
        private const val EXTRA_SNOOZE = "snooze"

        fun intent(
            context: Context,
            id: Int,
            title: String,
            message: String,
            snooze: Boolean = false,
        ): Intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_MESSAGE, message)
            .putExtra(EXTRA_SNOOZE, snooze)
    }
}
