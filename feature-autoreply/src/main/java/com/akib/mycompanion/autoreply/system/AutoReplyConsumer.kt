package com.akib.mycompanion.autoreply.system

import android.app.NotificationManager
import android.content.Context
import com.akib.mycompanion.autoreply.data.AutoReplyRepository
import com.akib.mycompanion.autoreply.data.AutoReplySettingsStore
import com.akib.mycompanion.autoreply.data.ContactLookup
import com.akib.mycompanion.autoreply.domain.AutoReplyDecider
import com.akib.mycompanion.autoreply.domain.Decision
import com.akib.mycompanion.autoreply.domain.Situation
import com.akib.mycompanion.core.context.ContextStatus
import com.akib.mycompanion.core.log.AppLog
import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.core.settings.SettingsStore
import com.akib.mycompanion.core.time.Clock
import com.akib.mycompanion.messaging.IncomingMessage
import com.akib.mycompanion.messaging.MessageConsumer
import com.akib.mycompanion.messaging.ReplySender
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Answers chat messages while auto-reply is on, through the chat app's own reply button. */
class AutoReplyConsumer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: SettingsStore,
    private val replySettings: AutoReplySettingsStore,
    private val repository: AutoReplyRepository,
    private val contacts: ContactLookup,
    private val status: ContextStatus,
    private val sender: ReplySender,
    private val clock: Clock,
    private val log: AppLog,
) : MessageConsumer {

    override suspend fun onMessage(message: IncomingMessage) = lock.withLock {
        if (!settingsStore.settings.first().isActive(Feature.AUTO_REPLY)) return
        val settings = replySettings.settings.first()
        val now = clock.nowMillis()
        val situation = Situation(now, dndOn(), status.activeRules.value)
        val decision = AutoReplyDecider.decide(
            message = message,
            settings = settings,
            templates = repository.templates.first(),
            situation = situation,
            isContact = runCatching { contacts.isContact(message.sender) }.getOrNull(),
            lastReplyAt = repository.lastReply(message.app.name, message.conversation),
            until = { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) },
        )
        when (decision) {
            is Decision.Reply -> {
                val sent = sender.reply(message.key, decision.text)
                val reason = if (sent) decision.template.name else "the chat app refused the reply"
                record(message, decision.text, decision.template.name, sent, reason)
                log.i(
                    TAG,
                    "${message.app.title}: ${if (sent) "replied" else "couldn't reply"} (${decision.template.name})"
                )
            }
            is Decision.Skip -> {
                // Only skips worth knowing about go in the log you see; the rest would drown it.
                if (decision.reason.startsWith("no template") || decision.reason.contains("reply button")) {
                    record(message, "", "", false, decision.reason)
                }
            }
        }
    }

    private suspend fun record(
        message: IncomingMessage,
        text: String,
        template: String,
        replied: Boolean,
        reason: String,
    ) =
        repository.record(
            clock.nowMillis(),
            message.app.title,
            message.conversation,
            message.sender,
            text,
            template,
            replied,
            reason
        )

    private fun dndOn() =
        context.getSystemService(NotificationManager::class.java).currentInterruptionFilter >
            NotificationManager.INTERRUPTION_FILTER_ALL

    private companion object {
        const val TAG = "AutoReply"

        /** One message at a time, so two quick messages can't both pass the cool-down check. */
        val lock = Mutex()
    }
}
