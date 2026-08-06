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
import org.jetbrains.compose.swing.core.findParentCompositionContext
import org.jetbrains.compose.swing.core.getOrCreateRecomposer
import org.jetbrains.compose.swing.core.recomposerOrNull
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
import kotlin.time.Duration.Companion.seconds

/**
 * Behavioral tests for the per-window recomposer on the real-window path: a realized top-level
 * [java.awt.Window] whose islands resolve their composition through [getOrCreateRecomposer]. These
 * tests realize a real [JFrame] so the on-window resolution, memoization, and window-close teardown
 * all run against a live peer.
 *
 * Each case skips (reports SKIPPED) on a headless environment, since it needs a real top-level peer.
 * Realized frames are disposed on every exit path so no peer leaks. The window recomposer runs on the
 * window's own Swing frame-clock timer, so the test body runs on the EDT and yields it back between
 * checks until a bounded deadline.
 */
class WindowRecomposerTest {
    @Test
    fun twoIslandsUnderOneRealWindowShareOneRecomposer() = runBlocking(Dispatchers.Swing) {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        try {
            // Two islands share one Compose state. If both join the window's single recomposer, one
            // state change recomposes both; if each had spun up its own runtime, one clock could not
            // drive the other.
            var shared by mutableStateOf("v0")
            val islandA = onEdtChild(frame)
            val islandB = onEdtChild(frame)
            islandA.setContent { Label(text = "a=$shared") }
            islandB.setContent { Label(text = "b=$shared") }

            awaitUntil("both islands render the initial shared state") {
                labelTextOrNull(islandA) == "a=v0" && labelTextOrNull(islandB) == "b=v0"
            }

            val windowRecomposer = frame.recomposerOrNull()
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
            assertNull(
                frame.recomposerOrNull(),
                "a window that nothing has mounted into must have no recomposer yet",
            )

            val first = frame.getOrCreateRecomposer()
            val second = frame.getOrCreateRecomposer()
            assertSame(first, second, "getOrCreateRecomposer must memoize one runtime per window")

            // Also observable through the non-creating lookup.
            val memoized = frame.recomposerOrNull()
            assertNotNull(memoized, "the created recomposer must be memoized on the window")
            assertSame(
                first.recomposer,
                memoized.recomposer,
                "recomposerOrNull must return the recomposer getOrCreateRecomposer created",
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
            frame.getOrCreateRecomposer()
            assertNotNull(
                frame.recomposerOrNull(),
                "resolving the window must have created its recomposer",
            )

            // Disposing the window fires windowClosed, whose teardown clears the memoized slot.
            frame.dispose()
            awaitUntil("the closed window clears its recomposer slot") { frame.recomposerOrNull() == null }
            assertNull(
                frame.recomposerOrNull(),
                "closing the window must clear its recomposer slot",
            )
        } finally {
            frame.dispose()
        }
    }

    /**
     * A realized, off-screen [JFrame] with a live peer. Packing realizes the peer without showing it,
     * so disposing it fires the `windowClosed` event the recomposer teardown listens for. Must be
     * called on the EDT.
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
     * frame-clock timer can fire and mount or recompose content. A condition that never becomes true
     * fails the test at the deadline, naming [description], instead of hanging.
     */
    private suspend fun awaitUntil(
        description: String,
        condition: () -> Boolean,
    ) {
        try {
            withTimeout(SETTLE_TIMEOUT) {
                while (!condition()) {
                    yield()
                }
            }
        } catch (timedOut: TimeoutCancellationException) {
            throw AssertionError("Timed out after $SETTLE_TIMEOUT waiting until $description", timedOut)
        }
    }

    private companion object {
        const val FRAME_SIZE: Int = 200
        val SETTLE_TIMEOUT = 10.seconds
    }
}
