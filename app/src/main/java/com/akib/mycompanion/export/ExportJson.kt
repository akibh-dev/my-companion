package com.akib.mycompanion.export

import com.akib.mycompanion.autoreply.domain.Audience
import com.akib.mycompanion.autoreply.domain.AutoReplySettings
import com.akib.mycompanion.autoreply.domain.GroupPolicy
import com.akib.mycompanion.autoreply.domain.ReplyContext
import com.akib.mycompanion.autoreply.domain.ReplyTemplate
import com.akib.mycompanion.context.domain.Place
import com.akib.mycompanion.context.domain.Rule
import com.akib.mycompanion.context.ui.Describe
import com.akib.mycompanion.core.settings.AiEngine
import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.core.settings.Settings
import com.akib.mycompanion.core.util.enumOf
import com.akib.mycompanion.domain.model.Contact
import com.akib.mycompanion.domain.model.Playlist
import com.akib.mycompanion.domain.model.PlaylistItem
import com.akib.mycompanion.domain.model.Reminder
import com.akib.mycompanion.messaging.ChatApp
import com.akib.mycompanion.voice.domain.Privacy
import com.akib.mycompanion.voice.domain.VoiceSettings
import java.time.Instant
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

/** All settings that a settings backup holds (NFR-11): the app's switches, voice mode and auto-reply. */
data class SettingsBundle(
    val app: Settings = Settings(),
    val voice: VoiceSettings = VoiceSettings(),
    val autoReply: AutoReplySettings = AutoReplySettings(),
)

/**
 * Human-readable JSON for "Export my data" (NFR-11), one document per area. Field names are plain
 * English, times are ISO-8601 local times next to the raw epoch millis, enums are their names.
 */
object ExportJson {
    const val SETTINGS_FORMAT = "my-companion-settings"
    const val SETTINGS_VERSION = 1

    fun people(contacts: List<Contact>): JSONObject = document(
        "people",
        contacts.map { JSONObject().put("name", it.name).put("whatsAppNumber", it.phone) },
    )

    fun reminders(reminders: List<Reminder>, contacts: List<Contact>, zone: ZoneId): JSONObject {
        val names = contacts.associate { it.id to it.name }
        return document(
            "reminders",
            reminders.map { reminder ->
                JSONObject()
                    .put("title", reminder.title)
                    .put("message", reminder.message)
                    .put("next", localTime(reminder.triggerAtMillis, zone))
                    .put("nextEpochMillis", reminder.triggerAtMillis)
                    .put("repeat", reminder.repeat.encode())
                    .put("callButtonFor", reminder.contactId?.let(names::get) ?: JSONObject.NULL)
            },
        )
    }

    fun playlists(playlists: Map<Playlist, List<PlaylistItem>>): JSONObject = document(
        "playlists",
        playlists.map { (playlist, items) ->
            JSONObject()
                .put("name", playlist.name)
                .put(
                    "videos",
                    JSONArray(
                        items.sortedBy { it.position }.map { item ->
                            JSONObject()
                                .put("title", item.video.title)
                                .put("channel", item.video.author)
                                .put("seconds", item.video.durationSeconds)
                                .put("url", item.video.watchUrl)
                        },
                    ),
                )
        },
    )

    fun rules(rules: List<Rule>, places: List<Place>): JSONObject {
        val byId = places.associateBy { it.id }
        return document(
            "rules",
            rules.sortedBy { it.order }.map { rule ->
                JSONObject()
                    .put("name", rule.name)
                    .put("enabled", rule.enabled)
                    .put("when", JSONArray(rule.triggers.map { Describe.trigger(it, byId) }))
                    .put("then", JSONArray(rule.actions.map { Describe.action(it) }))
            },
        )
    }

    fun places(places: List<Place>): JSONObject = document(
        "places",
        places.map { place ->
            JSONObject()
                .put("name", place.name)
                .put("latitude", place.latitude)
                .put("longitude", place.longitude)
                .put("radiusMeters", place.radiusMeters)
                .put("wifiNetworks", JSONArray(place.wifiNames))
        },
    )

    fun templates(templates: List<ReplyTemplate>): JSONObject = document(
        "autoReplyTemplates",
        templates.map { template ->
            JSONObject()
                .put("name", template.name)
                .put("text", template.text)
                .put("usedWhen", describe(template.context))
        },
    )

