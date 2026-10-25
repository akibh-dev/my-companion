package com.akib.mycompanion.capture.ui

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.android.AndroidEntryPoint

/**
 * The see-through capture panel (FR-4.2), opened by holding both volume keys, the Quick Settings
 * tile, the home-screen widget (action [ACTION_CAPTURE]) or the capture space. It shows over the lock
 * screen for new captures only (FR-4.8); opening the capture space asks to unlock first.
 */
@AndroidEntryPoint
class CapturePanelActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setContent {
            PanelTheme {
                CapturePanel(
                    onClose = { finish() },
                    onOpenSpace = { openCaptureSpace() },
                )
            }
        }
    }

    private fun openCaptureSpace() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard.isKeyguardLocked) {
            keyguard.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() {
                        launchApp()
                    }
                },
            )
        } else {
            launchApp()
        }
    }

    private fun launchApp() {
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            launch.putExtra(EXTRA_ROUTE, CAPTURE_SPACE_ROUTE)
            startActivity(launch)
        }
        finish()
    }

    companion object {
        /** Sent by the home-screen widget. */
        const val ACTION_CAPTURE = "com.akib.mycompanion.action.CAPTURE"

        /** Asks the app to open a screen by route; the app may honour it (see the W1 integration notes). */
        const val EXTRA_ROUTE = "com.akib.mycompanion.extra.ROUTE"

        const val CAPTURE_SPACE_ROUTE = "capture"

        fun intent(context: Context): Intent = Intent(context, CapturePanelActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/** Same look as the app: light or dark, in the wallpaper's colours. */
@Composable
internal fun PanelTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = colors, content = content)
}
