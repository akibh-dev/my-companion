package com.akib.mycompanion.messaging

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** Adapters against saved notification shapes (texts are made up, plan §6: no real messages). */
@RunWith(AndroidJUnit4::class)
class ChatAdaptersTest {

    private fun parts(
        app: ChatApp,
        title: String? = null,
        text: String? = null,
        messages: List<Pair<String?, String>> = emptyList(),
        conversation: String? = null,
        group: Boolean = false,
        summary: Boolean = false,
        ongoing: Boolean = false,
        category: String? = null,
    ) = NotificationParts(
        app.packageName, "key", 1_000, title, text, conversation, group, messages, summary, ongoing, category,
        hasReplyAction = true,
    )

    private fun parse(parts: NotificationParts) = ChatAdapter.ALL.getValue(ChatApp.of(parts.packageName)!!).parse(parts)

    @Test
    fun `a one-to-one MessagingStyle message`() {
        val message = parse(
            parts(ChatApp.WHATSAPP, messages = listOf("Rahim" to "earlier", "Rahim" to "Are you coming?"))
        )!!

        assertThat(message.sender).isEqualTo("Rahim")
        assertThat(message.text).isEqualTo("Are you coming?")
        assertThat(message.conversation).isEqualTo("Rahim")
        assertThat(message.isGroup).isFalse()
    }

    @Test
    fun `a group message keeps the group as the conversation`() {
        val message = parse(
            parts(ChatApp.SIGNAL, messages = listOf("Karim" to "Dinner at 8"), conversation = "Family", group = true)
        )!!

        assertThat(message.isGroup).isTrue()
        assertThat(message.conversation).isEqualTo("Family")
        assertThat(message.sender).isEqualTo("Karim")
    }

    @Test
    fun `your own message from another device is ignored`() {
        assertThat(parse(parts(ChatApp.TELEGRAM, messages = listOf("Rahim" to "hi", null to "hello back")))).isNull()
    }

    @Test
    fun `summaries, status lines and calls are not messages`() {
        assertThat(parse(parts(ChatApp.WHATSAPP, title = "WhatsApp", text = "5 new messages"))).isNull()
        assertThat(parse(parts(ChatApp.WHATSAPP, title = "Rahim", text = "3 messages from 2 chats"))).isNull()
        assertThat(parse(parts(ChatApp.WHATSAPP, title = "Rahim", text = "Incoming voice call"))).isNull()
        assertThat(
            parse(parts(ChatApp.SIGNAL, title = "Signal", text = "Background connection enabled", ongoing = true))
        ).isNull()
        assertThat(parse(parts(ChatApp.MESSENGER, title = "Rahim", text = "hi", summary = true))).isNull()
        assertThat(parse(parts(ChatApp.GOOGLE_MESSAGES, title = "Rahim", text = "calling", category = "call"))).isNull()
    }

    @Test
    fun `whatsapp's old group titles are understood`() {
        val colon = parse(parts(ChatApp.WHATSAPP, title = "Family: Mum", text = "Call me"))!!
        val at = parse(parts(ChatApp.WHATSAPP, title = "Mum @ Family", text = "Call me"))!!

        listOf(colon, at).forEach {
            assertThat(it.isGroup).isTrue()
            assertThat(it.sender).isEqualTo("Mum")
            assertThat(it.conversation).isEqualTo("Family")
        }
    }

    @Test
    fun `telegram groups without MessagingStyle put the sender in the text`() {
        val message = parse(parts(ChatApp.TELEGRAM, title = "Office", text = "Nadia: meeting moved", group = true))!!

        assertThat(message.sender).isEqualTo("Nadia")
        assertThat(message.text).isEqualTo("meeting moved")
        assertThat(message.conversation).isEqualTo("Office")
    }

    @Test
    fun `real notifications are reduced to parts with their reply action`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val reply = NotificationCompat.Action.Builder(
            0,
            "Reply",
            PendingIntent.getBroadcast(context, 0, Intent("reply"), PendingIntent.FLAG_MUTABLE),
        ).addRemoteInput(RemoteInput.Builder("text").setLabel("Reply").build())
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .build()
        val style = NotificationCompat.MessagingStyle(Person.Builder().setName("Me").build())
            .setConversationTitle("Family")
            .setGroupConversation(true)
            .addMessage("Dinner at 8", 1_000, Person.Builder().setName("Karim").build())
        val notification: Notification = NotificationCompat.Builder(context, "chats")
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setStyle(style)
            .addAction(reply)
            .build()
        val sbn =
            StatusBarNotification(
                ChatApp.WHATSAPP.packageName, ChatApp.WHATSAPP.packageName, 7, "tag", 0, 0, 0, notification,
                Process.myUserHandle(), 1_000,
            )

        val parts = NotificationExtractor.parts(sbn)

        assertThat(parts.messages).containsExactly("Karim" to "Dinner at 8")
        assertThat(parts.isGroupConversation).isTrue()
        assertThat(parts.conversationTitle).isEqualTo("Family")
        assertThat(parts.hasReplyAction).isTrue()
        assertThat(ChatAdapter.ALL.getValue(ChatApp.WHATSAPP).parse(parts)!!.canReply).isTrue()
    }
}
