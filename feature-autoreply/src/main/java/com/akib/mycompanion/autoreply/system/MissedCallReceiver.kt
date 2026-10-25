package com.akib.mycompanion.autoreply.system

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CallLog
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.akib.mycompanion.autoreply.data.AutoReplyRepository
import com.akib.mycompanion.autoreply.data.AutoReplySettingsStore
import com.akib.mycompanion.autoreply.data.ContactLookup
import com.akib.mycompanion.autoreply.domain.AutoReplyDecider
import com.akib.mycompanion.core.log.AppLog
import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.core.settings.SettingsStore
import com.akib.mycompanion.core.time.Clock
import dagger.hilt.android.AndroidEntryPoint
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** What counts as a missed call: it rang and went idle without being answered. */
object CallStates {
    // The values of TelephonyManager.EXTRA_STATE_*, written out so this is plain Kotlin (and testable).
    const val RINGING = "RINGING"
    const val OFFHOOK = "OFFHOOK"
    const val IDLE = "IDLE"

    /** The next remembered state, and whether this change completes a missed call. */
    fun next(previous: String?, current: String): Pair<String, Boolean> = when (current) {
        RINGING, OFFHOOK -> current to false
        else -> current to (previous == RINGING)
    }
}

/** Texts people back when you miss their call (FR-1.1), with the same cool-down idea as chats. */
@AndroidEntryPoint
class MissedCallReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsStore: SettingsStore

    @Inject lateinit var replySettings: AutoReplySettingsStore

    @Inject lateinit var repository: AutoReplyRepository

    @Inject lateinit var contacts: ContactLookup

    @Inject lateinit var clock: Clock

    @Inject lateinit var log: AppLog

    override fun onReceive(context: Context, intent: Intent) {
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val (remembered, missed) = CallStates.next(prefs.getString(LAST_STATE, null), state)
        prefs.edit().putString(LAST_STATE, remembered).apply()
        if (!missed) return
        val pending = goAsync()
        scope.launch {
            try {
                handleMissedCall(context)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handleMissedCall(context: Context) {
        if (!settingsStore.settings.first().isActive(Feature.MISSED_CALL_SMS)) return
        if (!granted(context, Manifest.permission.READ_CALL_LOG) || !granted(context, Manifest.permission.SEND_SMS)) {
            log.w(TAG, "missed call, but call log or SMS permission is missing")
            return
        }
        delay(CALL_LOG_DELAY_MILLIS) // the call log entry is written just after the state change
        val number = lastMissedNumber(context) ?: return
        val settings = replySettings.settings.first()
        val name = contacts.nameFor(number)
        val now = clock.nowMillis()
        val textedRecently = (repository.lastReply(SMS, number) ?: 0) > now - settings.cooldownMinutes * MINUTE
        val reason = when {
            settings.missedCallContactsOnly && name == null -> "not a saved contact"
            settings.blockList.any { it.equals(name ?: number, ignoreCase = true) } -> "on your never list"
            textedRecently -> "already texted recently"
            else -> null
        }
        if (reason != null) {
            repository.record(now, SMS, number, name ?: number, "", "", false, reason)
            return
        }
        val text = AutoReplyDecider.fill(settings.missedCallText, name ?: "", SMS, settings) {
            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it))
        }
        val sent = runCatching { sms(context).sendTextMessage(number, null, text, null, null) }
            .onFailure { log.e(TAG, "SMS failed", it) }
            .isSuccess
        repository.record(
            now,
            SMS,
            number,
            name ?: number,
            text,
            "Missed call",
            sent,
            if (sent) "Missed call" else "SMS failed"
        )
        log.i(TAG, if (sent) "texted back after a missed call" else "couldn't text back")
    }

    @SuppressLint("MissingPermission") // checked in handleMissedCall
    private fun lastMissedNumber(context: Context): String? = context.contentResolver.query(
        CallLog.Calls.CONTENT_URI,
        arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE),
        "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.DATE} > ?",
        arrayOf(CallLog.Calls.MISSED_TYPE.toString(), (clock.nowMillis() - RECENT_MILLIS).toString()),
        "${CallLog.Calls.DATE} DESC",
    )?.use { if (it.moveToFirst()) it.getString(0)?.takeIf(String::isNotBlank) else null }

    private fun sms(context: Context): SmsManager = context.getSystemService(SmsManager::class.java)

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "MissedCall"
        const val PREFS = "missed_calls"
        const val LAST_STATE = "last_state"
        const val SMS = "SMS"
        const val MINUTE = 60_000L
        const val CALL_LOG_DELAY_MILLIS = 2_000L
        const val RECENT_MILLIS = 2 * 60_000L
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
