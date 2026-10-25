package com.akib.mycompanion.system

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.akib.mycompanion.core.log.AppLog
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

/** Quick Settings tile: one tap pauses every automatic action, another resumes (FR-1.6, FR-3.6, NFR-4). */
@AndroidEntryPoint
class PauseTileService : TileService() {

    @Inject lateinit var settingsStore: SettingsStore

    @Inject lateinit var log: AppLog

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watching: Job? = null

    override fun onStartListening() {
        watching = scope.launch { settingsStore.settings.collect { show(it.paused) } }
    }

    override fun onStopListening() {
        watching?.cancel()
    }

    override fun onClick() {
        scope.launch {
            val paused = !settingsStore.settings.first().paused
            settingsStore.setPaused(paused)
            log.i("PauseTile", if (paused) "paused" else "resumed")
        }
    }

    private fun show(paused: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (paused) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = if (paused) "Paused" else "Running"
        tile.updateTile()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
