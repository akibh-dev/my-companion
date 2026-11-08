package com.akib.mycompanion.presentation.call

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.akib.mycompanion.di.IoDispatcher
import com.akib.mycompanion.domain.usecase.CallContactUseCase
import com.akib.mycompanion.domain.usecase.CallPreparation
import com.akib.mycompanion.domain.usecase.CallResult
import com.akib.mycompanion.presentation.contacts.CallMessages
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Invisible activity behind the home-screen shortcuts and the "Call" button on reminders.
 * Starts the WhatsApp call and closes itself.
 */
@AndroidEntryPoint
class CallActivity : ComponentActivity() {

    @Inject lateinit var callContact: CallContactUseCase

    @Inject
    @IoDispatcher
    lateinit var io: CoroutineDispatcher

    private var consumed = false

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(CONSUMED, consumed)
        super.onSaveInstanceState(outState)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val contactId = intent.getLongExtra(EXTRA_CONTACT_ID, NO_ID)
        if (!CallToken.isValid(this, intent.getStringExtra(EXTRA_TOKEN))) {
            // Another app, or an icon pinned before the token existed that couldn't be refreshed.
            if (contactId != NO_ID) Toast.makeText(applicationContext, OUTDATED_SHORTCUT, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, NO_NOTIFICATION)
        if (notificationId != NO_NOTIFICATION) NotificationManagerCompat.from(this).cancel(notificationId)

        if (contactId == NO_ID) {
            finish()
            return
        }
        if (savedInstanceState?.getBoolean(CONSUMED) == true) {
            finish()
            return
        }
        consumed = true
        lifecycleScope.launch {
            try {
                val preparation = withContext(io) { callContact.prepare(contactId) }
                if (preparation is CallPreparation.Ready) {
                    // Lookup suspends on IO internally; the launch resumes on this foreground/Main context.
                    when (val result = callContact(contactId, preparation.variant)) {
                        is CallResult.ChooseVariant -> startActivity(
                            CallFlowActivity.intent(this@CallActivity, contactId)
                        )
                        else -> CallMessages.forResult(result)?.let {
                            Toast.makeText(applicationContext, it, Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    startActivity(CallFlowActivity.intent(this@CallActivity, contactId))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Toast.makeText(applicationContext, "Couldn't open WhatsApp. Try again from People.", Toast.LENGTH_LONG)
                    .show()
            } finally {
                finish()
            }
        }
    }

    companion object {
        const val EXTRA_CONTACT_ID = "contact_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_TOKEN = "token"
        const val OUTDATED_SHORTCUT = "This shortcut is out of date. Add it again from My Companion."
        private const val CONSUMED = "handoff_consumed"
        private const val NO_ID = -1L
        private const val NO_NOTIFICATION = Int.MIN_VALUE

        fun intent(context: Context, contactId: Long, notificationId: Int? = null): Intent =
            Intent(context, CallActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(EXTRA_CONTACT_ID, contactId)
                .putExtra(EXTRA_TOKEN, CallToken.get(context))
                .apply { if (notificationId != null) putExtra(EXTRA_NOTIFICATION_ID, notificationId) }
    }
}
