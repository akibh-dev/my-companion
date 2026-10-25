package com.akib.mycompanion.core.time

import java.time.ZoneId
import javax.inject.Inject

/** Wall-clock time, swappable in tests. */
interface Clock {
    fun nowMillis(): Long

    val zone: ZoneId
}

class SystemClock @Inject constructor() : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()

    override val zone: ZoneId get() = ZoneId.systemDefault()
}
