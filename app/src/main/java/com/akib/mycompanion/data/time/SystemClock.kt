package com.akib.mycompanion.data.time

import com.akib.mycompanion.domain.time.Clock
import java.time.ZoneId
import javax.inject.Inject

class SystemClock @Inject constructor() : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()

    override val zone: ZoneId get() = ZoneId.systemDefault()
}
