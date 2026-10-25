package com.akib.mycompanion.autoreply.domain

import com.akib.mycompanion.messaging.ChatApp
import com.akib.mycompanion.messaging.IncomingMessage

/** When a template is used (FR-1.3, reply by context). */
sealed interface ReplyContext {
    /** You switched "Busy" on (with or without an end time). */
    data object Busy : ReplyContext

    /** A context rule with this name is active (e.g. "Driving", "Mosque"). */
    data class WhileRule(val ruleName: String) : ReplyContext

    data object WhileDnd : ReplyContext

    data object Always : ReplyContext

    fun encode(): String = when (this) {
        Busy -> "BUSY"
        is WhileRule -> "RULE:$ruleName"
        WhileDnd -> "DND"
        Always -> "ALWAYS"
    }

    companion object {
        fun decode(text: String): ReplyContext = when {
            text == "BUSY" -> Busy
            text.startsWith("RULE:") -> WhileRule(text.removePrefix("RULE:"))
            text == "DND" -> WhileDnd
            else -> Always
        }
    }
}

/** A reply text (FR-1.7). Placeholders: {name} (sender's first name), {app}, {until} (end of busy). */
data class ReplyTemplate(val id: Long = 0, val name: String, val text: String, val context: ReplyContext)

enum class Audience(val title: String) {
    EVERYONE("Everyone"),
    CONTACTS("Saved contacts only"),
    ALLOW_LIST("Only people I list"),
}

enum class GroupPolicy(val title: String) {
    NEVER("Never in groups"),
    WHEN_MENTIONED("In groups only when mentioned"),
}

data class AutoReplySettings(
    val audience: Audience = Audience.CONTACTS,
    val allowList: List<String> = emptyList(),
    /** Never auto-reply to these, whatever else is set. */
    val blockList: List<String> = emptyList(),
    val groups: GroupPolicy = GroupPolicy.NEVER,
    /** Your name or nickname, to notice mentions in groups. */
    val mentionWords: List<String> = emptyList(),
    /** At most one auto-reply per conversation in this time (FR-1.5). */
    val cooldownMinutes: Int = 30,
    /** Put in front of every auto-reply so nobody mistakes it for you (FR-1.9). */
    val label: String = "[Auto-reply]",
    val apps: Set<ChatApp> = ChatApp.entries.toSet(),
    /** "Busy" switched on; null when off. [busyUntil] null with [busy] true means until switched off. */
    val busy: Boolean = false,
    val busyUntil: Long? = null,
    val missedCallText: String = "Sorry I missed your call. I'll call you back soon.",
    val missedCallContactsOnly: Boolean = true,
)

/** The phone's situation when a message arrives. */
data class Situation(val now: Long, val dndOn: Boolean, val activeRules: Set<String>)

sealed interface Decision {
    data class Reply(val text: String, val template: ReplyTemplate) : Decision

    data class Skip(val reason: String) : Decision
}

object AutoReplyDecider {

    fun isBusy(settings: AutoReplySettings, now: Long) =
        settings.busy && (settings.busyUntil == null || now < settings.busyUntil)

    /** The first template, in your order, whose context is true now. */
    fun template(templates: List<ReplyTemplate>, settings: AutoReplySettings, situation: Situation): ReplyTemplate? =
        templates.firstOrNull { template ->
            when (val c = template.context) {
                ReplyContext.Busy -> isBusy(settings, situation.now)
                is ReplyContext.WhileRule -> situation.activeRules.any { it.equals(c.ruleName, ignoreCase = true) }
                ReplyContext.WhileDnd -> situation.dndOn
                ReplyContext.Always -> true
            }
        }

    /**
     * @param isContact whether the sender is a saved contact; null when unknown (no permission)
     * @param lastReplyAt when this conversation last got an auto-reply
     */
    fun decide(
        message: IncomingMessage,
        settings: AutoReplySettings,
        templates: List<ReplyTemplate>,
        situation: Situation,
        isContact: Boolean?,
        lastReplyAt: Long?,
        until: (Long) -> String,
    ): Decision {
        if (message.app !in settings.apps) return Decision.Skip("${message.app.title} is off")
        if (!message.canReply) return Decision.Skip("${message.app.title} gave no reply button")
        if (settings.blockList.any {
                it.equals(
                    message.sender,
                    ignoreCase = true
                )
            }
        ) {
            return Decision.Skip("on your never list")
        }
        when (settings.audience) {
            Audience.EVERYONE -> Unit
            Audience.CONTACTS -> if (isContact != true) return Decision.Skip("not a saved contact")
            Audience.ALLOW_LIST ->
                if (settings.allowList.none {
                        it.equals(
                            message.sender,
                            ignoreCase = true
                        )
                    }
                ) {
                    return Decision.Skip("not on your list")
                }
        }
        if (message.isGroup) {
            if (settings.groups == GroupPolicy.NEVER) return Decision.Skip("group chat")
            val mentioned = settings.mentionWords.any { word ->
                word.isNotBlank() && message.text.contains(word, ignoreCase = true)
            }
            if (!mentioned) return Decision.Skip("group chat without a mention")
        }
        if (lastReplyAt != null && situation.now - lastReplyAt < settings.cooldownMinutes * MINUTE) {
            return Decision.Skip("already replied in the last ${settings.cooldownMinutes} min")
        }
        val template = template(templates, settings, situation) ?: return Decision.Skip("no template fits right now")
        return Decision.Reply(fill(template.text, message.sender, message.app.title, settings, until), template)
    }

    fun fill(text: String, sender: String, app: String, settings: AutoReplySettings, until: (Long) -> String): String {
        val filled = text
            .replace("{name}", sender.trim().split(' ').first())
            .replace("{app}", app)
            .replace("{until}", settings.busyUntil?.let(until) ?: "later")
        return listOf(settings.label.trim(), filled.trim()).filter { it.isNotEmpty() }.joinToString(" ")
    }

    private const val MINUTE = 60_000L
}