    fun settings(bundle: SettingsBundle): JSONObject {
        val app = bundle.app
        val voice = bundle.voice
        val reply = bundle.autoReply
        return JSONObject()
            .put("format", SETTINGS_FORMAT)
            .put("version", SETTINGS_VERSION)
            .put(
                "app",
                JSONObject()
                    .put("paused", app.paused)
                    .put("features", names(app.enabled))
                    .put("modelEngines", names(app.modelEngines)),
            )
            .put(
                "voice",
                JSONObject()
                    .put("privacy", voice.privacy.name)
                    .put("apps", names(voice.apps))
                    .put("senders", JSONArray(voice.senders))
                    .put("maxChars", voice.maxChars)
                    .put("speechRate", voice.speechRate.toDouble()),
            )
            .put(
                "autoReply",
                JSONObject()
                    .put("audience", reply.audience.name)
                    .put("allowList", JSONArray(reply.allowList))
                    .put("blockList", JSONArray(reply.blockList))
                    .put("groups", reply.groups.name)
                    .put("mentionWords", JSONArray(reply.mentionWords))
                    .put("cooldownMinutes", reply.cooldownMinutes)
                    .put("label", reply.label)
                    .put("apps", names(reply.apps))
                    .put("busy", reply.busy)
                    .put("busyUntil", reply.busyUntil ?: JSONObject.NULL)
                    .put("missedCallText", reply.missedCallText)
                    .put("missedCallContactsOnly", reply.missedCallContactsOnly),
            )
    }

    /**
     * Reads a settings backup. Missing fields keep their defaults and unknown names (from a newer or older
     * version) are skipped, so an old backup still restores what it can.
     *
     * @throws IllegalArgumentException when the text isn't a My Companion settings backup.
     */
    fun parseSettings(text: String): SettingsBundle {
        val root = runCatching { JSONObject(text) }.getOrNull()
        if (root == null || root.optString("format") != SETTINGS_FORMAT) {
            throw IllegalArgumentException("That file isn't a My Companion settings backup")
        }
        val appJson = root.optJSONObject("app") ?: JSONObject()
        val voiceJson = root.optJSONObject("voice") ?: JSONObject()
        val replyJson = root.optJSONObject("autoReply") ?: JSONObject()
        val voiceDefaults = VoiceSettings()
        val replyDefaults = AutoReplySettings()
        return SettingsBundle(
            app = Settings(
                paused = appJson.optBoolean("paused", false),
                enabled = enums<Feature>(appJson.optJSONArray("features")) ?: emptySet(),
                modelEngines = enums<AiEngine>(appJson.optJSONArray("modelEngines")) ?: emptySet(),
            ),
            voice = VoiceSettings(
                privacy = enumOf<Privacy>(voiceJson.optString("privacy")) ?: voiceDefaults.privacy,
                apps = enums<ChatApp>(voiceJson.optJSONArray("apps")) ?: voiceDefaults.apps,
                senders = strings(voiceJson.optJSONArray("senders")) ?: voiceDefaults.senders,
                maxChars = voiceJson.optInt("maxChars", voiceDefaults.maxChars),
                speechRate = voiceJson.optDouble("speechRate", voiceDefaults.speechRate.toDouble()).toFloat(),
            ),
            autoReply = AutoReplySettings(
                audience = enumOf<Audience>(replyJson.optString("audience")) ?: replyDefaults.audience,
                allowList = strings(replyJson.optJSONArray("allowList")) ?: replyDefaults.allowList,
                blockList = strings(replyJson.optJSONArray("blockList")) ?: replyDefaults.blockList,
                groups = enumOf<GroupPolicy>(replyJson.optString("groups")) ?: replyDefaults.groups,
                mentionWords = strings(replyJson.optJSONArray("mentionWords")) ?: replyDefaults.mentionWords,
                cooldownMinutes = replyJson.optInt("cooldownMinutes", replyDefaults.cooldownMinutes),
                label = replyJson.optString("label", replyDefaults.label),
                apps = enums<ChatApp>(replyJson.optJSONArray("apps")) ?: replyDefaults.apps,
                busy = replyJson.optBoolean("busy", false),
                busyUntil = if (replyJson.isNull("busyUntil")) null else replyJson.optLong("busyUntil"),
                missedCallText = replyJson.optString("missedCallText", replyDefaults.missedCallText),
                missedCallContactsOnly = replyJson.optBoolean(
                    "missedCallContactsOnly",
                    replyDefaults.missedCallContactsOnly,
                ),
            ),
        )
    }

    private fun localTime(millis: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime().toString()

    private fun describe(context: ReplyContext): String = when (context) {
        ReplyContext.Busy -> "While you're marked busy"
        is ReplyContext.WhileRule -> "While the rule \"${context.ruleName}\" is active"
        ReplyContext.WhileDnd -> "While Do Not Disturb is on"
        ReplyContext.Always -> "Always"
    }

    private fun document(area: String, items: List<JSONObject>): JSONObject =
        JSONObject().put("area", area).put("count", items.size).put("items", JSONArray(items))

    private fun names(values: Collection<Enum<*>>): JSONArray = JSONArray(values.map { it.name }.sorted())

    private fun strings(array: JSONArray?): List<String>? =
        array?.let { (0 until it.length()).mapNotNull { i -> it.optString(i).takeIf(String::isNotEmpty) } }

    private inline fun <reified E : Enum<E>> enums(array: JSONArray?): Set<E>? =
        strings(array)?.mapNotNull { enumOf<E>(it) }?.toSet()
}
