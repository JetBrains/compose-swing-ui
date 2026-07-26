package org.jetbrains.compose.swing.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.setContent
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Container
import java.awt.GraphicsEnvironment
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Behavioral tests for the per-window recomposer on the real-window path: a realized top-level
 * [java.awt.Window] whose islands resolve their composition through
 * [Window.getOrCreateWindowRecomposer]. Unlike the off-screen sharing tests, these tests realize a
 * real [JFrame] so the on-window resolution, memoization, and window-close teardown all run against a
 * live peer.
 *
 * Every case realizes a real top-level peer, so each skips (reports SKIPPED) on a headless
 * environment. Realized frames are disposed on every exit path so no peer leaks. The window
 * recomposer is driven by the window's own real Swing frame-clock timer; the test body runs on the
 * EDT and yields it back between checks, letting that timer fire, until a bounded deadline.
 */
class WindowRecomposerTest {
    @Test
    fun twoIslandsUnderOneRealWindowShareOneRecomposer() = runBlocking(Dispatchers.Swing) {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        try {
            // Two independent islands mounted into the same realized window, each reading ONE shared
            // Compose state. If both islands join the window's single recomposer, driving a single
            // state change recomposes BOTH; if each island had spun up its own runtime, one clock
            // could not drive the other.
            var shared by mutableStateOf("v0")
            val islandA = onEdtChild(frame)
            val islandB = onEdtChild(frame)
            islandA.setContent { Label(text = "a=$shared") }
            islandB.setContent { Label(text = "b=$shared") }

            awaitUntil("both islands render the initial shared state") {
                labelTextOrNull(islandA) == "a=v0" && labelTextOrNull(islandB) == "b=v0"
            }

            // The window created exactly one recomposer, and both islands resolved to it.
            val windowRecomposer = frame.windowRecomposerOrNull()
            assertNotNull(windowRecomposer, "mounting an island into a realized window must create its recomposer")
            assertSame(
                windowRecomposer.recomposer,
                islandA.findParentCompositionContext(),
                "island A did not resolve to the window's shared recomposer",
            )
            assertSame(
                windowRecomposer.recomposer,
                islandB.findParentCompositionContext(),
                "island B did not resolve to the window's shared recomposer",
            )

            shared = "v1"
            awaitUntil("both islands recompose to the changed shared state") {
                labelTextOrNull(islandA) == "a=v1" && labelTextOrNull(islandB) == "b=v1"
            }
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun theRecomposerIsCreatedLazilyAndMemoizedPerWindow() = runBlocking(Dispatchers.Swing) {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        try {
            // Nothing has resolved the window yet, so no recomposer exists.
            assertNull(
                frame.windowRecomposerOrNull(),
                "a window that nothing has mounted into must have no recomposer yet",
            )

            // First resolution creates it; a second resolution returns the very same recomposer.
            val first = frame.getOrCreateWindowRecomposer()
            val second = frame.getOrCreateWindowRecomposer()
            assertSame(first, second, "getOrCreateWindowRecomposer must memoize one recomposer per window")

            // And it is now observable through the non-creating lookup.
            val memoized = frame.windowRecomposerOrNull()
            assertNotNull(memoized, "the created recomposer must be memoized on the window")
            assertSame(
                first,
                memoized.recomposer,
                "windowRecomposerOrNull must return the recomposer getOrCreateWindowRecomposer created",
            )
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun closingTheWindowTearsTheRecomposerDown() = runBlocking(Dispatchers.Swing) {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        try {
            frame.getOrCreateWindowRecomposer()
            assertNotNull(
                frame.windowRecomposerOrNull(),
                "resolving the window must have created its recomposer",
            )

            // Disposing the realized window fires windowClosed, whose teardown clears the memoized
            // slot: the very next lookup finds nothing again.
            frame.dispose()
            awaitUntil("the closed window clears its recomposer slot") { frame.windowRecomposerOrNull() == null }
            assertNull(
                frame.windowRecomposerOrNull(),
                "closing the window must clear its recomposer slot",
            )
        } finally {
            frame.dispose()
        }
    }

    /**
     * A realized, off-screen [JFrame] with a live peer. Packing realizes the peer without showing the
     * frame, so disposing it fires the `windowClosed` event the recomposer teardown listens for while
     * never flashing a window on screen. Must be called on the EDT.
     */
    private fun realizedFrame(): JFrame = JFrame().apply {
        defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
        setBounds(0, 0, FRAME_SIZE, FRAME_SIZE)
        pack()
    }

    /** Adds and returns a fresh island container inside [frame]'s content pane. Must be on the EDT. */
    private fun onEdtChild(frame: JFrame): Container = JPanel().also { frame.contentPane.add(it) }

    /** The single [JLabel]'s text in [container]'s subtree, or `null` while none has mounted yet. */
    private fun labelTextOrNull(container: Container): String? {
        val labels = mutableListOf<JLabel>()

        fun visit(c: Container) {
            for (child in c.components) {
                if (child is JLabel) labels += child
                if (child is Container) visit(child)
            }
        }
        visit(container)
        return labels.singleOrNull()?.text
    }

    /**
     * Suspends on the EDT until [condition] holds, yielding the EDT back between checks so the window's
     * real frame-clock timer can fire and the window recomposer can mount and recompose content. A
     * condition that never becomes true fails the test at the deadline, naming [description], instead
     * of hanging.
     */
    private suspend fun awaitUntil(
        description: String,
        condition: () -> Boolean,
    ) {
        try {
            withTimeout(SETTLE_TIMEOUT_MILLIS) {
                while (!condition()) {
                    yield()
                }
            }
        } catch (timedOut: TimeoutCancellationException) {
            throw AssertionError("Timed out after ${SETTLE_TIMEOUT_MILLIS}ms waiting until $description", timedOut)
        }
    }

    private companion object {
        const val FRAME_SIZE: Int = 200
        const val SETTLE_TIMEOUT_MILLIS: Long = 10_000
    }
}
