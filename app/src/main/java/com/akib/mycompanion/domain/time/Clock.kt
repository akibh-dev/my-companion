package com.akib.mycompanion.domain.time

import java.time.ZoneId

/** Abstracts "now" so time-based rules can be tested with a fixed clock. */
interface Clock {
    fun nowMillis(): Long

    val zone: ZoneId
}
