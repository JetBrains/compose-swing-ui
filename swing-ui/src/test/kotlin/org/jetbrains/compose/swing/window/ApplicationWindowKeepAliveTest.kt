package org.jetbrains.compose.swing.window

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Tray
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.awt.GraphicsEnvironment
import java.awt.SystemTray
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Behavioral coverage for the lifecycle contract that a [Window], [Dialog], or [Tray] in the
 * composition keeps the enclosing application running. The application loop runs only while its
 * recomposer's effect job has an active child coroutine; a top-level host mounts its content or peer
 * without launching such a coroutine itself, so each host must hold a parked keep-alive effect to
 * remain a reason for the application to stay open until it leaves the composition.
 *
 * Realizing a real top-level peer needs a display, so each case skips on a headless environment; the
 * tray case additionally skips where the platform provides no system tray.
 */
class ApplicationWindowKeepAliveTest {
    @Test
    fun applicationStaysAliveWhileAWindowIsOpenThenExitsOnRequest() = runBlocking {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        lateinit var scope: ApplicationScope
        val appJob =
            launch {
                awaitApplication {
                    scope = this
                    Window(onCloseRequest = ::exitApplication, visible = false) { Label("hi") }
                }
            }
        settleApplication()
        assertTrue(appJob.isActive, "application exited while a Window was still in the composition")

        withContext(Dispatchers.Swing) { scope.exitApplication() }
        withTimeout(EXIT_TIMEOUT) { appJob.join() }
        assertTrue(appJob.isCompleted, "application did not exit after exitApplication()")
    }

    @Test
    fun applicationStaysAliveWhileADialogIsOpenThenExitsOnRequest() = runBlocking {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        lateinit var scope: ApplicationScope
        val appJob =
            launch {
                awaitApplication {
                    scope = this
                    Dialog(onCloseRequest = ::exitApplication, visible = false) { Label("hi") }
                }
            }
        settleApplication()
        assertTrue(appJob.isActive, "application exited while a Dialog was still in the composition")

        withContext(Dispatchers.Swing) { scope.exitApplication() }
        withTimeout(EXIT_TIMEOUT) { appJob.join() }
        assertTrue(appJob.isCompleted, "application did not exit after exitApplication()")
    }

    @Test
    fun applicationStaysAliveWhileATrayIsPresentThenExitsOnRequest() = runBlocking {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        assumeTrue(SystemTray.isSupported(), "Registering a tray icon requires a platform system tray")
        val icon = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        lateinit var scope: ApplicationScope
        val appJob =
            launch {
                awaitApplication {
                    scope = this
                    Tray(image = icon)
                }
            }
        try {
            settleApplication()
            assertTrue(appJob.isActive, "application exited while a Tray was still in the composition")

            withContext(Dispatchers.Swing) { scope.exitApplication() }
            withTimeout(EXIT_TIMEOUT) { appJob.join() }
            assertTrue(appJob.isCompleted, "application did not exit after exitApplication()")
        } finally {
            // The tray icon is a real peer in the platform's status area; canceling the application
            // on every exit path disposes its composition and removes the icon, so a failure cannot
            // leak the icon into the user's environment.
            appJob.cancel()
            withTimeout(EXIT_TIMEOUT) { appJob.join() }
        }
    }

    /**
     * Settles the freshly launched application without waiting on the wall clock: each round trip
     * hands the event dispatch thread a turn (running the application's queued composition and effect
     * work) and then yields this scope so a completed application job can resume and be observed. An
     * application with no reason to stay open completes well within these bounded round trips, so
     * asserting the job is still active afterwards is meaningful.
     */
    private suspend fun settleApplication() {
        repeat(SETTLE_ROUND_TRIPS) {
            withContext(Dispatchers.Swing) { yield() }
            yield()
        }
    }

    private companion object {
        const val SETTLE_ROUND_TRIPS: Int = 40
        val EXIT_TIMEOUT = 5.seconds
    }
}
