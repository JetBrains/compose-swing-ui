package org.jetbrains.compose.swing.swingmark.harness

private const val MILLIS_PER_SECOND = 1000L
private const val NANOS_PER_SECOND = 1_000_000_000L
private const val IDLE_LIMIT_SECONDS = 15L
private const val TIMED_OUT = 2

/**
 * Gives up on a run that has stopped making progress, and says where the event dispatch thread was.
 *
 * Bounds time without progress rather than total time, so a long run is left alone and a stuck one is
 * reported within [IDLE_LIMIT_SECONDS].
 */
internal object Watchdog {
    @Volatile
    private var lastProgress: Long = System.nanoTime()

    /** Records that something happened. Called whenever a wait or a posted change has come back. */
    fun progress() {
        lastProgress = System.nanoTime()
    }

    fun start() {
        val watchdog =
            Thread {
                while (true) {
                    Thread.sleep(MILLIS_PER_SECOND)
                    if (System.nanoTime() - lastProgress > IDLE_LIMIT_SECONDS * NANOS_PER_SECOND) {
                        println("No progress for ${IDLE_LIMIT_SECONDS}s. The event dispatch thread is at:")
                        println(eventThreadStack())
                        Runtime.getRuntime().halt(TIMED_OUT)
                    }
                }
            }
        watchdog.isDaemon = true
        watchdog.start()
    }
}
