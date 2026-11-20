package dev.akibh.mycompanion.messaging

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Re-posts of a notification still in the shade are read only when they add something (texts are made up). */
class NoticeMemoryTest {

    private val memory = NoticeMemory()

    private fun whatsApp(
        vararg messages: Pair<String?, String>,
        title: String = "Mum",
        postedAt: Long = 1_000,
        times: List<Long> = messages.indices.map { 500L + it },
    ) = NotificationParts(
        ChatApp.WHATSAPP.packageName, "chat-mum", postedAt, title, messages.last().second, null, false,
        messages.toList(), false, false, null, hasReplyAction = true, messageTimes = times,
    )

    @Test
    fun `the same whatsapp message re-posted with a new title, time or order is read once`() {
        assertThat(memory.isNew(whatsApp("Mum" to "Call me"))).isTrue()

        assertThat(memory.isNew(whatsApp("Mum" to "Call me", title = "Mum (1 message)"))).isFalse()
        assertThat(memory.isNew(whatsApp("Mum" to "Call me", postedAt = 9_000, times = listOf(8_000)))).isFalse()
        assertThat(memory.isNew(whatsApp("Mum" to "Call me"))).isFalse()
    }

    @Test
    fun `a new message in the same notification is new, even with the same text as an earlier one`() {
        memory.isNew(whatsApp("Mum" to "ok"))

        assertThat(memory.isNew(whatsApp("Mum" to "ok", "Mum" to "ok"))).isTrue()
        assertThat(memory.isNew(whatsApp("Mum" to "ok", "Mum" to "ok", "Mum" to "on my way"))).isTrue()
        // The oldest message dropping out of the list adds nothing.
        assertThat(memory.isNew(whatsApp("Mum" to "ok", "Mum" to "on my way"))).isFalse()
    }

    @Test
    fun `a removed notification starts fresh, and notifications seen on connect aren't read`() {
        memory.isNew(whatsApp("Mum" to "Call me"))
        memory.forget("chat-mum")
        assertThat(memory.isNew(whatsApp("Mum" to "Call me"))).isTrue()

        val other = NoticeMemory()
        other.remember(whatsApp("Mum" to "Call me"))
        assertThat(other.isNew(whatsApp("Mum" to "Call me"))).isFalse()
        assertThat(other.isNew(whatsApp("Mum" to "Call me", "Mum" to "Now"))).isTrue()
    }

    @Test
    fun `other apps are new when the title or text changes`() {
        fun bank(title: String, text: String, postedAt: Long = 1) = NotificationParts(
            "com.bank.app", "bank", postedAt, title, text, null, false, emptyList(), false, false, null,
            hasReplyAction = false,
        )
        assertThat(memory.isNew(bank("Payment", "Tk 500"))).isTrue()
        assertThat(memory.isNew(bank("Payment", "Tk 500", postedAt = 99))).isFalse()
        assertThat(memory.isNew(bank("Payment", "Tk 600"))).isTrue()
        assertThat(memory.isNew(bank("Refund", "Tk 600"))).isTrue()
    }

    @Test
    fun `a notice's fingerprint ignores the title chat apps change on re-posts`() {
        val message = IncomingMessage("k", ChatApp.WHATSAPP, "Mum", "Call me", "Mum", false, 500, true)

        assertThat(message.toNotice(title = "Mum").fingerprint)
            .isEqualTo(message.toNotice(title = "Mum (2 messages)").fingerprint)
    }
}
