package org.jetbrains.compose.swing.window

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [application] blocks the calling thread until its composition exits, and that composition's
 * recomposer runs on the Event Dispatch Thread. Called from there, the wait and the work it waits
 * on deadlock with no diagnostic, so the entry point must fail fast instead.
 *
 * The posted call is driven through a latch rather than [SwingUtilities.invokeAndWait]: if the
 * check regresses, the posted runnable hangs the Event Dispatch Thread forever, and only a
 * latch with a bounded timeout lets the test fail instead of hanging the suite alongside it.
 */
class ApplicationBlockingThreadCheckTest {
    @Test
    fun applicationFailsFastInsteadOfHangingWhenCalledOnTheEventDispatchThread() {
        val latch = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()

        SwingUtilities.invokeLater {
            try {
                application(exitProcessOnExit = false) {}
            } catch (thrown: Throwable) {
                failure.set(thrown)
            } finally {
                latch.countDown()
            }
        }

        assertTrue(
            latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
            "application() called on the Event Dispatch Thread must fail fast instead of hanging it",
        )

        val thrown =
            assertNotNull(
                failure.get(),
                "application() must throw when called on the Event Dispatch Thread",
            )
        val message = thrown.message
        assertTrue(
            message != null && message.contains("must not be called on the Event Dispatch Thread"),
            "expected the failure to name the Event Dispatch Thread, but was: $message",
        )
    }

    private companion object {
        const val TIMEOUT_SECONDS = 10L
    }
}
