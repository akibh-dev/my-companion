package dev.akibh.mycompanion.voice.system

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dagger.hilt.android.AndroidEntryPoint
import dev.akibh.mycompanion.core.settings.Feature
import dev.akibh.mycompanion.core.settings.SettingsStore
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Quick Settings tile for voice mode (FR-5.1). */
@AndroidEntryPoint
class VoiceTileService : TileService() {

    @Inject lateinit var settingsStore: SettingsStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watching: Job? = null

    override fun onStartListening() {
        watching = scope.launch {
            settingsStore.settings.collect { settings ->
                val tile = qsTile ?: return@collect
                val on = Feature.VOICE_MODE in settings.enabled
                tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                tile.subtitle = when {
                    on && settings.paused -> "Paused"
                    on -> "On"
                    else -> "Off"
                }
                tile.updateTile()
            }
        }
    }

    override fun onStopListening() {
        watching?.cancel()
    }

    override fun onClick() {
        scope.launch {
            settingsStore.toggleEnabled(Feature.VOICE_MODE)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
