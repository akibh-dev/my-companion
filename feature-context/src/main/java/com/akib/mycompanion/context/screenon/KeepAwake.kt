package com.akib.mycompanion.context.screenon

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.net.Uri
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.akib.mycompanion.core.log.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps the screen from timing out while held. Called on the main thread. */
interface KeepAwake {
    /** Whether holding is possible at all (the permission is granted). */
    fun canHold(): Boolean

    /** Starts keeping the screen on (no-op if already); true when it is now being kept on. */
    fun hold(): Boolean

    /** Lets the screen time out normally again (no-op if not holding). */
    fun release()
}

/** "Display over other apps", the special access the keep-awake window needs. */
object OverlayPermission {
    fun granted(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.fromParts("package", context.packageName, null))
}

/**
 * Spike S3's method: an invisible 1×1 px overlay window with `FLAG_KEEP_SCREEN_ON`. While it's
 * showing, Android won't turn the screen off, whichever app is in front. It can't be touched or
 * focused, so it never gets in the way. No wake lock is held, so nothing is kept awake once the
 * window is gone.
 */
@Singleton
class OverlayKeepAwake @Inject constructor(
    @ApplicationContext private val context: Context,
    private val log: AppLog,
) : KeepAwake {
    private var view: View? = null
    private var windowContext: Context? = null

    override fun canHold(): Boolean = OverlayPermission.granted(context)

    override fun hold(): Boolean {
        if (view != null) return true
        if (!canHold()) return false
        return runCatching {
            val windows = windowContext()
            val pixel = View(windows)
            windows.getSystemService(WindowManager::class.java).addView(pixel, layoutParams())
            view = pixel
        }.onFailure { log.w(TAG, "couldn't add the keep-awake window", it) }.isSuccess
    }

    override fun release() {
        val pixel = view ?: return
        view = null
        runCatching { windowContext().getSystemService(WindowManager::class.java).removeView(pixel) }
            .onFailure { log.w(TAG, "couldn't remove the keep-awake window", it) }
    }

    /** Overlay windows need a window context; the application context has no display. */
    private fun windowContext(): Context = windowContext ?: run {
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        context.createWindowContext(display, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
            .also { windowContext = it }
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        1,
        1,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        title = "Keep screen on"
    }

    private companion object {
        const val TAG = "ScreenOn"
    }
}
