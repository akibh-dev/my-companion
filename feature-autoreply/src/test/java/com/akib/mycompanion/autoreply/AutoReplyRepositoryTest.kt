package com.akib.mycompanion.autoreply

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.autoreply.data.AutoReplyDatabase
import com.akib.mycompanion.autoreply.data.AutoReplyRepository
import com.akib.mycompanion.autoreply.domain.ReplyContext
import com.akib.mycompanion.autoreply.domain.ReplyTemplate
import com.akib.mycompanion.core.crypto.FieldCipher
import com.google.common.truth.Truth.assertThat
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoReplyRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AutoReplyDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    /** A software key instead of the Keystore one. */
    private val cipher = FieldCipher { SecretKeySpec(ByteArray(32) { 5 }, "AES") }
    private val repository = AutoReplyRepository(db.dao(), cipher)

    @After
    fun tearDown() = db.close()

    @Test
    fun `defaults are added once, in their order`() = runTest {
        repository.addDefaultsIfEmpty()
        repository.addDefaultsIfEmpty()

        val templates = repository.templates.first()
        assertThat(templates.map { it.name }).containsExactly("Busy", "Driving", "Do Not Disturb").inOrder()
        assertThat(templates.map { it.context })
            .containsExactly(ReplyContext.Busy, ReplyContext.WhileRule("Driving"), ReplyContext.WhileDnd)
            .inOrder()
    }

    @Test
    fun `defaults never overwrite your own templates`() = runTest {
        repository.save(ReplyTemplate(name = "Mine", text = "Later", context = ReplyContext.Always), order = 0)

        repository.addDefaultsIfEmpty()

        assertThat(repository.templates.first().map { it.name }).containsExactly("Mine")
    }

    @Test
    fun `templates are added, edited and deleted`() = runTest {
        repository.save(ReplyTemplate(name = "Gym", text = "At the gym", context = ReplyContext.WhileRule("Gym")), 1)
        repository.save(ReplyTemplate(name = "Always", text = "Hi {name}", context = ReplyContext.Always), 0)
        val gym = repository.templates.first().first { it.name == "Gym" }

        repository.save(gym.copy(text = "Working out, back soon"), 1)
        var templates = repository.templates.first()
        assertThat(templates.map { it.name }).containsExactly("Always", "Gym").inOrder()
        assertThat(templates.last().text).isEqualTo("Working out, back soon")
        assertThat(templates.last().id).isEqualTo(gym.id)

        repository.delete(templates.first())
        templates = repository.templates.first()
        assertThat(templates.map { it.name }).containsExactly("Gym")
    }

    @Test
    fun `the log is stored encrypted and read back`() = runTest {
        repository.record(1_000, "WhatsApp", "Family", "Rahim Uddin", "[Auto-reply] Busy", "Busy", true, "Busy")

        val raw = db.dao().observeLog().first().single()
        assertThat(raw.personEncrypted).doesNotContain("Rahim")
        assertThat(raw.textEncrypted).doesNotContain("Busy")
        assertThat(raw.conversationHash).doesNotContain("Family")

        val entry = repository.log.first().single()
        assertThat(entry.person).isEqualTo("Rahim Uddin")
        assertThat(entry.text).isEqualTo("[Auto-reply] Busy")
        assertThat(entry.app).isEqualTo("WhatsApp")
        assertThat(entry.replied).isTrue()
    }

    @Test
    fun `the last reply is per app and conversation, ignoring case, and counts only sent replies`() = runTest {
        repository.record(1_000, "WHATSAPP", "Family", "Mum", "x", "Busy", true, "Busy")
        repository.record(2_000, "WHATSAPP", "family", "Mum", "x", "Busy", true, "Busy")
        repository.record(3_000, "WHATSAPP", "Family", "Mum", "", "", false, "no template fits right now")
        repository.record(4_000, "SIGNAL", "Family", "Mum", "x", "Busy", true, "Busy")

        assertThat(repository.lastReply("WHATSAPP", "FAMILY")).isEqualTo(2_000)
        assertThat(repository.lastReply("SIGNAL", "Family")).isEqualTo(4_000)
        assertThat(repository.lastReply("WHATSAPP", "Office")).isNull()
    }

    @Test
    fun `the log keeps 30 days and shows the newest first`() = runTest {
        val day = 24 * 60 * 60 * 1000L
        repository.record(0, "SMS", "+880", "+880", "old", "Missed call", true, "Missed call")
        repository.record(10 * day, "SMS", "+880", "+880", "middle", "Missed call", true, "Missed call")
        repository.record(31 * day, "SMS", "+880", "+880", "new", "Missed call", true, "Missed call")

        assertThat(repository.log.first().map { it.text }).containsExactly("new", "middle").inOrder()
    }

    @Test
    fun `a log line from another key reads as unreadable instead of crashing`() = runTest {
        val other = AutoReplyRepository(db.dao(), FieldCipher { SecretKeySpec(ByteArray(32) { 9 }, "AES") })
        other.record(1_000, "Telegram", "Nadia", "Nadia", "hi", "Busy", true, "Busy")

        val entry = repository.log.first().single()
        assertThat(entry.person).isEqualTo("(unreadable)")
        assertThat(entry.text).isEqualTo("(unreadable)")
    }
}
