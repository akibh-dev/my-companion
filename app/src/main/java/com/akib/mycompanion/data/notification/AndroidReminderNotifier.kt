package com.akib.mycompanion.data.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.akib.mycompanion.MainActivity
import com.akib.mycompanion.R
import com.akib.mycompanion.Tab
import com.akib.mycompanion.domain.model.Contact
import com.akib.mycompanion.domain.model.Reminder
import com.akib.mycompanion.domain.port.ReminderNotifier
import com.akib.mycompanion.domain.usecase.SnoozeReminderUseCase
import com.akib.mycompanion.presentation.call.CallActivity
import com.akib.mycompanion.system.ReminderReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderNotifier {

    private val manager = NotificationManagerCompat.from(context)

    // Permission is checked in canPost(); lint can't follow that.
    @SuppressLint("MissingPermission")
    override fun show(reminder: Reminder, contact: Contact?): Boolean {
        if (!canPost(context)) return false
        ensureChannel(context)

        val id = reminder.notificationId
        val snooze = broadcast(id, ReminderReceiver.snoozeIntent(context, reminder.id), ACTION_SNOOZE)
        val done = broadcast(id, ReminderReceiver.doneIntent(context, reminder.id), ACTION_DONE)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(id))
            .addAction(0, "Snooze ${SnoozeReminderUseCase.DEFAULT_SNOOZE_MINUTES} min", snooze)
            .addAction(0, "Done", done)

        if (reminder.message.isNotBlank()) {
            builder.setContentText(reminder.message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.message))
        }
        if (contact != null) {
            builder.addAction(0, "Call ${contact.name}", callIntent(id, contact))
        }

        manager.notify(id, builder.build())
        return true
    }

    override fun cancel(notificationId: Int) = manager.cancel(notificationId)

    private fun openAppIntent(id: Int): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode(id, ACTION_OPEN),
        MainActivity.openTabIntent(context, Tab.REMINDERS),
        FLAGS,
    )

    private fun callIntent(id: Int, contact: Contact): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode(id, ACTION_CALL),
        CallActivity.intent(context, contact.id, notificationId = id),
        FLAGS,
    )

    private fun broadcast(id: Int, intent: Intent, action: Int): PendingIntent =
        PendingIntent.getBroadcast(context, requestCode(id, action), intent, FLAGS)

    companion object {
        const val CHANNEL_ID = "reminders"
        private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        private const val ACTION_OPEN = 0
        private const val ACTION_SNOOZE = 1
        private const val ACTION_DONE = 2
        private const val ACTION_CALL = 3

        /** Distinct request codes per reminder + action, so PendingIntents don't overwrite each other. */
        fun requestCode(notificationId: Int, action: Int): Int = notificationId * 4 + action

        fun canPost(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Custom notifications you create in My Companion"
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
}
