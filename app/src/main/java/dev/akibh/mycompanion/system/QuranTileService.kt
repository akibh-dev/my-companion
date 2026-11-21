package dev.akibh.mycompanion.system

import android.app.PendingIntent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dagger.hilt.android.AndroidEntryPoint
import dev.akibh.mycompanion.MainActivity
import dev.akibh.mycompanion.quran.data.QuranSummaries
import dev.akibh.mycompanion.quran.domain.QuranReminder
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Quick Settings tile: opens the Quran where reading stopped; its subtitle says where that is. */
@AndroidEntryPoint
class QuranTileService : TileService() {

    @Inject lateinit var summaries: QuranSummaries

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watching: Job? = null

    override fun onStartListening() {
        watching = scope.launch {
            summaries.summary.collect { summary ->
                val tile = qsTile ?: return@collect
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = summary.place
                tile.updateTile()
            }
        }
    }

    override fun onStopListening() {
        watching?.cancel()
    }

    override fun onClick() {
        val open = MainActivity.openRouteIntent(this, CONTINUE_ROUTE)
        startActivityAndCollapse(
            PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
        )
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** The reader with no ayah given opens the last place read. */
        const val CONTINUE_ROUTE = QuranReminder.ROUTE
    }
}
