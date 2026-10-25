package com.akib.mycompanion.export

import com.akib.mycompanion.autoreply.data.AutoReplySettingsStore
import com.akib.mycompanion.core.settings.AiEngine
import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.core.settings.SettingsStore
import com.akib.mycompanion.voice.system.VoiceSettingsStore
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Settings backup (Phase 5): the app's switches, voice mode and auto-reply settings as one JSON file.
 * Restoring overwrites all three (the screen asks first).
 */
class SettingsBackup @Inject constructor(
    private val settingsStore: SettingsStore,
    private val voiceSettings: VoiceSettingsStore,
    private val autoReplySettings: AutoReplySettingsStore,
) {
    suspend fun current(): SettingsBundle = SettingsBundle(
        app = settingsStore.settings.first(),
        voice = voiceSettings.settings.first(),
        autoReply = autoReplySettings.settings.first(),
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
    }

    private companion object {
        const val INDENT = 2
    }
}
