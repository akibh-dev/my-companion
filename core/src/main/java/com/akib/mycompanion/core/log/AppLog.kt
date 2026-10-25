package com.akib.mycompanion.core.log

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class LogLevel(val letter: Char) { INFO('I'), WARN('W'), ERROR('E') }

data class LogLine(val time: String, val level: LogLevel, val tag: String, val message: String)

/**
 * The app's own log, kept on the phone and shown in the app (plan §3: no crash-reporting service).
 * Two files of at most [MAX_BYTES] each: when the current one is full it becomes the old one.
 * Writes happen on one background thread, in order.
 */
@Singleton
class AppLog(private val dir: File, private val mirrorToLogcat: Boolean) {

    @Inject constructor(@ApplicationContext context: Context) : this(
        File(context.filesDir, "logs"),
        mirrorToLogcat = true
    )

    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AppLog").apply {
            isDaemon = true
        }
    }
    private val current get() = File(dir, "app.log")
    private val previous get() = File(dir, "app.1.log")

    fun i(tag: String, message: String) = write(LogLevel.INFO, tag, message, null)

    fun w(tag: String, message: String, error: Throwable? = null) = write(LogLevel.WARN, tag, message, error)

    fun e(tag: String, message: String, error: Throwable? = null) = write(LogLevel.ERROR, tag, message, error)

    /** Oldest first, across both files. */
    suspend fun read(): List<LogLine> = withContext(Dispatchers.IO) {
        flush()
        listOf(previous, current).filter { it.exists() }.flatMap { it.readLines() }.mapNotNull(::parse)
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        flush()
        previous.delete()
        current.delete()
    }

    /** Waits until everything written so far is on disk. */
    fun flush() {
        writer.submit {}.get()
    }

    private fun write(level: LogLevel, tag: String, message: String, error: Throwable?) {
        if (mirrorToLogcat) {
            when (level) {
                LogLevel.INFO -> Log.i(tag, message, error)
                LogLevel.WARN -> Log.w(tag, message, error)
                LogLevel.ERROR -> Log.e(tag, message, error)
            }
        }
        val time = FORMAT.format(Instant.now())
        val text = buildString {
            append(message.replace('\n', ' '))
            if (error != null) {
                append(
                    " ("
                ).append(error.javaClass.simpleName).append(": ").append(error.message).append(')')
            }
        }
        writer.execute {
            runCatching {
                dir.mkdirs()
                if (current.length() > MAX_BYTES) {
                    previous.delete()
                    current.renameTo(previous)
                }
                current.appendText("$time ${level.letter} $tag: $text\n")
            }
        }
    }

    companion object {
        const val MAX_BYTES = 256 * 1024L
        private val FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
        private val LINE = Regex("""^(\S+ \S+) ([IWE]) ([^:]+): (.*)$""")

        fun parse(line: String): LogLine? {
            val match = LINE.matchEntire(line) ?: return null
            val (time, level, tag, message) = match.destructured
            return LogLine(time, LogLevel.entries.first { it.letter == level[0] }, tag, message)
        }
    }
}
