package com.akib.mycompanion.capture.system

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.akib.mycompanion.capture.data.CaptureFiles
import com.akib.mycompanion.capture.data.CaptureRepository
import com.akib.mycompanion.capture.domain.CaptureType
import com.akib.mycompanion.capture.domain.VolumeComboDetector
import com.akib.mycompanion.capture.ui.CapturePanelActivity
import com.akib.mycompanion.core.log.AppLog
import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.core.settings.SettingsStore
import com.akib.mycompanion.core.time.Clock
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Opens the capture panel when both volume keys are held for a second (FR-4.1, spike S2), and takes
 * screenshots for the panel (FR-4.7). Key events are only watched, never consumed, so single presses
 * keep changing the volume. Does nothing while quick capture is off or everything is paused.
 */
@AndroidEntryPoint
class CaptureAccessibilityService : AccessibilityService() {

    @Inject lateinit var settingsStore: SettingsStore

    @Inject lateinit var clock: Clock

    @Inject lateinit var log: AppLog

    @Inject lateinit var bridge: ScreenshotBridge

    @Inject lateinit var repository: CaptureRepository

    @Inject lateinit var files: CaptureFiles

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var detector: VolumeComboDetector? = null

    @Volatile private var active = false

    /** Runs when a held combo is due, in case no further key event (auto-repeat) arrives. */
    private val holdCheck = Runnable {
        if (detector?.check(clock.nowMillis()) == true) openPanel()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        detector = VolumeComboDetector(clock)
        scope.launch {
            settingsStore.settings.collect { settings ->
                active = settings.isActive(Feature.CAPTURE)
                if (!active) {
                    handler.removeCallbacks(holdCheck)
                    detector?.reset()
                }
            }
        }
        bridge.attach(::screenshotSoon)
        log.i(TAG, "connected")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val combo = detector
        if (!active || combo == null) return false
        val down = when (event.action) {
            KeyEvent.ACTION_DOWN -> true
            KeyEvent.ACTION_UP -> false
            else -> return false
        }
        val now = clock.nowMillis()
        handler.removeCallbacks(holdCheck)
        if (combo.onKey(event.keyCode, down, now)) {
            openPanel()
        } else {
            combo.millisUntilFire(now)?.let { handler.postDelayed(holdCheck, it) }
        }
        // Never swallow the key: the volume must keep working.
        return false
    }

    private fun openPanel() {
        log.i(TAG, "volume keys held: opening the capture panel")
        runCatching { startActivity(CapturePanelActivity.intent(this)) }
            .onFailure { log.w(TAG, "couldn't open the capture panel", it) }
    }

    /** Waits for the panel to close, so the screenshot shows what was under it. */
    private fun screenshotSoon() {
        scope.launch {
            delay(SCREENSHOT_DELAY_MILLIS)
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        scope.launch { save(screenshot) }
                    }

                    override fun onFailure(errorCode: Int) {
                        log.w(TAG, "screenshot failed ($errorCode)")
                        toast("Couldn't take a screenshot")
                    }
                },
            )
        }
    }

    private suspend fun save(screenshot: ScreenshotResult) {
        val buffer = screenshot.hardwareBuffer
        val file = withContext(Dispatchers.IO) {
            runCatching { writePng(Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)) }
                .onFailure { log.w(TAG, "couldn't save the screenshot", it) }
                .getOrNull()
                .also { buffer.close() }
        }
        if (file == null) {
            toast("Couldn't save the screenshot")
            return
        }
        repository.addFile(CaptureType.SCREENSHOT, file)
        toast("Screenshot saved to your captures")
    }

    private fun writePng(hardware: Bitmap?): File {
        checkNotNull(hardware) { "No image in the screenshot" }
        // Hardware bitmaps can't be compressed directly; copy into normal memory first.
        val bitmap = hardware.copy(Bitmap.Config.ARGB_8888, false)
        hardware.recycle()
        val file = files.newFile(CaptureType.SCREENSHOT, clock.nowMillis())
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
        } catch (e: Exception) {
            file.delete()
            throw e
        } finally {
            bitmap.recycle()
        }
        return file
    }

    private fun toast(text: String) {
        Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        bridge.detach()
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        log.i(TAG, "disconnected")
        super.onDestroy()
    }

    private companion object {
        const val TAG = "CaptureTrigger"
        const val SCREENSHOT_DELAY_MILLIS = 600L
        const val PNG_QUALITY = 100
    }
}
