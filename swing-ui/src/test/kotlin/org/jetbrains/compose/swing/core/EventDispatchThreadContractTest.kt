package org.jetbrains.compose.swing.core

import org.jetbrains.compose.swing.setContent
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import javax.swing.JFrame
import javax.swing.JMenuBar
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Pins the thread-confinement contract of the composition entry points: every `setContent` overload
 * must run on the Swing Event Dispatch Thread, and a call from any other thread fails immediately
 * with an [IllegalStateException] naming the offending thread - instead of mounting a composition
 * whose applier would then mutate Swing state off the EDT.
 *
 * Each case performs the call on a named worker thread and joins it, so the assertions run on the
 * test thread against the failure the worker actually observed.
 */
class EventDispatchThreadContractTest {
    @Test
    fun containerSetContentOffTheEventDispatchThreadFailsFast() {
        val panel = JPanel()
        assertRejectedOffEdt { panel.setContent { } }
    }

    @Test
    fun windowSetContentOffTheEventDispatchThreadFailsFast() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = JFrame()
        try {
            assertRejectedOffEdt { frame.setContent { } }
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun menuBarSetContentOffTheEventDispatchThreadFailsFast() {
        val menuBar = JMenuBar()
        assertRejectedOffEdt { menuBar.setContent { } }
    }

    /**
     * Runs [call] on a worker thread named [OFF_EDT_THREAD_NAME] and asserts it was rejected with the
     * thread-confinement failure, which names the calling thread so the author can locate the call.
     */
    private fun assertRejectedOffEdt(call: () -> Unit) {
        var failure: Throwable? = null
        val worker =
            Thread({ failure = runCatching(call).exceptionOrNull() }, OFF_EDT_THREAD_NAME)
        worker.start()
        worker.join(JOIN_TIMEOUT_MILLIS)

        val thrown = assertNotNull(failure, "setContent off the Event Dispatch Thread must fail")
        assertIs<IllegalStateException>(thrown, "the off-EDT call must be rejected as illegal state")
        val message = assertNotNull(thrown.message, "the failure must carry a diagnostic message")
        assertTrue(
            "Event Dispatch Thread" in message,
            "the failure must state the Event Dispatch Thread requirement, but was: $message",
        )
        assertTrue(
            OFF_EDT_THREAD_NAME in message,
            "the failure must name the calling thread, but was: $message",
        )
    }

    private companion object {
        const val OFF_EDT_THREAD_NAME: String = "off-edt-caller"
        const val JOIN_TIMEOUT_MILLIS: Long = 10_000
    }
}
