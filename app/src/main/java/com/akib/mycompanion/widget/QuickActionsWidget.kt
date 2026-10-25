package com.akib.mycompanion.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.akib.mycompanion.MainActivity
import com.akib.mycompanion.R
import com.akib.mycompanion.core.log.AppLog
import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.core.settings.Settings
import com.akib.mycompanion.core.settings.SettingsStore
import com.akib.mycompanion.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Home-screen widget (Phase 5): pause/resume everything, voice mode on/off, open the capture panel,
 * open the vault. Plain RemoteViews, no extra library. CompanionApp redraws it whenever settings change.
 */
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
                val settings = settingsStore.settings.first()
                if (action == ACTION_TOGGLE_PAUSE) {
                    settingsStore.setPaused(!settings.paused)
                    log.i(TAG, if (settings.paused) "resumed from the widget" else "paused from the widget")
                } else {
                    val on = Feature.VOICE_MODE !in settings.enabled
                    settingsStore.setEnabled(Feature.VOICE_MODE, on)
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
        const val ACTION_TOGGLE_PAUSE = "com.akib.mycompanion.action.WIDGET_TOGGLE_PAUSE"
        const val ACTION_TOGGLE_VOICE = "com.akib.mycompanion.action.WIDGET_TOGGLE_VOICE"

        /** Opens quick capture's panel (W1's CapturePanelActivity listens for it). */
        const val ACTION_CAPTURE = "com.akib.mycompanion.action.CAPTURE"

        const val VAULT_ROUTE = "vault"
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
                setOnClickPendingIntent(
                    R.id.widget_capture,
                    PendingIntent.getActivity(context, REQUEST_CAPTURE, captureIntent(context), FLAGS),
                )
                setOnClickPendingIntent(
                    R.id.widget_vault,
                    PendingIntent.getActivity(
                        context,
                        REQUEST_VAULT,
                        MainActivity.openRouteIntent(context, VAULT_ROUTE),
                        FLAGS,
                    ),
                )
            }
        }

        fun captureIntent(context: Context): Intent = Intent(ACTION_CAPTURE)
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        private fun toggle(context: Context, action: String, requestCode: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, QuickActionsWidget::class.java).setAction(action),
                FLAGS,
            )

        private const val REQUEST_PAUSE = 0
        private const val REQUEST_VOICE = 1
        private const val REQUEST_CAPTURE = 2
        private const val REQUEST_VAULT = 3
    }
}
