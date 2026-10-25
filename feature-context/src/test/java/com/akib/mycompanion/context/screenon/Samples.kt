package com.akib.mycompanion.context.screenon

import kotlin.math.cos
import kotlin.math.sin

/** Synthetic sensor samples: a phone held at a given angle, shaking a little in a hand or lying still. */
object Samples {
    private const val G = 9.81

    /** Gravity as the phone reads it when tilted [pitch]° up from flat and rolled [roll]° sideways. */
    fun gravity(pitch: Double, roll: Double = 0.0): Vec3 {
        val p = Math.toRadians(pitch)
        val r = Math.toRadians(roll)
        val horizontal = G * cos(r)
        return Vec3((G * sin(r)).toFloat(), (horizontal * sin(p)).toFloat(), (horizontal * cos(p)).toFloat())
    }

    /** A hand adds a small wobble (±0.3 m/s², alternating) on top of gravity; a table adds none. */
    fun sample(time: Long, pitch: Double, roll: Double = 0.0, shaking: Boolean = true): MotionSample {
        val gravity = gravity(pitch, roll)
        val wobble = if (!shaking) 0f else if ((time / STEP) % 2 == 0L) 0.3f else -0.3f
        return MotionSample(time, Vec3(gravity.x + wobble, gravity.y, gravity.z), gravity)
    }

    /** Sample times every [STEP] ms from [from] to [to], both included. */
    fun times(from: Long, to: Long): LongProgression = from..to step STEP

    const val STEP = 100L
}
