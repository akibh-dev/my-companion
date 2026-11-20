package dev.akibh.mycompanion.messaging

import android.app.Notification
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** Any app's notification as voice mode sees it (texts are made up). */
@RunWith(AndroidJUnit4::class)
class NoticeParserTest {

    private fun parts(
        packageName: String = "com.bank.app",
        title: String? = "Payment received",
        text: String? = "Tk 500 from Rahim",
        bigText: String? = null,
        messages: List<Pair<String?, String>> = emptyList(),
        conversation: String? = null,
        category: String? = null,
        summary: Boolean = false,
        ongoing: Boolean = false,
        foreground: Boolean = false,
        media: Boolean = false,
        silent: Boolean = false,
    ) = NotificationParts(
        packageName, "key", 1_000, title, text, conversation, false, messages, summary, ongoing, category,
        hasReplyAction = false,
        messageTimes = if (messages.isEmpty()) emptyList() else listOf(2_000),
        bigText = bigText,
        isForegroundService = foreground,
        isMedia = media,
        isSilent = silent,
    )

    @Test
    fun `a plain notification keeps app, title and the longest text`() {
        val notice = NoticeParser.parse(parts(bigText = "Tk 500 from Rahim. Balance Tk 9,000"), "City Bank", null)!!

        assertThat(notice.appName).isEqualTo("City Bank")
        assertThat(notice.title).isEqualTo("Payment received")
        assertThat(notice.text).isEqualTo("Tk 500 from Rahim. Balance Tk 9,000")
        assertThat(notice.sender).isNull()
        assertThat(notice.isChatApp).isFalse()
    }

    @Test
    fun `a title that is just the app name is dropped, and an empty notification is skipped`() {
        assertThat(NoticeParser.parse(parts(title = "city bank"), "City Bank", null)!!.title).isEmpty()
        assertThat(NoticeParser.parse(parts(title = " ", text = null), "City Bank", null)).isNull()
    }

    @Test
    fun `status, players, progress, calls and summaries are never read`() {
        val skipped = listOf(
            parts(summary = true),
            parts(ongoing = true),
            parts(foreground = true),
            parts(media = true),
            parts(category = Notification.CATEGORY_PROGRESS),
            parts(category = Notification.CATEGORY_CALL),
            parts(category = Notification.CATEGORY_TRANSPORT),
            parts(category = Notification.CATEGORY_ALARM),
        )
        skipped.forEach { assertThat(NoticeParser.parse(it, "City Bank", null)).isNull() }
        assertThat(NoticeParser.parse(parts(category = Notification.CATEGORY_EMAIL), "Mail", null)).isNotNull()
    }

    @Test
    fun `silent notifications are passed on, marked silent`() {
        assertThat(NoticeParser.parse(parts(silent = true), "City Bank", null)!!.isSilent).isTrue()
    }

    @Test
    fun `messaging style from any app reads as a message, your own messages don't`() {
        val notice = NoticeParser.parse(
            parts(packageName = "com.discord", messages = listOf("Nadia" to "lunch?"), conversation = "Friends"),
            "Discord",
            null,
        )!!

        assertThat(notice.sender).isEqualTo("Nadia")
        assertThat(notice.conversation).isEqualTo("Friends")
        assertThat(notice.text).isEqualTo("lunch?")
        assertThat(notice.postedAt).isEqualTo(2_000)

        val mine = parts(packageName = "com.discord", messages = listOf(null to "on my way"))
        assertThat(NoticeParser.parse(mine, "Discord", null)).isNull()
    }

    @Test
    fun `chat apps are read only when their adapter found a new message`() {
        val whatsApp = parts(packageName = ChatApp.WHATSAPP.packageName, title = "Mum", text = "Call me")
        assertThat(NoticeParser.parse(whatsApp, "WhatsApp", null)).isNull()

        val message = ChatAdapter.ALL.getValue(ChatApp.WHATSAPP).parse(whatsApp)!!
        val notice = NoticeParser.parse(whatsApp, "WhatsApp", message)!!
        assertThat(notice.sender).isEqualTo("Mum")
        assertThat(notice.title).isEqualTo("Mum")
        assertThat(notice.isChatApp).isTrue()
    }

    @Test
    fun `an update with the same content has the same fingerprint`() {
        val first = NoticeParser.parse(parts(), "City Bank", null)!!
        val again = NoticeParser.parse(parts().copy(postedAt = 9_000), "City Bank", null)!!

        assertThat(again.fingerprint).isEqualTo(first.fingerprint)
        assertThat(first.copy(text = "Tk 600").fingerprint).isNotEqualTo(first.fingerprint)
    }

    @Test
    fun `the extractor finds big text, foreground service and media notifications`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        fun sbn(notification: Notification) = StatusBarNotification(
            "com.bank.app", "com.bank.app", 1, null, 0, 0, 0, notification, Process.myUserHandle(), 0,
        )

        val big = NotificationCompat.Builder(context, "c")
            .setContentTitle("Payment received")
            .setContentText("Tk 500")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Tk 500 from Rahim"))
            .build()
        val parts = NotificationExtractor.parts(sbn(big), isSilent = true)
        assertThat(parts.bigText).isEqualTo("Tk 500 from Rahim")
        assertThat(parts.isSilent).isTrue()
        assertThat(parts.isMedia).isFalse()

        val service = NotificationCompat.Builder(context, "c").setContentTitle("Syncing").build()
            .apply { flags = flags or Notification.FLAG_FOREGROUND_SERVICE }
        assertThat(NotificationExtractor.parts(sbn(service)).isForegroundService).isTrue()

        val media = Notification.Builder(context, "c").setStyle(Notification.MediaStyle()).build()
        assertThat(NotificationExtractor.parts(sbn(media)).isMedia).isTrue()
    }
}
