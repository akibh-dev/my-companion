package com.akib.mycompanion.messaging

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.akib.mycompanion.core.log.AppLog
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Started by the system once Notification access is on; passes chat messages to the consumers. */
@AndroidEntryPoint
class MessageListener : NotificationListenerService() {

    @Inject lateinit var consumers: Set<@JvmSuppressWildcards MessageConsumer>

    @Inject lateinit var replies: ReplySender

    @Inject lateinit var samples: MessageSamples

    @Inject lateinit var log: AppLog

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onListenerConnected() {
        samples.setConnected(true)
        log.i(TAG, "connected")
    }

    override fun onListenerDisconnected() {
        samples.setConnected(false)
        log.w(TAG, "disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val app = ChatApp.of(sbn.packageName) ?: return
        val parts = NotificationExtractor.parts(sbn)
        val message = ChatAdapter.ALL.getValue(app).parse(parts)
        samples.record(parts, app, parsed = message != null)
        message ?: return
        NotificationExtractor.replyAction(sbn.notification)?.let { replies.remember(sbn.key, it) }
        consumers.forEach { consumer ->
            scope.launch {
                runCatching { consumer.onMessage(message) }.onFailure { log.e(TAG, "consumer failed", it) }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        replies.forget(sbn.key)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "Messages"
    }
}

object NotificationExtractor {
    fun parts(sbn: StatusBarNotification): NotificationParts {
        val notification = sbn.notification
        val extras = notification.extras
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        return NotificationParts(
            packageName = sbn.packageName,
            key = sbn.key,
            postedAt = sbn.postTime,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            conversationTitle = style?.conversationTitle?.toString(),
            isGroupConversation = style?.isGroupConversation ?: false,
            messages = style?.messages.orEmpty().map { it.person?.name?.toString() to it.text?.toString().orEmpty() },
            isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            isOngoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
            category = notification.category,
            hasReplyAction = replyAction(notification) != null,
        )
    }

    /** The action that takes a typed reply: has a free-form RemoteInput, preferably marked "reply". */
    fun replyAction(notification: Notification): Notification.Action? {
        val withInput = notification.actions.orEmpty().filter { action ->
            action.remoteInputs.orEmpty().any { it.allowFreeFormInput }
        }
        return withInput.firstOrNull {
            it.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY
        } ?: withInput.firstOrNull()
    }
}

/** Sends replies through the chat app's own notification reply action (FR-1.2). */
@Singleton
class ReplySender @Inject constructor(@ApplicationContext private val context: Context, private val log: AppLog) {
    private val actions = java.util.concurrent.ConcurrentHashMap<String, Notification.Action>()

    fun remember(key: String, action: Notification.Action) {
        actions[key] = action
        if (actions.size > MAX_REMEMBERED) actions.keys.take(actions.size - MAX_REMEMBERED).forEach(actions::remove)
    }

    fun forget(key: String) {
        actions.remove(key)
    }

    /** False when the notification is gone or the app refused the reply. */
    fun reply(messageKey: String, text: String): Boolean {
        val action = actions[messageKey] ?: return false
        val inputs = action.remoteInputs.orEmpty()
        val results = Bundle().apply { inputs.forEach { putCharSequence(it.resultKey, text) } }
        val intent = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        RemoteInput.addResultsToIntent(inputs, intent, results)
        return runCatching { action.actionIntent.send(context, 0, intent) }
            .onFailure { log.w("Reply", "the chat app refused the reply", it) }
            .isSuccess
    }

    private companion object {
        const val MAX_REMEMBERED = 100
    }
}

/**
 * The structure (never the text) of recent chat notifications, for the self-test screen: shows per
 * app whether messages are understood and can be replied to (spike S4).
 */
@Singleton
class MessageSamples @Inject constructor() {
    data class Sample(
        val app: ChatApp,
        val time: Long,
        val usesMessagingStyle: Boolean,
        val isGroup: Boolean,
        val hasReplyAction: Boolean,
        val skipped: Boolean,
    )

    private val mutableSamples = MutableStateFlow<List<Sample>>(emptyList())
    val samples: StateFlow<List<Sample>> = mutableSamples.asStateFlow()
    private val mutableConnected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = mutableConnected.asStateFlow()

    fun record(parts: NotificationParts, app: ChatApp, parsed: Boolean) {
        val sample =
            Sample(
                app,
                parts.postedAt,
                parts.messages.isNotEmpty(),
                parts.isGroupConversation,
                parts.hasReplyAction,
                !parsed
            )
        mutableSamples.update { (listOf(sample) + it).take(MAX_SAMPLES) }
    }

    fun setConnected(value: Boolean) {
        mutableConnected.value = value
    }

    private companion object {
        const val MAX_SAMPLES = 50
    }
}
