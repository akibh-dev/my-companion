package com.akib.mycompanion.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.akib.mycompanion.core.util.enumsOf
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Features with their own on/off switch (plan, Phase 1: "a switch per feature"). */
enum class Feature(val title: String, val description: String) {
    CONTEXT_RULES("Context rules", "Change Wi-Fi, sound and Do Not Disturb by place, time, charging or Bluetooth"),
    AUTO_REPLY("Auto-reply", "Answer chat messages for you while you're busy"),
    MISSED_CALL_SMS("Missed-call SMS", "Text people back when you miss their call"),
    VOICE_MODE("Voice mode", "Read incoming messages aloud"),
    CAPTURE("Quick capture", "Hold both volume keys to capture a note, voice memo or photo"),
    KEEP_SCREEN_ON("Keep screen on", "Keep the screen awake while you hold the phone up outdoors"),
}

/**
 * The four model interfaces (plan §2). Each runs its built-in fallback until switched to the model;
 * a bad model can be switched off again without a new build.
 */
enum class AiEngine(val title: String) {
    TRANSLITERATOR("Banglish to Bangla"),
    SPEECH_SYNTH("Speech (voice)"),
    SPEECH_RECOGNIZER("Speech to text"),
    SUMMARIZER("Summaries"),
}

data class Settings(
    /** Master pause (FR-1.6, FR-3.6): every automatic action stops while set. */
    val paused: Boolean = false,
    val enabled: Set<Feature> = emptySet(),
    /** Engines switched to their model instead of the fallback. */
    val modelEngines: Set<AiEngine> = emptySet(),
) {
    /** On and not paused. */
    fun isActive(feature: Feature): Boolean = !paused && feature in enabled

    fun usesModel(engine: AiEngine): Boolean = engine in modelEngines
}

interface SettingsStore {
    val settings: Flow<Settings>

    suspend fun setPaused(paused: Boolean)

    suspend fun setEnabled(feature: Feature, enabled: Boolean)

    suspend fun setUseModel(engine: AiEngine, useModel: Boolean)
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class DataStoreSettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) : SettingsStore {
    private val store = context.settingsDataStore

    override val settings: Flow<Settings> = store.data.map { prefs ->
        Settings(
            paused = prefs[PAUSED] ?: false,
            enabled = enumsOf<Feature>(prefs[ENABLED]).orEmpty(),
            modelEngines = enumsOf<AiEngine>(prefs[MODELS]).orEmpty(),
        )
    }

    override suspend fun setPaused(paused: Boolean) {
        store.edit { it[PAUSED] = paused }
    }

    override suspend fun setEnabled(feature: Feature, enabled: Boolean) {
        store.edit { prefs ->
            val current = prefs[ENABLED].orEmpty()
            prefs[ENABLED] = if (enabled) current + feature.name else current - feature.name
        }
    }

    override suspend fun setUseModel(engine: AiEngine, useModel: Boolean) {
        store.edit { prefs ->
            val current = prefs[MODELS].orEmpty()
            prefs[MODELS] = if (useModel) current + engine.name else current - engine.name
        }
    }

    private companion object {
        val PAUSED = booleanPreferencesKey("paused")
        val ENABLED = stringSetPreferencesKey("enabled_features")
        val MODELS = stringSetPreferencesKey("model_engines")
    }
}
