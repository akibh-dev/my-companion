package com.akib.mycompanion.presentation.call

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.akib.mycompanion.domain.usecase.CallContactUseCase
import com.akib.mycompanion.presentation.contacts.CallMessages
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Invisible activity behind the home-screen shortcuts and the "Call" button on reminders.
 * Starts the WhatsApp call and closes itself.
 */
@AndroidEntryPoint
class CallActivity : ComponentActivity() {

    @Inject lateinit var callContact: CallContactUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val contactId = intent.getLongExtra(EXTRA_CONTACT_ID, NO_ID)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, NO_NOTIFICATION)
        if (notificationId != NO_NOTIFICATION) NotificationManagerCompat.from(this).cancel(notificationId)

        if (contactId == NO_ID) {
            finish()
            return
        }
        lifecycleScope.launch {
            val message = CallMessages.forResult(callContact(contactId))
            if (message != null) Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    companion object {
        const val EXTRA_CONTACT_ID = "contact_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        private const val NO_ID = -1L
        private const val NO_NOTIFICATION = Int.MIN_VALUE

        fun intent(context: Context, contactId: Long, notificationId: Int? = null): Intent =
            Intent(context, CallActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(EXTRA_CONTACT_ID, contactId)
                .apply { if (notificationId != null) putExtra(EXTRA_NOTIFICATION_ID, notificationId) }
    }
}
