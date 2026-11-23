package dev.akibh.mycompanion.core.log

/**
 * Writes an uncaught exception to [AppLog] (and waits for it to reach the disk) before handing it
 * to the handler that was there before, which still shows the crash dialog and ends the process.
 * Without it a crash only ever reached logcat, which is gone by the time anyone looks.
 */
class CrashLogger(
    private val log: AppLog,
    private val previous: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, error: Throwable) {
        try {
            log.e(TAG, "crashed on ${thread.name}${where(error)}", error)
            log.flush(FLUSH_TIMEOUT_MILLIS)
        } catch (ignored: Throwable) {
            // Logging must never stop the crash reaching the previous handler.
        }
        previous?.uncaughtException(thread, error)
    }

    companion object {
        const val TAG = "Crash"
        private const val FLUSH_TIMEOUT_MILLIS = 1_000L
        private const val FRAMES = 4

        /** Chains onto the current default handler; call once, early in `Application.onCreate`. */
        fun install(log: AppLog): CrashLogger =
            CrashLogger(
                log,
                Thread.getDefaultUncaughtExceptionHandler()
            ).also(Thread::setDefaultUncaughtExceptionHandler)

        /** The top frames of the innermost cause: the log keeps one line per entry, not the whole trace. */
        internal fun where(error: Throwable): String {
            var root = error
            while (root.cause != null && root.cause !== root) root = root.cause!!
            val frames = root.stackTrace.take(FRAMES)
            if (frames.isEmpty()) return ""
            val cause = if (root !== error) " caused by ${root.javaClass.simpleName}: ${root.message}" else ""
            return "$cause at " + frames.joinToString(" < ") {
                "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}"
            }
        }
    }
}
