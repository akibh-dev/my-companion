package dev.akibh.mycompanion.export

import dev.akibh.mycompanion.autoreply.domain.Audience
import dev.akibh.mycompanion.autoreply.domain.AutoReplySettings
import dev.akibh.mycompanion.autoreply.domain.GroupPolicy
import dev.akibh.mycompanion.autoreply.domain.ReplyContext
import dev.akibh.mycompanion.autoreply.domain.ReplyTemplate
import dev.akibh.mycompanion.context.domain.Place
import dev.akibh.mycompanion.context.domain.Rule
import dev.akibh.mycompanion.context.ui.Describe
import dev.akibh.mycompanion.core.settings.AiEngine
import dev.akibh.mycompanion.core.settings.Feature
import dev.akibh.mycompanion.core.settings.Settings
import dev.akibh.mycompanion.core.util.enumOf
import dev.akibh.mycompanion.domain.model.Contact
import dev.akibh.mycompanion.domain.model.Playlist
import dev.akibh.mycompanion.domain.model.PlaylistItem
import dev.akibh.mycompanion.domain.model.Reminder
import dev.akibh.mycompanion.library.data.LibraryBackup
import dev.akibh.mycompanion.messaging.ChatApp
import dev.akibh.mycompanion.quran.data.BackupBookmark
import dev.akibh.mycompanion.quran.data.QuranBackup
import dev.akibh.mycompanion.quran.domain.AyahRef
import dev.akibh.mycompanion.voice.domain.AppScope
import dev.akibh.mycompanion.voice.domain.BlockRule
import dev.akibh.mycompanion.voice.domain.BlockWhere
import dev.akibh.mycompanion.voice.domain.Privacy
import dev.akibh.mycompanion.voice.domain.ReadingMode
import dev.akibh.mycompanion.voice.domain.VoiceSettings
import java.time.Instant
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

/** All settings that a settings backup holds (NFR-11): the app's switches, voice mode and auto-reply. */
data class SettingsBundle(
    val app: Settings = Settings(),
    val voice: VoiceSettings = VoiceSettings(),
    val autoReply: AutoReplySettings = AutoReplySettings(),
    /** Quran bookmarks; null in backups made before the Quran reader, so restoring them keeps the current ones. */
    val quran: QuranBackup? = null,
    /** The book library (books, places read, shelves, stats); null in backups made before it. */
    val library: LibraryBackup? = null,
)

/**
 * Human-readable JSON for "Export my data" (NFR-11), one document per area. Field names are plain
 * English, times are ISO-8601 local times next to the raw epoch millis, enums are their names.
 */
object ExportJson {
    const val SETTINGS_FORMAT = "my-companion-settings"
    const val SETTINGS_VERSION = 1

