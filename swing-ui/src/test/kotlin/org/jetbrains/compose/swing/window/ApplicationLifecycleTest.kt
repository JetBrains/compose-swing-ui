package org.jetbrains.compose.swing.window

import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Behavioral coverage for the application lifecycle entry points, which run entirely on a Compose
 * recomposer over a non-visual [ApplicationScope] composition and so are exercisable headless without
 * any real window.
 *
 * The visual entry points that realize AWT peers ([Window], [Dialog],
 * [org.jetbrains.compose.swing.components.Tray]) are NOT covered here: each constructs a real
 * `JFrame` / `JDialog` / system-tray peer and so needs a display; their lifecycle contract is
 * asserted in [ApplicationWindowKeepAliveTest], which skips where the environment lacks one. The
 * application loop itself needs no peer - it spins a recomposer on the EDT - so its start/exit
 * contract is asserted by observable effects: the suspending entry point returns once the scope
 * requests exit, and the scope-bound coroutine variant completes its job.
 */
class ApplicationLifecycleTest {
    @Test
    fun awaitApplicationRunsContentThenReturnsWhenExitIsRequested() = runBlocking {
        var contentComposed = false
        var sawWindow = true

        // Bounded so a lifecycle that never settles fails fast instead of hanging the suite.
        withTimeout(WALL_CLOCK_TIMEOUT) {
            awaitApplication {
                contentComposed = true
                // LocalWindow is null under a bare application scope that has created no Window;
                // read it to prove the documented default reaches application-scope content.
                sawWindow = LocalWindow.current != null
                LaunchedEffect(Unit) {
                    // Requesting exit drops the content and lets awaitApplication unwind.
                    exitApplication()
                }
            }
        }

        assertTrue(contentComposed, "awaitApplication must compose its content")
        assertFalse(
            sawWindow,
            "LocalWindow must be null in a bare application scope with no Window",
        )
    }

    @Test
    fun launchApplicationCompletesItsJobWhenExitIsRequested() = runBlocking {
        val scope = CoroutineScope(coroutineContext + Job())
        try {
            val job = scope.launchApplication { LaunchedEffect(Unit) { exitApplication() } }
            withTimeout(WALL_CLOCK_TIMEOUT) { job.join() }
            assertTrue(job.isCompleted, "the launched application job must complete after exit")
        } finally {
            scope.coroutineContext[Job]?.cancel()
        }
    }

    private companion object {
        val WALL_CLOCK_TIMEOUT = 10.seconds
    }
}
