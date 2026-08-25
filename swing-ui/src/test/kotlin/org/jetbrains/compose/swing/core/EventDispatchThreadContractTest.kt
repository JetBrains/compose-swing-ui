package org.jetbrains.compose.swing.core

import androidx.compose.runtime.CompositionContext
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.setContentAsInteropHost
import org.jetbrains.compose.swing.setContentAsMenuInteropHost
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import javax.swing.JFrame
import javax.swing.JMenuBar
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Pins the thread-confinement contract of the composition entry points, both ways: every `setContent`
 * overload must run on the Swing Event Dispatch Thread, and so must disposing what it returns - a
 * disposal empties the caller's container, which is as much a Swing mutation as mounting into it. A
 * call from any other thread fails immediately with an [IllegalStateException] naming the offending
 * thread - instead of mutating Swing state off the EDT.
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

    @Test
    fun mountDisposalOffTheEventDispatchThreadFailsFast() {
        val panel = JPanel()
        lateinit var handle: DisposableHandle
        SwingUtilities.invokeAndWait { handle = panel.setContent { } }
        try {
            assertRejectedOffEdt { handle.dispose() }
        } finally {
            SwingUtilities.invokeAndWait { handle.dispose() }
        }
    }

    @Test
    fun recomposerDisposalOffTheEventDispatchThreadFailsFast() {
        val panel = JPanel()
        lateinit var recomposer: SwingRecomposer
        SwingUtilities.invokeAndWait { recomposer = SwingRecomposer.create(panel) }
        try {
            assertRejectedOffEdt { recomposer.dispose() }
        } finally {
            SwingUtilities.invokeAndWait { recomposer.dispose() }
        }
    }

    @Test
    fun interopHostDisposalOffTheEventDispatchThreadFailsFast() {
        val panel = JPanel()
        lateinit var recomposer: SwingRecomposer
        lateinit var handle: DisposableHandle
        SwingUtilities.invokeAndWait {
            recomposer = SwingRecomposer.create(panel)
            handle = panel.setContentAsInteropHost(recomposer.compositionContext) { }
        }
        try {
            assertRejectedOffEdt { handle.dispose() }

            // The disposal empties the host and withdraws the context it published on it, so a rejected
            // one must have touched neither. Read on the EDT, asserted off it.
            var published: CompositionContext? = null
            SwingUtilities.invokeAndWait { published = panel.findParentCompositionContext() }
            assertSame(
                recomposer.compositionContext,
                published,
                "a disposal rejected off the Event Dispatch Thread must leave the interop host's " +
                    "published context standing",
            )
        } finally {
            SwingUtilities.invokeAndWait {
                handle.dispose()
                recomposer.dispose()
            }
        }
    }

    @Test
    fun menuInteropHostDisposalOffTheEventDispatchThreadFailsFast() {
        val menuBar = JMenuBar()
        lateinit var recomposer: SwingRecomposer
        lateinit var handle: DisposableHandle
        SwingUtilities.invokeAndWait {
            recomposer = SwingRecomposer.create(menuBar)
            handle = menuBar.setContentAsMenuInteropHost(recomposer.compositionContext) { }
        }
        try {
            // The handle disposes only what the call mounted, so the thread it demands is that
            // content's own.
            assertRejectedOffEdt { handle.dispose() }
        } finally {
            SwingUtilities.invokeAndWait {
                handle.dispose()
                recomposer.dispose()
            }
        }
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
        worker.join(JOIN_TIMEOUT.inWholeMilliseconds)

        val thrown = assertNotNull(failure, "the call off the Event Dispatch Thread must fail")
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
        val JOIN_TIMEOUT = 10.seconds
    }
}
