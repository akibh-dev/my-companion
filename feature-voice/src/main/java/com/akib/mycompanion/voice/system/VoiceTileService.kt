package com.akib.mycompanion.voice.system

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.core.settings.SettingsStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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
            val on = Feature.VOICE_MODE in settingsStore.settings.first().enabled
            settingsStore.setEnabled(Feature.VOICE_MODE, !on)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