    /** The ranges the voice and auto-reply screens offer; restored values are kept inside them. */
    val MAX_CHARS_RANGE = 60..600
    val SPEECH_RATE_RANGE = 0.5f..2f
    val COOLDOWN_RANGE = 5..240

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
                    .put("sound", reminder.sound.encode())
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
                    .put("appScope", voice.appScope.name)
                    .put("mutedApps", JSONArray(voice.mutedApps.sorted()))
                    .put(
                        "blockRules",
                        JSONArray(
                            voice.blockRules.map { JSONObject().put("phrase", it.phrase).put("where", it.where.name) },
                        ),
                    )
                    .put("readSilent", voice.readSilent)
                    .put("senders", JSONArray(voice.senders))
                    .put("maxChars", voice.maxChars)
                    .put("speechRate", voice.speechRate.toDouble())
                    .put("readingMode", voice.readingMode.name),
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
            .apply { bundle.quran?.let { put("quran", quran(it)) } }
            .apply { bundle.library?.let { put("library", it.json) } }
    }

    private fun quran(backup: QuranBackup): JSONObject = JSONObject().put(
        "bookmarks",
        JSONArray(
            backup.bookmarks.map {
                JSONObject()
                    .put("surah", it.ref.surah)
                    .put("ayah", it.ref.ayah)
                    .put("note", it.note)
                    .put("createdEpochMillis", it.createdAt)
            },
        ),
    )

    private fun parseQuran(json: JSONObject?): QuranBackup? {
        json ?: return null
        val list = json.optJSONArray("bookmarks") ?: JSONArray()
        return QuranBackup(
            (0 until list.length()).mapNotNull { i ->
                val item = list.optJSONObject(i) ?: return@mapNotNull null
                val surah = item.optInt("surah")
                val ayah = item.optInt("ayah")
                if (surah <= 0 || ayah <= 0) return@mapNotNull null
                BackupBookmark(AyahRef(surah, ayah), item.optString("note"), item.optLong("createdEpochMillis"))
            },
        )
    }

    /**
     * Reads a settings backup. Missing fields keep their defaults and unknown names are skipped, so an
     * old backup still restores what it can. A backup from a newer version is refused: its fields may
     * mean something else.
     *
     * @throws IllegalArgumentException when the text isn't a My Companion settings backup, or is newer.
     */
    fun parseSettings(text: String): SettingsBundle {
        val root = runCatching { JSONObject(text) }.getOrNull()
        if (root == null || root.optString("format") != SETTINGS_FORMAT) {
            throw IllegalArgumentException("That file isn't a My Companion settings backup")
        }
        if (root.optInt("version", SETTINGS_VERSION) > SETTINGS_VERSION) {
            throw IllegalArgumentException("That backup is from a newer version of My Companion. Update the app first.")
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
                appScope = enumOf<AppScope>(voiceJson.optString("appScope")) ?: voiceDefaults.appScope,
                mutedApps = strings(voiceJson.optJSONArray("mutedApps"))?.toSet()
                    // Version 1 backups listed the chat apps that were read; the others become muted.
                    ?: enums<ChatApp>(voiceJson.optJSONArray("apps"))?.let { on ->
                        (ChatApp.entries - on).map { it.packageName }.toSet()
                    }
                    ?: voiceDefaults.mutedApps,
                blockRules = blockRules(voiceJson.optJSONArray("blockRules")),
                readSilent = voiceJson.optBoolean("readSilent", voiceDefaults.readSilent),
                senders = strings(voiceJson.optJSONArray("senders")) ?: voiceDefaults.senders,
                // Clamped to what the screens allow: a hand-edited backup can't set what the app can't.
                maxChars = voiceJson.optInt("maxChars", voiceDefaults.maxChars).coerceIn(MAX_CHARS_RANGE),
                speechRate = voiceJson.optDouble("speechRate", voiceDefaults.speechRate.toDouble()).toFloat()
                    .takeIf { it.isFinite() }?.coerceIn(SPEECH_RATE_RANGE) ?: voiceDefaults.speechRate,
                readingMode = enumOf<ReadingMode>(voiceJson.optString("readingMode")) ?: voiceDefaults.readingMode,
            ),
            autoReply = AutoReplySettings(
                audience = enumOf<Audience>(replyJson.optString("audience")) ?: replyDefaults.audience,
                allowList = strings(replyJson.optJSONArray("allowList")) ?: replyDefaults.allowList,
                blockList = strings(replyJson.optJSONArray("blockList")) ?: replyDefaults.blockList,
                groups = enumOf<GroupPolicy>(replyJson.optString("groups")) ?: replyDefaults.groups,
                mentionWords = strings(replyJson.optJSONArray("mentionWords")) ?: replyDefaults.mentionWords,
                // A zero or negative cool-down would turn off the one-reply-per-chat throttle.
                cooldownMinutes = replyJson.optInt("cooldownMinutes", replyDefaults.cooldownMinutes)
                    .coerceIn(COOLDOWN_RANGE),
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
            quran = parseQuran(root.optJSONObject("quran")),
            library = root.optJSONObject("library")?.let(::LibraryBackup),
        )
    }

    private fun blockRules(array: JSONArray?): List<BlockRule> = array?.let {
        (0 until it.length()).mapNotNull { i ->
            val rule = it.optJSONObject(i) ?: return@mapNotNull null
            val phrase = rule.optString("phrase").trim()
            val where = enumOf<BlockWhere>(rule.optString("where")) ?: BlockWhere.ANYWHERE
            if (phrase.isEmpty()) null else BlockRule(phrase, where)
        }
    }.orEmpty()

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
