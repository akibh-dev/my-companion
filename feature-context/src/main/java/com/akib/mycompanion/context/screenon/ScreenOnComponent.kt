package com.akib.mycompanion.context.screenon

import com.akib.mycompanion.context.device.PlaceDetector
import com.akib.mycompanion.core.host.HostedComponent
import com.akib.mycompanion.core.log.AppLog
import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.core.settings.Settings
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/** The screen is kept on only when all three hold (FR-3.5, FR-3.10). */
fun shouldKeepScreenOn(screenOn: Boolean, outside: Boolean, raised: Boolean): Boolean = screenOn && outside && raised

/**
 * Keeps the screen on outdoors while you hold the phone up (FR-3.5, FR-3.10), in core's background
 * service while "Keep screen on" is on and nothing is paused.
 *
 * Sensors and the outside check run only while the screen is on: in a pocket, with the screen off,
 * this costs nothing but one screen on/off listener.
 */
class ScreenOnComponent @Inject constructor(
    private val sensors: ScreenOnSensors,
    private val outsideDetector: OutsideDetector,
    private val keepAwake: KeepAwake,
    private val preferences: ScreenOnPreferences,
    private val status: ScreenOnStatus,
    private val log: AppLog,
    private val places: PlaceDetector,
) : HostedComponent {
    override val name = "keep-screen-on"

    private var keeping = false

    override fun isWanted(settings: Settings) = settings.isActive(Feature.KEEP_SCREEN_ON)

    override fun start(scope: CoroutineScope) {
        // Live arriving/leaving for the "outside" check, shared with context rules (FR-3.10).
        places.start(scope)
        scope.launch {
            try {
                sensors.screenOn().collectLatest { screenOn ->
                    if (screenOn) {
                        combine(outsideDetector.outside(), raised()) { outside, raised -> outside to raised }
                            .collect { (outside, raised) -> update(screenOn = true, outside, raised) }
                    } else {
                        update(screenOn = false, outside = false, raised = false)
                    }
                }
            } finally {
                keepAwake.release()
                keeping = false
                status.reset()
            }
        }
    }

    /** Debounced posture; a fresh detector per sensitivity and per screen-on, starting "not raised". */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun raised(): Flow<Boolean> = preferences.sensitivity.flatMapLatest { sensitivity ->
        val detector = PostureDetector(sensitivity)
        sensors.motion().map(detector::update).onStart { emit(false) }
    }.distinctUntilChanged()

    private fun update(screenOn: Boolean, outside: Boolean, raised: Boolean) {
        val allowed = keepAwake.canHold()
        val holding = if (shouldKeepScreenOn(screenOn, outside, raised) && allowed) {
            keepAwake.hold()
        } else {
            keepAwake.release()
            false
        }
        if (holding != keeping) {
            keeping = holding
            log.i(TAG, if (holding) "keeping the screen on" else "stopped keeping the screen on")
        }
        status.set(
            ScreenOnState(
                running = true,
                screenOn = screenOn,
                outside = outside,
                raised = raised,
                keepingOn = holding,
                overlayAllowed = allowed,
            ),
        )
    }

    private companion object {
        const val TAG = "ScreenOn"
    }
}
