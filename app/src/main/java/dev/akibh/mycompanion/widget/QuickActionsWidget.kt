package dev.akibh.mycompanion.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dagger.hilt.android.AndroidEntryPoint
import dev.akibh.mycompanion.R
import dev.akibh.mycompanion.core.log.AppLog
import dev.akibh.mycompanion.core.settings.Feature
import dev.akibh.mycompanion.core.settings.Settings
import dev.akibh.mycompanion.core.settings.SettingsStore
import dev.akibh.mycompanion.di.ApplicationScope
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Home-screen widget: pause/resume and voice mode. Redrawn whenever settings change. */
@AndroidEntryPoint
class QuickActionsWidget : AppWidgetProvider() {

    @Inject lateinit var settingsStore: SettingsStore

    @Inject lateinit var log: AppLog

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        // Hilt injects in super.onReceive; AppWidgetProvider then calls onUpdate for update broadcasts.
        super.onReceive(context, intent)
        val action = intent.action
        if (action != ACTION_TOGGLE_PAUSE && action != ACTION_TOGGLE_VOICE) return
        // goAsync keeps the process alive while DataStore writes (null when called directly in tests).
        val pending: PendingResult? = goAsync()
        scope.launch {
            try {
                if (action == ACTION_TOGGLE_PAUSE) {
                    val paused = settingsStore.togglePaused()
                    log.i(TAG, if (paused) "paused from the widget" else "resumed from the widget")
                } else {
                    val on = settingsStore.toggleEnabled(Feature.VOICE_MODE)
                    log.i(TAG, if (on) "voice mode on from the widget" else "voice mode off from the widget")
                }
                // CompanionApp's settings collector redraws the widget with the new state.
            } finally {
                pending?.finish()
            }
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending: PendingResult? = goAsync()
        scope.launch {
            try {
                manager.updateAppWidget(appWidgetIds, views(context, settingsStore.settings.first()))
            } finally {
                pending?.finish()
            }
        }
    }

    companion object {
        private const val TAG = "Widget"
        const val ACTION_TOGGLE_PAUSE = "dev.akibh.mycompanion.action.WIDGET_TOGGLE_PAUSE"
        const val ACTION_TOGGLE_VOICE = "dev.akibh.mycompanion.action.WIDGET_TOGGLE_VOICE"

        private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        /** Redraws every placed widget; does nothing when none is on the home screen. */
        fun refresh(context: Context, settings: Settings) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, QuickActionsWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, views(context, settings))
        }

        fun views(context: Context, settings: Settings): RemoteViews {
            val voiceOn = Feature.VOICE_MODE in settings.enabled
            return RemoteViews(context.packageName, R.layout.widget_quick_actions).apply {
                setTextViewText(
                    R.id.widget_pause,
                    context.getString(if (settings.paused) R.string.widget_resume else R.string.widget_pause),
                )
                setTextViewText(
                    R.id.widget_voice,
                    context.getString(if (voiceOn) R.string.widget_voice_on else R.string.widget_voice_off),
                )
                setOnClickPendingIntent(R.id.widget_pause, toggle(context, ACTION_TOGGLE_PAUSE, REQUEST_PAUSE))
                setOnClickPendingIntent(R.id.widget_voice, toggle(context, ACTION_TOGGLE_VOICE, REQUEST_VOICE))
            }
        }

        private fun toggle(context: Context, action: String, requestCode: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, QuickActionsWidget::class.java).setAction(action),
                FLAGS,
            )

        private const val REQUEST_PAUSE = 0
        private const val REQUEST_VOICE = 1
    }
}
