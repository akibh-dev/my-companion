package com.akib.mycompanion.capture.system

import android.app.PendingIntent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.akib.mycompanion.capture.ui.CapturePanelActivity

/**
 * Quick Settings tile that opens the capture panel (FR-4.2). The panel shows over the lock screen,
 * so the tile works without unlocking (FR-4.8). Tapping it is a deliberate action, so it works even
 * while the volume-key trigger is off.
 */
class CaptureTileService : TileService() {

    override fun onStartListening() {
        val tile = qsTile ?: return
        tile.state = Tile.STATE_ACTIVE
        tile.updateTile()
    }

    override fun onClick() {
        val panel = PendingIntent.getActivity(
            this,
            0,
            CapturePanelActivity.intent(this),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        startActivityAndCollapse(panel)
    }
}
