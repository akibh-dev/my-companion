package dev.akibh.mycompanion.messaging

import android.app.Notification
import android.app.NotificationManager
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.akibh.mycompanion.core.log.AppLog
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

/**
 * Started by the system once Notification access is on. Chat messages go to the [MessageConsumer]s
 * (auto-reply); every app's readable notification goes to the [NoticeConsumer]s (voice mode).
 */
@AndroidEntryPoint
class MessageListener : NotificationListenerService() {

    @Inject lateinit var consumers: Set<@JvmSuppressWildcards MessageConsumer>

    @Inject lateinit var replies: ReplySender

    @Inject lateinit var samples: MessageSamples

    @Inject lateinit var log: AppLog

    @Inject lateinit var observers: Set<@JvmSuppressWildcards NotificationObserver>

    @Inject lateinit var notices: Set<@JvmSuppressWildcards NoticeConsumer>

    @Inject lateinit var apps: InstalledApps

    @Inject lateinit var memory: NoticeMemory

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onListenerConnected() {
        samples.setConnected(true)
        // Already in the shade before (a restart, access switched on): seen, so not read again.
        runCatching { activeNotifications.orEmpty() }.getOrDefault(emptyArray())
            .filter { it.packageName != packageName }
            .forEach { runCatching { memory.remember(NotificationExtractor.parts(it)) } }
        log.i(TAG, "connected")
    }

    override fun onListenerDisconnected() {
        samples.setConnected(false)
        log.w(TAG, "disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        observers.forEach { observer -> deliver("observer") { observer.onPosted(sbn) } }
        if (sbn.packageName == packageName) return
        val parts = NotificationExtractor.parts(sbn, isSilent = isSilent(sbn))
        val app = ChatApp.of(sbn.packageName)
        val message = app?.let { ChatAdapter.ALL.getValue(it).parse(parts) }
        if (app != null) {
            samples.record(parts, app, parsed = message != null)
            if (message != null) {
                NotificationExtractor.replyAction(sbn.notification)?.let { replies.remember(sbn.key, it) }
                consumers.forEach { consumer -> deliver("consumer") { consumer.onMessage(message) } }
            }
        }
        if (notices.isEmpty()) return
        // Re-posts of a notification still in the shade are read only when they add something.
        if (!memory.isNew(parts)) return
        val notice = NoticeParser.parse(parts, apps.label(sbn.packageName), message) ?: return
        notices.forEach { consumer -> deliver("notice consumer") { consumer.onNotice(notice) } }
    }

    private fun deliver(what: String, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                log.e(TAG, "$what failed", failure)
            }
        }
    }

    /** Low and min importance post without sound; the ranking knows the user's per-channel choice. */
    private fun isSilent(sbn: StatusBarNotification): Boolean {
        val ranking = Ranking()
        val found = runCatching { currentRanking?.getRanking(sbn.key, ranking) == true }.getOrDefault(false)
        return found && ranking.importance < NotificationManager.IMPORTANCE_DEFAULT
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        replies.forget(sbn.key)
        memory.forget(sbn.key)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "Messages"
    }
}

/** Optional notification integrations; the debug build uses one for its own spoken test fixture. */
interface NotificationObserver {
    suspend fun onPosted(notification: StatusBarNotification)
}

object NotificationExtractor {
    fun parts(sbn: StatusBarNotification, isSilent: Boolean = false): NotificationParts {
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
            messageTimes = style?.messages.orEmpty().map { it.timestamp },
            // A message by the phone's own user (some apps re-post your reply under a named "You") has
            // no sender, so it reads as your own and is never answered or read out.
            messages = style?.messages.orEmpty().map { message ->
                message.person?.takeUnless { isUser(it, style?.user) }?.name?.toString() to
                    message.text?.toString().orEmpty()
            },
            isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            isOngoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
            category = notification.category,
            hasReplyAction = replyAction(notification) != null,
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            isForegroundService = notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0,
            isMedia = extras.containsKey(Notification.EXTRA_MEDIA_SESSION) ||
                extras.getString(Notification.EXTRA_TEMPLATE).orEmpty().endsWith("MediaStyle"),
            isSilent = isSilent,
        )
    }

    private fun isUser(person: Person, user: Person?): Boolean = when {
        user == null -> false
        person.key != null || user.key != null -> person.key == user.key
        else -> person.name?.toString() == user.name?.toString()
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
    /** Oldest first, so the oldest is dropped when there are too many. Guarded by `this`. */
    private val actions = LinkedHashMap<String, Notification.Action>()

    @Synchronized
    fun remember(key: String, action: Notification.Action) {
        actions.remove(key) // an updated notification counts as the newest
        actions[key] = action
        while (actions.size > MAX_REMEMBERED) actions.remove(actions.keys.first())
    }

    @Synchronized
    fun forget(key: String) {
        actions.remove(key)
    }

    /** False when the notification is gone or the app refused the reply. */
    fun reply(messageKey: String, text: String): Boolean {
        val action = synchronized(this) { actions[messageKey] } ?: return false
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
