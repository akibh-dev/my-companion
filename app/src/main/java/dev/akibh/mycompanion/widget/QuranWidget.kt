package dev.akibh.mycompanion.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import dagger.hilt.android.AndroidEntryPoint
import dev.akibh.mycompanion.MainActivity
import dev.akibh.mycompanion.R
import dev.akibh.mycompanion.di.ApplicationScope
import dev.akibh.mycompanion.quran.data.QuranSummaries
import dev.akibh.mycompanion.quran.data.QuranSummary
import dev.akibh.mycompanion.system.QuranTileService
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Home-screen widget: where Quran reading stopped and today's khatm pages; a tap continues reading. */
@AndroidEntryPoint
class QuranWidget : AppWidgetProvider() {

    @Inject lateinit var summaries: QuranSummaries

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending: PendingResult? = goAsync()
        scope.launch {
            try {
                manager.updateAppWidget(appWidgetIds, views(context, summaries.summary.first()))
            } finally {
                pending?.finish()
            }
        }
    }

    companion object {
        /** Redraws every placed widget; does nothing when none is on the home screen. */
        fun refresh(context: Context, summary: QuranSummary) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, QuranWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, views(context, summary))
        }

        fun views(context: Context, summary: QuranSummary): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_quran).apply {
                setTextViewText(R.id.widget_quran_place, summary.place)
                setTextViewText(R.id.widget_quran_goal, summary.goal ?: context.getString(R.string.widget_quran_label))
                setViewVisibility(R.id.widget_quran_goal, View.VISIBLE)
                val open = MainActivity.openRouteIntent(context, QuranTileService.CONTINUE_ROUTE)
                setOnClickPendingIntent(
                    R.id.widget_quran,
                    PendingIntent.getActivity(
                        context,
                        REQUEST_OPEN,
                        open,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            }

        private const val REQUEST_OPEN = 10
    }
}
