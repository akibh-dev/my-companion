package com.akib.mycompanion.core.health

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.akib.mycompanion.core.log.AppLog
import com.akib.mycompanion.core.time.Clock
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Records that the app is alive every 15 minutes (the shortest WorkManager period), so gaps show
 * when Android or the phone maker stopped it (spike S6, NFR-7).
 */
@Singleton
class HeartbeatLog internal constructor(private val file: File, private val clock: Clock) {

    @Inject constructor(@ApplicationContext context: Context, clock: Clock) : this(
        File(context.filesDir, "heartbeats.txt"),
        clock
    )

    suspend fun beat() = withContext(Dispatchers.IO) {
        val beats = (times() + clock.nowMillis()).takeLast(MAX_BEATS)
        file.parentFile?.mkdirs()
        file.writeText(beats.joinToString("\n"))
    }

    suspend fun times(): List<Long> = withContext(Dispatchers.IO) {
        if (!file.exists()) emptyList() else file.readLines().mapNotNull { it.trim().toLongOrNull() }
    }

    /** Gaps longer than [thresholdMillis] between beats, and since the last beat until now. */
    suspend fun gaps(thresholdMillis: Long = GAP_MILLIS): List<Gap> {
        val beats = times()
        val pairs = beats.zipWithNext() + listOfNotNull(beats.lastOrNull()?.let { it to clock.nowMillis() })
        return pairs.filter { (from, to) -> to - from > thresholdMillis }.map { (from, to) -> Gap(from, to) }
    }

    data class Gap(val fromMillis: Long, val toMillis: Long)

    companion object {
        const val MAX_BEATS = 500
        const val GAP_MILLIS = 45 * 60 * 1000L
    }
}

/** Gets its dependencies through a Hilt entry point, so WorkManager's default factory can create it. */
class HeartbeatWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun heartbeats(): HeartbeatLog

        fun log(): AppLog
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        deps.heartbeats().beat()
        deps.log().i("Heartbeat", "alive")
        return Result.success()
    }

    companion object {
        private const val NAME = "heartbeat"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<HeartbeatWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
