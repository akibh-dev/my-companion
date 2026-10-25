package com.akib.mycompanion.context.screenon

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt

/** A sensor vector in the phone's own axes (x right, y up the screen, z out of the screen), in m/s². */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    val length: Float get() = sqrt(x * x + y * y + z * z)

    operator fun minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
}

/**
 * One accelerometer reading with the gravity part of it. [timeMillis] only needs to grow steadily
 * (the sensor's boot-time clock is fine).
 */
data class MotionSample(val timeMillis: Long, val acceleration: Vec3, val gravity: Vec3)

/** How upright the phone must be to count as "raised" (FR-3.5). Angles are in degrees. */
enum class Sensitivity(
    val title: String,
    val description: String,
    val minPitch: Double,
    val maxPitch: Double,
    val maxRoll: Double,
) {
    RELAXED("Relaxed", "Wider angles: stays on more easily, also when held low or sideways", 15.0, 85.0, 45.0),
    NORMAL("Normal", "Held up in front of you, the way you read a map", 25.0, 80.0, 35.0),
    STRICT("Strict", "Only when clearly held up and straight towards your face", 35.0, 70.0, 25.0),
}

/**
 * Tells whether the phone is "raised and facing you" (FR-3.5): the screen tilted up towards your
 * face, not turned far sideways, and in a hand rather than resting still on a table or stand.
 *
 * Feed it every accelerometer sample. The answer is debounced so a moment of looking away or a
 * quick wobble doesn't flicker the screen: it turns on after [ENTER_MILLIS] of holding the phone up
 * and off after [LEAVE_MILLIS] of not.
 *
 * Pure Kotlin (no Android types) so it's unit-tested with synthetic samples.
 */
class PostureDetector(private val sensitivity: Sensitivity = Sensitivity.NORMAL) {
    private var raised = false
    private var changingSince: Long? = null
    private var lastMovedAt: Long? = null

    /** Takes one sample and returns the debounced answer. */
    fun update(sample: MotionSample): Boolean {
        val now = sample.timeMillis
        if ((sample.acceleration - sample.gravity).length > MOTION_THRESHOLD) lastMovedAt = now
        val rawRaised = isUpright(sample.gravity) && isInHand(now)
        if (rawRaised == raised) {
            changingSince = null
        } else {
            val since = changingSince ?: now.also { changingSince = it }
            val wait = if (rawRaised) ENTER_MILLIS else LEAVE_MILLIS
            if (now - since >= wait) {
                raised = rawRaised
                changingSince = null
            }
        }
        return raised
    }

    /** Forgets everything, e.g. after the screen was off. */
    fun reset() {
        raised = false
        changingSince = null
        lastMovedAt = null
    }

    /** Pitch and roll within the [sensitivity]'s angles; screen facing down or upside down never counts. */
    private fun isUpright(gravity: Vec3): Boolean {
        val length = gravity.length
        if (length < MIN_GRAVITY) return false
        val pitch = pitchDegrees(gravity)
        val roll = rollDegrees(gravity)
        return pitch in sensitivity.minPitch..sensitivity.maxPitch && roll in -sensitivity.maxRoll..sensitivity.maxRoll
    }

    /** A hand always shakes a little; a table or stand doesn't. */
    private fun isInHand(now: Long): Boolean {
        val moved = lastMovedAt ?: return false
        return now - moved <= STILL_MILLIS
    }

    companion object {
        const val ENTER_MILLIS = 1_500L
        const val LEAVE_MILLIS = 3_000L

        /** No hand tremor for this long means the phone is resting somewhere. */
        const val STILL_MILLIS = 2_000L

        /** Movement (m/s², gravity removed) above sensor noise but below the smallest hand tremor. */
        const val MOTION_THRESHOLD = 0.05f

        /** Less than this means free fall or a broken reading, not a phone held still. */
        private const val MIN_GRAVITY = 1f

        /** 0° lying flat face up, 90° upright facing you, above 90° tipping over backwards. */
        fun pitchDegrees(gravity: Vec3): Double =
            Math.toDegrees(atan2(gravity.y.toDouble(), gravity.z.toDouble()))

        /** 0° level side to side, ±90° standing on one of its long edges. */
        fun rollDegrees(gravity: Vec3): Double {
            val length = gravity.length
            if (length == 0f) return 0.0
            return Math.toDegrees(asin((gravity.x / length).toDouble().coerceIn(-1.0, 1.0)))
        }
    }
}
