package com.akib.mycompanion.export

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.autoreply.domain.Audience
import com.akib.mycompanion.autoreply.domain.AutoReplySettings
import com.akib.mycompanion.autoreply.domain.ReplyContext
import com.akib.mycompanion.autoreply.domain.ReplyTemplate
import com.akib.mycompanion.context.domain.Place
import com.akib.mycompanion.context.domain.Rule
import com.akib.mycompanion.context.domain.RuleAction
import com.akib.mycompanion.context.domain.SoundMode
import com.akib.mycompanion.context.domain.Trigger
import com.akib.mycompanion.core.settings.AiEngine
import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.core.settings.Settings
import com.akib.mycompanion.domain.model.Contact
import com.akib.mycompanion.domain.model.Playlist
import com.akib.mycompanion.domain.model.PlaylistItem
import com.akib.mycompanion.domain.model.Reminder
import com.akib.mycompanion.domain.model.Repeat
import com.akib.mycompanion.fakes.DHAKA
import com.akib.mycompanion.fakes.millisAt
import com.akib.mycompanion.fakes.video
import com.akib.mycompanion.messaging.ChatApp
import com.akib.mycompanion.voice.domain.Privacy
import com.akib.mycompanion.voice.domain.VoiceSettings
import com.google.common.truth.Truth.assertThat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/** org.json is part of Android, so these run on Robolectric. */
@RunWith(AndroidJUnit4::class)
class ExportJsonTest {

    private val mum = Contact(id = 3, name = "Mum", phone = "+8801711123456")

    @Test
    fun `people and reminders are readable`() {
        val people = ExportJson.people(listOf(mum))
        assertThat(people.getInt("count")).isEqualTo(1)
        assertThat(people.getJSONArray("items").getJSONObject(0).getString("whatsAppNumber"))
            .isEqualTo("+8801711123456")

        val reminder = Reminder(
            id = 1,
            title = "Medicine",
            message = "After breakfast",
            triggerAtMillis = millisAt(2026, 10, 24, 8, 0),
            repeat = Repeat.Daily,
            contactId = 3,
        )
        val item = ExportJson.reminders(listOf(reminder), listOf(mum), DHAKA)
            .getJSONArray("items").getJSONObject(0)
        assertThat(item.getString("next")).isEqualTo("2026-10-24T08:00")
        assertThat(item.getString("repeat")).isEqualTo("DAILY")
        assertThat(item.getString("callButtonFor")).isEqualTo("Mum")
    }

    @Test
    fun `playlists, rules, places and templates are described in words`() {
        val focus = Playlist(id = 1, name = "Focus")
        val playlists = ExportJson.playlists(
            mapOf(focus to listOf(PlaylistItem(id = 5, playlistId = 1, video = video("abc", "Lo-fi"), position = 0))),
        )
        val videoJson = playlists.getJSONArray("items").getJSONObject(0).getJSONArray("videos").getJSONObject(0)
        assertThat(videoJson.getString("url")).isEqualTo("https://www.youtube.com/watch?v=abc")

        val home = Place(id = 2, name = "Home", latitude = 23.81, longitude = 90.41, wifiNames = listOf("HomeNet"))
        val rule = Rule(
            id = 1,
            name = "Quiet at home",
            triggers = listOf(Trigger.AtPlace(2)),
            actions = listOf(RuleAction.Sound(SoundMode.VIBRATE)),
        )
        val ruleJson = ExportJson.rules(listOf(rule), listOf(home)).getJSONArray("items").getJSONObject(0)
        assertThat(ruleJson.getJSONArray("when").getString(0)).isEqualTo("At Home")
        assertThat(ruleJson.getJSONArray("then").getString(0)).isEqualTo("Sound: Vibrate")

        val placeJson = ExportJson.places(listOf(home)).getJSONArray("items").getJSONObject(0)
        assertThat(placeJson.getJSONArray("wifiNetworks").getString(0)).isEqualTo("HomeNet")

        val template = ReplyTemplate(name = "Driving", text = "I'm driving", context = ReplyContext.WhileRule("Car"))
        val templateJson = ExportJson.templates(listOf(template)).getJSONArray("items").getJSONObject(0)
        assertThat(templateJson.getString("usedWhen")).isEqualTo("While the rule \"Car\" is active")
    }

    @Test
    fun `settings survive a round trip`() {
        val bundle = SettingsBundle(
            app = Settings(
                paused = true,
                enabled = setOf(Feature.VOICE_MODE, Feature.AUTO_REPLY),
                modelEngines = setOf(AiEngine.SUMMARIZER),
            ),
            voice = VoiceSettings(
                privacy = Privacy.ALWAYS,
                apps = setOf(ChatApp.WHATSAPP),
                senders = listOf("Mum"),
                maxChars = 120,
                speechRate = 1.25f,
            ),
            autoReply = AutoReplySettings(
                audience = Audience.ALLOW_LIST,
                allowList = listOf("Boss"),
                cooldownMinutes = 10,
                label = "[Bot]",
                busy = true,
                busyUntil = 1_900_000_000_000,
                missedCallContactsOnly = false,
            ),
        )

        val text = ExportJson.settings(bundle).toString(2)

        assertThat(ExportJson.parseSettings(text)).isEqualTo(bundle)
    }

    @Test
    fun `an old or partial backup keeps defaults and skips unknown names`() {
        val text = JSONObject()
            .put("format", ExportJson.SETTINGS_FORMAT)
            .put("app", JSONObject().put("features", JSONArray(listOf("CAPTURE", "TELEPORT"))))
            .toString()

        val bundle = ExportJson.parseSettings(text)

        assertThat(bundle.app.enabled).containsExactly(Feature.CAPTURE)
        assertThat(bundle.app.paused).isFalse()
        assertThat(bundle.voice).isEqualTo(VoiceSettings())
        assertThat(bundle.autoReply).isEqualTo(AutoReplySettings())
    }

    @Test
    fun `a file that isn't a settings backup is refused`() {
        assertThrows(IllegalArgumentException::class.java) { ExportJson.parseSettings("not json") }
        assertThrows(IllegalArgumentException::class.java) { ExportJson.parseSettings("{\"area\":\"people\"}") }
    }
}
