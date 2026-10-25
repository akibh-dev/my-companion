package com.akib.mycompanion.autoreply

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.autoreply.data.AutoReplyDatabase
import com.akib.mycompanion.autoreply.data.AutoReplyRepository
import com.akib.mycompanion.autoreply.data.AutoReplySettingsStore
import com.akib.mycompanion.autoreply.data.ContactLookup
import com.akib.mycompanion.autoreply.domain.Audience
import com.akib.mycompanion.autoreply.domain.AutoReplySettings
import com.akib.mycompanion.autoreply.domain.ReplyContext
import com.akib.mycompanion.autoreply.domain.ReplyTemplate
import com.akib.mycompanion.autoreply.system.AutoReplyConsumer
import com.akib.mycompanion.core.context.ContextStatus
import com.akib.mycompanion.core.crypto.FieldCipher
import com.akib.mycompanion.core.log.AppLog
import com.akib.mycompanion.core.settings.AiEngine
import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.core.settings.Settings
import com.akib.mycompanion.core.settings.SettingsStore
import com.akib.mycompanion.core.time.Clock
import com.akib.mycompanion.messaging.ChatApp
import com.akib.mycompanion.messaging.IncomingMessage
import com.akib.mycompanion.messaging.ReplySender
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.nio.file.Files
import java.time.ZoneId
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoReplyConsumerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AutoReplyDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val repository = AutoReplyRepository(db.dao(), FieldCipher { SecretKeySpec(ByteArray(32), "AES") })

    private val settingsStore = object : SettingsStore {
        override val settings = MutableStateFlow(Settings(enabled = setOf(Feature.AUTO_REPLY)))
        override suspend fun setPaused(paused: Boolean) {
            settings.value = settings.value.copy(paused = paused)
        }
        override suspend fun setEnabled(feature: Feature, enabled: Boolean) = Unit
        override suspend fun setUseModel(engine: AiEngine, useModel: Boolean) = Unit
    }
    private val replySettings = MutableStateFlow(AutoReplySettings(audience = Audience.EVERYONE))
    private val replyStore = mockk<AutoReplySettingsStore> { every { settings } returns replySettings }
    private val contacts = mockk<ContactLookup> { every { isContact(any()) } returns true }
    private val status = ContextStatus()
    private val sender = mockk<ReplySender> { every { reply(any(), any()) } returns true }
    private val clock = object : Clock {
        var now = 10_000_000L
        override fun nowMillis() = now
        override val zone: ZoneId = ZoneId.of("Asia/Dhaka")
    }
    private val consumer = AutoReplyConsumer(
        context,
        settingsStore,
        replyStore,
        repository,
        contacts,
        status,
        sender,
        clock,
        AppLog(Files.createTempDirectory("log").toFile(), mirrorToLogcat = false),
    )

    private val message =
        IncomingMessage("key-1", ChatApp.WHATSAPP, "Rahim Uddin", "Are you free?", "Rahim Uddin", false, 0, true)

    private suspend fun always(text: String = "Hi {name}, later.") =
        repository.save(ReplyTemplate(name = "Always", text = text, context = ReplyContext.Always), 0)

    @After
    fun tearDown() = db.close()

    @Test
    fun `replies through the chat app and logs it`() = runTest {
        always()

        consumer.onMessage(message)

        verify { sender.reply("key-1", "[Auto-reply] Hi Rahim, later.") }
        val entry = repository.log.first().single()
        assertThat(entry.replied).isTrue()
        assertThat(entry.reason).isEqualTo("Always")
        assertThat(entry.person).isEqualTo("Rahim Uddin")
        assertThat(entry.app).isEqualTo("WhatsApp")
    }

    @Test
    fun `nothing happens while auto-reply is off or paused`() = runTest {
        always()
        settingsStore.setPaused(true)
        consumer.onMessage(message)

        settingsStore.settings.value = Settings()
        consumer.onMessage(message)

        verify(exactly = 0) { sender.reply(any(), any()) }
        assertThat(repository.log.first()).isEmpty()
    }

    @Test
    fun `a refused reply is logged as not sent`() = runTest {
        always()
        every { sender.reply(any(), any()) } returns false

        consumer.onMessage(message)

        val entry = repository.log.first().single()
        assertThat(entry.replied).isFalse()
        assertThat(entry.reason).isEqualTo("the chat app refused the reply")
    }

    @Test
    fun `no fitting template is worth a line in the log`() = runTest {
        consumer.onMessage(message)

        verify(exactly = 0) { sender.reply(any(), any()) }
        val entry = repository.log.first().single()
        assertThat(entry.replied).isFalse()
        assertThat(entry.reason).isEqualTo("no template fits right now")
    }

    @Test
    fun `a missing reply button is logged, ordinary skips are not`() = runTest {
        always()

        consumer.onMessage(message.copy(isGroup = true, conversation = "Family"))
        assertThat(repository.log.first()).isEmpty()

        consumer.onMessage(message.copy(canReply = false))
        assertThat(repository.log.first().single().reason).isEqualTo("WhatsApp gave no reply button")
    }

    @Test
    fun `one reply per conversation during the cool-down`() = runTest {
        always()

        consumer.onMessage(message)
        clock.now += 5 * 60_000
        consumer.onMessage(message.copy(key = "key-2", text = "Hello?"))
        clock.now += 30 * 60_000
        consumer.onMessage(message.copy(key = "key-3", text = "Still there?"))

        verify(exactly = 1) { sender.reply("key-1", any()) }
        verify(exactly = 0) { sender.reply("key-2", any()) }
        verify(exactly = 1) { sender.reply("key-3", any()) }
    }

    @Test
    fun `an active context rule picks its template`() = runTest {
        val driving = ReplyTemplate(name = "Driving", text = "Driving.", context = ReplyContext.WhileRule("Driving"))
        repository.save(driving, 0)
        status.setActiveRules(setOf("Driving"))

        consumer.onMessage(message)

        verify { sender.reply("key-1", "[Auto-reply] Driving.") }
    }

    @Test
    fun `when contacts can't be read, contacts-only skips everyone`() = runTest {
        always()
        replySettings.value = AutoReplySettings(audience = Audience.CONTACTS)
        every { contacts.isContact(any()) } throws SecurityException("no access")

        consumer.onMessage(message)

        verify(exactly = 0) { sender.reply(any(), any()) }
    }
}
