package com.akib.mycompanion.messaging

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Plain Kotlin: adapters only see [NotificationParts]. Texts are made up. */
class ChatAdapterEdgeCasesTest {

    private fun parts(
        app: ChatApp,
        title: String? = null,
        text: String? = null,
        messages: List<Pair<String?, String>> = emptyList(),
        conversation: String? = null,
        group: Boolean = false,
        reply: Boolean = true,
        key: String = "key",
        postedAt: Long = 1_000,
    ) = NotificationParts(
        packageName = app.packageName,
        key = key,
        postedAt = postedAt,
        title = title,
        text = text,
        conversationTitle = conversation,
        isGroupConversation = group,
        messages = messages,
        isGroupSummary = false,
        isOngoing = false,
        category = null,
        hasReplyAction = reply,
    )

    private fun parse(parts: NotificationParts) = ChatAdapter.ALL.getValue(ChatApp.of(parts.packageName)!!).parse(parts)

    @Test
    fun `every chat app is known by its package and has an adapter`() {
        ChatApp.entries.forEach { app ->
            assertThat(ChatApp.of(app.packageName)).isEqualTo(app)
            assertThat(ChatAdapter.ALL.getValue(app).app).isEqualTo(app)
        }
        assertThat(ChatApp.of("com.example.notachat")).isNull()
        assertThat(ChatApp.entries.map { it.packageName }.toSet()).hasSize(ChatApp.entries.size)
    }

    @Test
    fun `key, time and the reply button are carried over`() {
        val message = parse(
            parts(ChatApp.MESSENGER, title = "Rahim", text = "hi", reply = false, key = "0|k|7", postedAt = 42),
        )!!

        assertThat(message.key).isEqualTo("0|k|7")
        assertThat(message.postedAt).isEqualTo(42)
        assertThat(message.canReply).isFalse()
        assertThat(message.app).isEqualTo(ChatApp.MESSENGER)
    }

    @Test
    fun `text is trimmed, and blank text isn't a message`() {
        assertThat(parse(parts(ChatApp.SIGNAL, title = "Rahim", text = "  see you  "))!!.text).isEqualTo("see you")
        assertThat(parse(parts(ChatApp.SIGNAL, title = "Rahim", text = "   "))).isNull()
        assertThat(parse(parts(ChatApp.SIGNAL, title = "Rahim", text = null))).isNull()
        assertThat(parse(parts(ChatApp.SIGNAL, title = null, text = "hi"))).isNull()
        assertThat(parse(parts(ChatApp.SIGNAL, title = " ", text = "hi"))).isNull()
    }

    @Test
    fun `a notification titled with the app's own name isn't from a person`() {
        assertThat(parse(parts(ChatApp.TELEGRAM, title = "Telegram", text = "Updating"))).isNull()
        assertThat(parse(parts(ChatApp.GOOGLE_MESSAGES, title = "messages", text = "Backup done"))).isNull()
    }

    @Test
    fun `status lines are skipped whatever their case`() {
        assertThat(parse(parts(ChatApp.MESSENGER, title = "Rahim", text = "CHECKING FOR NEW MESSAGES"))).isNull()
        assertThat(parse(parts(ChatApp.WHATSAPP, title = "Rahim", text = "1 new message"))).isNull()
        assertThat(parse(parts(ChatApp.WHATSAPP, title = "Rahim", text = "Missed video call"))).isNull()
        assertThat(parse(parts(ChatApp.WHATSAPP, title = "Rahim", text = "WhatsApp Web is currently active"))).isNull()
    }

    @Test
    fun `call wording only counts as noise in apps that send it`() {
        val signal = parse(parts(ChatApp.SIGNAL, title = "Rahim", text = "Incoming voice call later?"))

        assertThat(signal!!.text).isEqualTo("Incoming voice call later?")
    }

    @Test
    fun `a message that merely mentions new messages is still a message`() {
        val message = parse(parts(ChatApp.WHATSAPP, title = "Rahim", text = "I sent you 2 new messages earlier"))

        assertThat(message!!.text).isEqualTo("I sent you 2 new messages earlier")
    }

    @Test
    fun `whatsapp business groups are understood like whatsapp's`() {
        val message = parse(parts(ChatApp.WHATSAPP_BUSINESS, title = "Shop: Karim", text = "Order ready"))!!

        assertThat(message.isGroup).isTrue()
        assertThat(message.sender).isEqualTo("Karim")
        assertThat(message.conversation).isEqualTo("Shop")
    }

    @Test
    fun `whatsapp messaging style keeps a plain sender as it is`() {
        val message = parse(parts(ChatApp.WHATSAPP, messages = listOf("Mum" to "Call me")))!!

        assertThat(message.sender).isEqualTo("Mum")
        assertThat(message.isGroup).isFalse()
        assertThat(message.conversation).isEqualTo("Mum")
    }

    @Test
    fun `a group without a conversation title falls back to the sender`() {
        val message = parse(parts(ChatApp.SIGNAL, messages = listOf("Karim" to "Dinner?"), group = true))!!

        assertThat(message.isGroup).isTrue()
        assertThat(message.conversation).isEqualTo("Karim")
    }

    @Test
    fun `telegram one-to-one text with a colon keeps the whole text`() {
        val message = parse(parts(ChatApp.TELEGRAM, title = "Nadia", text = "Note: bring the keys"))!!

        assertThat(message.sender).isEqualTo("Nadia")
        assertThat(message.text).isEqualTo("Note: bring the keys")
        assertThat(message.isGroup).isFalse()
    }

    @Test
    fun `telegram groups with messaging style use it`() {
        val message = parse(
            parts(
                ChatApp.TELEGRAM,
                title = "Office",
                messages = listOf("Nadia" to "on my way"),
                conversation = "Office",
                group = true,
            ),
        )!!

        assertThat(message.sender).isEqualTo("Nadia")
        assertThat(message.conversation).isEqualTo("Office")
    }
}
