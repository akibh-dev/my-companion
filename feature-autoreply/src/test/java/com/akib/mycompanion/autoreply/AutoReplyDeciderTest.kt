package com.akib.mycompanion.autoreply

import com.akib.mycompanion.autoreply.domain.Audience
import com.akib.mycompanion.autoreply.domain.AutoReplyDecider
import com.akib.mycompanion.autoreply.domain.AutoReplySettings
import com.akib.mycompanion.autoreply.domain.Decision
import com.akib.mycompanion.autoreply.domain.GroupPolicy
import com.akib.mycompanion.autoreply.domain.ReplyContext
import com.akib.mycompanion.autoreply.domain.ReplyTemplate
import com.akib.mycompanion.autoreply.domain.Situation
import com.akib.mycompanion.autoreply.system.CallStates
import com.akib.mycompanion.messaging.ChatApp
import com.akib.mycompanion.messaging.IncomingMessage
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AutoReplyDeciderTest {

    private val now = 10_000_000L
    private val message =
        IncomingMessage("k", ChatApp.WHATSAPP, "Rahim Uddin", "Are you free?", "Rahim Uddin", false, now, true)
    private val busy = ReplyTemplate(1, "Busy", "Hi {name}, busy until {until}.", ReplyContext.Busy)
    private val driving = ReplyTemplate(2, "Driving", "Driving, {name}.", ReplyContext.WhileRule("Driving"))
    private val dnd = ReplyTemplate(3, "DND", "Can't look now.", ReplyContext.WhileDnd)
    private val templates = listOf(busy, driving, dnd)
    private val quiet = Situation(now, dndOn = false, activeRules = emptySet())
    private val settings = AutoReplySettings(audience = Audience.EVERYONE)

    private fun decide(
        m: IncomingMessage = message,
        s: AutoReplySettings = settings,
        situation: Situation = quiet,
        isContact: Boolean? = true,
        lastReplyAt: Long? = null,
    ) = AutoReplyDecider.decide(m, s, templates, situation, isContact, lastReplyAt) { "5:30 PM" }

    @Test
    fun `replies with the first template that fits, filled in and labelled`() {
        val decision = decide(s = settings.copy(busy = true, busyUntil = now + 60_000))

        assertThat(decision).isEqualTo(Decision.Reply("[Auto-reply] Hi Rahim, busy until 5:30 PM.", busy))
    }

    @Test
    fun `context decides the template`() {
        assertThat(
            (decide(situation = quiet.copy(activeRules = setOf("driving"))) as Decision.Reply).template
        ).isEqualTo(driving)
        assertThat((decide(situation = quiet.copy(dndOn = true)) as Decision.Reply).template).isEqualTo(dnd)
        assertThat(decide()).isEqualTo(Decision.Skip("no template fits right now"))
    }

    @Test
    fun `busy ends at its end time`() {
        val ended = settings.copy(busy = true, busyUntil = now - 1)
        assertThat(AutoReplyDecider.isBusy(ended, now)).isFalse()
        assertThat(AutoReplyDecider.isBusy(settings.copy(busy = true), now)).isTrue()
    }

    @Test
    fun `audience rules`() {
        val dndNow = quiet.copy(dndOn = true)
        assertThat(decide(s = settings.copy(audience = Audience.CONTACTS), situation = dndNow, isContact = false))
            .isEqualTo(Decision.Skip("not a saved contact"))
        assertThat(decide(s = settings.copy(audience = Audience.CONTACTS), situation = dndNow, isContact = null))
            .isEqualTo(Decision.Skip("not a saved contact"))
        assertThat(
            decide(
                s = settings.copy(audience = Audience.ALLOW_LIST, allowList = listOf("rahim uddin")),
                situation = dndNow
            )
        )
            .isInstanceOf(Decision.Reply::class.java)
        assertThat(
            decide(s = settings.copy(blockList = listOf("Rahim Uddin")), situation = dndNow)
        ).isEqualTo(Decision.Skip("on your never list"))
    }

    @Test
    fun `groups only when mentioned`() {
        val group = message.copy(isGroup = true, conversation = "Office", text = "Is Akib coming?")
        val dndNow = quiet.copy(dndOn = true)

        assertThat(decide(m = group, situation = dndNow)).isEqualTo(Decision.Skip("group chat"))
        val mentions = settings.copy(groups = GroupPolicy.WHEN_MENTIONED, mentionWords = listOf("akib"))
        assertThat(decide(m = group, s = mentions, situation = dndNow)).isInstanceOf(Decision.Reply::class.java)
        assertThat(decide(m = group.copy(text = "lunch?"), s = mentions, situation = dndNow))
            .isEqualTo(Decision.Skip("group chat without a mention"))
    }

    @Test
    fun `one reply per conversation within the cool-down`() {
        val dndNow = quiet.copy(dndOn = true)
        assertThat(
            decide(situation = dndNow, lastReplyAt = now - 10 * 60_000)
        ).isEqualTo(Decision.Skip("already replied in the last 30 min"))
        assertThat(decide(situation = dndNow, lastReplyAt = now - 31 * 60_000)).isInstanceOf(Decision.Reply::class.java)
    }

    @Test
    fun `apps that are off or give no reply button are skipped`() {
        assertThat(decide(s = settings.copy(apps = setOf(ChatApp.SIGNAL)))).isEqualTo(Decision.Skip("WhatsApp is off"))
        assertThat(decide(m = message.copy(canReply = false))).isEqualTo(Decision.Skip("WhatsApp gave no reply button"))
    }

    @Test
    fun `contexts survive storage`() {
        listOf(ReplyContext.Busy, ReplyContext.WhileRule("Mosque"), ReplyContext.WhileDnd, ReplyContext.Always)
            .forEach { assertThat(ReplyContext.decode(it.encode())).isEqualTo(it) }
    }

    @Test
    fun `placeholders are filled, and an empty label adds nothing in front`() {
        val text = AutoReplyDecider.fill(
            "  {name} on {app}, back {until}  ",
            sender = "  Nadia Rahman ",
            app = "Signal",
            settings = AutoReplySettings(label = " "),
        ) { "never" }

        assertThat(text).isEqualTo("Nadia on Signal, back later")
    }

    @Test
    fun `until is the busy end time when there is one`() {
        val text = AutoReplyDecider.fill("back {until}", "Mum", "SMS", AutoReplySettings(busyUntil = 42)) { "at $it" }

        assertThat(text).isEqualTo("[Auto-reply] back at 42")
    }

    @Test
    fun `the never list beats the allow list`() {
        val both = settings.copy(
            audience = Audience.ALLOW_LIST,
            allowList = listOf("Rahim Uddin"),
            blockList = listOf("rahim uddin"),
        )

        assertThat(decide(s = both, situation = quiet.copy(dndOn = true)))
            .isEqualTo(Decision.Skip("on your never list"))
    }

    @Test
    fun `allow list and contacts-only let the right people through`() {
        val dndNow = quiet.copy(dndOn = true)

        val onlyKarim = settings.copy(audience = Audience.ALLOW_LIST, allowList = listOf("Karim"))

        assertThat(decide(s = onlyKarim, situation = dndNow))
            .isEqualTo(Decision.Skip("not on your list"))
        assertThat(decide(s = settings.copy(audience = Audience.CONTACTS), situation = dndNow, isContact = true))
            .isInstanceOf(Decision.Reply::class.java)
    }

    @Test
    fun `blank mention words never count as a mention`() {
        val group = message.copy(isGroup = true, conversation = "Office", text = "lunch at 1")
        val blanks = settings.copy(groups = GroupPolicy.WHEN_MENTIONED, mentionWords = listOf("", " "))

        assertThat(decide(m = group, s = blanks, situation = quiet.copy(dndOn = true)))
            .isEqualTo(Decision.Skip("group chat without a mention"))
    }

    @Test
    fun `the cool-down ends exactly at its length`() {
        val dndNow = quiet.copy(dndOn = true)
        val tenMinutes = settings.copy(cooldownMinutes = 10)

        assertThat(decide(s = tenMinutes, situation = dndNow, lastReplyAt = now - 10 * 60_000))
            .isInstanceOf(Decision.Reply::class.java)
        assertThat(decide(s = tenMinutes, situation = dndNow, lastReplyAt = now - 10 * 60_000 + 1))
            .isEqualTo(Decision.Skip("already replied in the last 10 min"))
    }

    @Test
    fun `templates are tried in your order and always fits last`() {
        val always = ReplyTemplate(9, "Always", "Later.", ReplyContext.Always)
        val dndNow = quiet.copy(dndOn = true)

        val alwaysFirst = AutoReplyDecider.decide(message, settings, listOf(always, dnd), dndNow, true, null) { "" }
        val alwaysLast = AutoReplyDecider.decide(message, settings, listOf(dnd, always), quiet, true, null) { "" }

        assertThat((alwaysFirst as Decision.Reply).template).isEqualTo(always)
        assertThat((alwaysLast as Decision.Reply).template).isEqualTo(always)
    }

    @Test
    fun `busy without an end lasts until switched off`() {
        val busyNoEnd = settings.copy(busy = true)

        assertThat(AutoReplyDecider.isBusy(busyNoEnd, Long.MAX_VALUE)).isTrue()
        assertThat(AutoReplyDecider.isBusy(settings, now)).isFalse()
        assertThat(AutoReplyDecider.isBusy(settings.copy(busy = true, busyUntil = now), now)).isFalse()
    }

    @Test
    fun `unknown stored contexts read as always, and rule names keep their colons`() {
        assertThat(ReplyContext.decode("SOMETHING_NEW")).isEqualTo(ReplyContext.Always)
        assertThat(ReplyContext.decode("")).isEqualTo(ReplyContext.Always)
        assertThat(ReplyContext.decode(ReplyContext.WhileRule("Home: night").encode()))
            .isEqualTo(ReplyContext.WhileRule("Home: night"))
    }

    @Test
    fun `a call answered after ringing is not missed, and ringing again starts over`() {
        assertThat(CallStates.next(CallStates.RINGING, CallStates.OFFHOOK)).isEqualTo(CallStates.OFFHOOK to false)
        assertThat(CallStates.next(CallStates.IDLE, CallStates.RINGING)).isEqualTo(CallStates.RINGING to false)
        assertThat(CallStates.next(CallStates.RINGING, CallStates.RINGING)).isEqualTo(CallStates.RINGING to false)
        assertThat(CallStates.next(null, CallStates.IDLE)).isEqualTo(CallStates.IDLE to false)
    }

    @Test
    fun `a call that rang and stopped without being answered is missed`() {
        val ringing = CallStates.RINGING
        val offhook = CallStates.OFFHOOK
        val idle = CallStates.IDLE

        assertThat(CallStates.next(ringing, idle)).isEqualTo(idle to true)
        assertThat(CallStates.next(offhook, idle)).isEqualTo(idle to false) // answered, then hung up
        assertThat(CallStates.next(null, ringing)).isEqualTo(ringing to false)
        assertThat(CallStates.next(idle, idle)).isEqualTo(idle to false)
    }
}
