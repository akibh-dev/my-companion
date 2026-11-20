package dev.akibh.mycompanion.export

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.akibh.mycompanion.autoreply.domain.Audience
import dev.akibh.mycompanion.autoreply.domain.AutoReplySettings
import dev.akibh.mycompanion.autoreply.domain.ReplyContext
import dev.akibh.mycompanion.autoreply.domain.ReplyTemplate
import dev.akibh.mycompanion.context.domain.Place
import dev.akibh.mycompanion.context.domain.Rule
import dev.akibh.mycompanion.context.domain.RuleAction
import dev.akibh.mycompanion.context.domain.SoundMode
import dev.akibh.mycompanion.context.domain.Trigger
import dev.akibh.mycompanion.core.settings.AiEngine
import dev.akibh.mycompanion.core.settings.Feature
import dev.akibh.mycompanion.core.settings.Settings
import dev.akibh.mycompanion.domain.model.Contact
import dev.akibh.mycompanion.domain.model.Playlist
import dev.akibh.mycompanion.domain.model.PlaylistItem
import dev.akibh.mycompanion.domain.model.Reminder
import dev.akibh.mycompanion.domain.model.Repeat
import dev.akibh.mycompanion.fakes.DHAKA
import dev.akibh.mycompanion.fakes.millisAt
import dev.akibh.mycompanion.fakes.video
import dev.akibh.mycompanion.messaging.ChatApp
import dev.akibh.mycompanion.voice.domain.Privacy
import dev.akibh.mycompanion.voice.domain.VoiceSettings
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
            sound = dev.akibh.mycompanion.domain.model.ReminderSound.Preset("bell"),
            contactId = 3,
        )
        val item = ExportJson.reminders(listOf(reminder), listOf(mum), DHAKA)
            .getJSONArray("items").getJSONObject(0)
        assertThat(item.getString("next")).isEqualTo("2026-10-24T08:00")
        assertThat(item.getString("repeat")).isEqualTo("DAILY")
        assertThat(item.getString("sound")).isEqualTo("PRESET:bell")
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
                appScope = dev.akibh.mycompanion.voice.domain.AppScope.CHAT_APPS,
                mutedApps = setOf("com.google.android.gm", ChatApp.TELEGRAM.packageName),
                blockRules = listOf(
                    dev.akibh.mycompanion.voice.domain.BlockRule(
                        "promo",
                        dev.akibh.mycompanion.voice.domain.BlockWhere.TITLE,
                    ),
                ),
                readSilent = true,
                senders = listOf("Mum"),
                maxChars = 120,
                speechRate = 1.25f,
                readingMode = dev.akibh.mycompanion.voice.domain.ReadingMode.BANGLISH,
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
            .put("app", JSONObject().put("features", JSONArray(listOf("CAPTURE", "CONTEXT_RULES", "TELEPORT"))))
            .toString()

        val bundle = ExportJson.parseSettings(text)

        assertThat(bundle.app.enabled).containsExactly(Feature.CONTEXT_RULES)
        assertThat(bundle.app.paused).isFalse()
        assertThat(bundle.voice).isEqualTo(VoiceSettings())
        assertThat(bundle.autoReply).isEqualTo(AutoReplySettings())
    }

    @Test
    fun `a version 1 backup's voice apps become muted chat apps`() {
        val text = JSONObject()
            .put("format", ExportJson.SETTINGS_FORMAT)
            .put("voice", JSONObject().put("apps", JSONArray(ChatApp.entries.map { it.name } - "SIGNAL")))
            .toString()

        assertThat(ExportJson.parseSettings(text).voice.mutedApps).containsExactly(ChatApp.SIGNAL.packageName)
    }

    @Test
    fun `a file that isn't a settings backup is refused`() {
        assertThrows(IllegalArgumentException::class.java) { ExportJson.parseSettings("not json") }
        assertThrows(IllegalArgumentException::class.java) { ExportJson.parseSettings("{\"area\":\"people\"}") }
    }
}
