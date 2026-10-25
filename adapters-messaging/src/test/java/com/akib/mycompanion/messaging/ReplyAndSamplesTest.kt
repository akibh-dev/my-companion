package com.akib.mycompanion.messaging

import android.app.Application
import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.graphics.drawable.Icon
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.core.log.AppLog
import com.akib.mycompanion.core.settings.Feature
import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class ReplyAndSamplesTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val log = AppLog(Files.createTempDirectory("log").toFile(), mirrorToLogcat = false)
    private val sender = ReplySender(app, log)

    private fun pendingIntent(action: String, request: Int = 0) = PendingIntent.getBroadcast(
        app,
        request,
        Intent(action).setPackage(app.packageName),
        PendingIntent.FLAG_MUTABLE,
    )

    private fun action(
        title: String,
        freeForm: Boolean = true,
        reply: Boolean = false,
        intent: PendingIntent = pendingIntent(title),
    ): Notification.Action {
        val input = RemoteInput.Builder("text").setLabel(title).apply {
            if (!freeForm) {
                setChoices(arrayOf("Yes", "No"))
                setAllowFreeFormInput(false)
            }
        }.build()
        val icon = Icon.createWithResource(app, android.R.drawable.sym_action_chat)
        return Notification.Action.Builder(icon, title, intent)
            .addRemoteInput(input)
            .apply { if (reply) setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY) }
            .build()
    }

    private fun notification(vararg actions: Notification.Action): Notification =
        Notification.Builder(app, "chats")
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .apply { actions.forEach { addAction(it) } }
            .build()

    // ---- Picking the reply button ----

    @Test
    fun `the action marked as reply wins over other typed actions`() {
        val other = action("Note")
        val reply = action("Reply", reply = true)

        assertThat(NotificationExtractor.replyAction(notification(other, reply))?.title).isEqualTo("Reply")
    }

    @Test
    fun `without a reply mark, the first action that takes typed text is used`() {
        val quick = action("Quick", freeForm = false)
        val typed = action("Answer")

        assertThat(NotificationExtractor.replyAction(notification(quick, typed))?.title).isEqualTo("Answer")
    }

    @Test
    fun `no typed-text action means no reply button`() {
        val markRead = Notification.Action.Builder(
            Icon.createWithResource(app, android.R.drawable.sym_action_chat),
            "Mark as read",
            pendingIntent("read"),
        ).build()

        assertThat(NotificationExtractor.replyAction(notification(markRead))).isNull()
        assertThat(NotificationExtractor.replyAction(notification(action("Quick", freeForm = false)))).isNull()
        assertThat(NotificationExtractor.replyAction(notification())).isNull()
    }

    // ---- Sending replies ----

    @Test
    fun `a reply goes out through the chat app's own button, with the text filled in`() {
        sender.remember("msg-1", action("Reply", reply = true, intent = pendingIntent("reply")))

        assertThat(sender.reply("msg-1", "On my way")).isTrue()

        val sent = shadowOf(app).broadcastIntents.single { it.action == "reply" }
        assertThat(RemoteInput.getResultsFromIntent(sent)?.getCharSequence("text")).isEqualTo("On my way")
    }

    @Test
    fun `no reply for a message whose notification is unknown or gone`() {
        assertThat(sender.reply("never-seen", "hi")).isFalse()

        sender.remember("msg-2", action("Reply"))
        sender.forget("msg-2")

        assertThat(sender.reply("msg-2", "hi")).isFalse()
    }

    @Test
    fun `a chat app that cancelled its button refuses the reply`() {
        val intent = pendingIntent("cancelled")
        sender.remember("msg-3", action("Reply", intent = intent))
        intent.cancel()

        assertThat(sender.reply("msg-3", "hi")).isFalse()
    }

    @Test
    fun `only the most recent hundred reply buttons are remembered`() {
        repeat(150) { i -> sender.remember("msg-$i", action("Reply", intent = pendingIntent("r$i", request = i))) }

        val usable = (0 until 150).count { i -> sender.reply("msg-$i", "x") }

        assertThat(usable).isEqualTo(100)
    }

    // ---- Self-test samples ----

    private fun parts(messages: Int = 1, group: Boolean = false, reply: Boolean = true, time: Long = 0) =
        NotificationParts(
            packageName = ChatApp.SIGNAL.packageName,
            key = "k",
            postedAt = time,
            title = "Rahim",
            text = "secret text",
            conversationTitle = null,
            isGroupConversation = group,
            messages = List(messages) { "Rahim" to "secret text" },
            isGroupSummary = false,
            isOngoing = false,
            category = null,
            hasReplyAction = reply,
        )

    @Test
    fun `samples keep the shape of a notification, never its text, newest first`() {
        val samples = MessageSamples()

        samples.record(parts(messages = 0, reply = false, time = 1), ChatApp.SIGNAL, parsed = false)
        samples.record(parts(group = true, time = 2), ChatApp.WHATSAPP, parsed = true)

        val (newest, oldest) = samples.samples.value
        assertThat(newest).isEqualTo(MessageSamples.Sample(ChatApp.WHATSAPP, 2, true, true, true, false))
        assertThat(oldest).isEqualTo(MessageSamples.Sample(ChatApp.SIGNAL, 1, false, false, false, true))
        assertThat(samples.samples.value.toString()).doesNotContain("secret")
    }

    @Test
    fun `only the last fifty samples are kept`() {
        val samples = MessageSamples()

        repeat(60) { samples.record(parts(time = it.toLong()), ChatApp.SIGNAL, parsed = true) }

        assertThat(samples.samples.value).hasSize(50)
        assertThat(samples.samples.value.first().time).isEqualTo(59)
        assertThat(samples.samples.value.last().time).isEqualTo(10)
    }

    @Test
    fun `the listener's connection is shown`() {
        val samples = MessageSamples()
        assertThat(samples.connected.value).isFalse()

        samples.setConnected(true)
        assertThat(samples.connected.value).isTrue()

        samples.setConnected(false)
        assertThat(samples.connected.value).isFalse()
    }

    // ---- Permission ----

    @Test
    fun `notification access is asked for auto-reply, in the listener settings`() {
        val requirement = MessagingModule.notificationAccess()

        assertThat(requirement.id).isEqualTo("notification_access")
        assertThat(requirement.feature).isEqualTo(Feature.AUTO_REPLY)
        assertThat(requirement.isGranted(app)).isFalse()
        assertThat(requirement.settingsIntent(app).action).isEqualTo(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        assertThat(requirement.steps.joinToString()).contains("Allow restricted settings")
    }
}
