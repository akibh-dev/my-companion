package dev.akibh.mycompanion.export

import dev.akibh.mycompanion.autoreply.data.AutoReplySettingsStore
import dev.akibh.mycompanion.core.settings.AiEngine
import dev.akibh.mycompanion.core.settings.Feature
import dev.akibh.mycompanion.core.settings.SettingsStore
import dev.akibh.mycompanion.quran.data.BookmarkRepository
import dev.akibh.mycompanion.voice.system.VoiceSettingsStore
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Settings backup (Phase 5): the app's switches, voice mode and auto-reply settings and the Quran
 * bookmarks as one JSON file. Restoring overwrites them all (the screen asks first).
 */
class SettingsBackup @Inject constructor(
    private val settingsStore: SettingsStore,
    private val voiceSettings: VoiceSettingsStore,
    private val autoReplySettings: AutoReplySettingsStore,
    private val quranBookmarks: BookmarkRepository,
) {
    suspend fun current(): SettingsBundle = SettingsBundle(
        app = settingsStore.settings.first(),
        voice = voiceSettings.settings.first(),
        autoReply = autoReplySettings.settings.first(),
        quran = quranBookmarks.backup(),
    )

    suspend fun exportJson(): String = ExportJson.settings(current()).toString(INDENT)

    /** @throws IllegalArgumentException when [text] isn't a settings backup. Nothing is changed then. */
    fun parse(text: String): SettingsBundle = ExportJson.parseSettings(text)

    /** Replaces the current settings with [bundle]. */
    suspend fun restore(bundle: SettingsBundle) {
        settingsStore.setPaused(bundle.app.paused)
        Feature.entries.forEach { settingsStore.setEnabled(it, it in bundle.app.enabled) }
        AiEngine.entries.forEach { settingsStore.setUseModel(it, it in bundle.app.modelEngines) }
        voiceSettings.update { bundle.voice }
        autoReplySettings.update { bundle.autoReply }
        bundle.quran?.let { quranBookmarks.restore(it) }
    }

    private companion object {
        const val INDENT = 2
    }
}
