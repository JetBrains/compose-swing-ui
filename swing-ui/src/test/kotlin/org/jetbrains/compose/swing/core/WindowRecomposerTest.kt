package org.jetbrains.compose.swing.core

import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.window.LocalWindow
import org.jetbrains.compose.swing.window.Window
import org.jetbrains.compose.swing.window.awaitApplication
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.BorderLayout
import java.awt.Container
import java.awt.GraphicsEnvironment
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JMenuBar
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.seconds
import java.awt.Window as AwtWindow

/**
 * Behavioral tests for the per-window recomposer on the real-window path: a realized top-level
 * [java.awt.Window] whose content compositions resolve their parent through [getOrCreateRecomposer].
 * These tests realize a real [JFrame] so the on-window resolution, memoization, and window-close
 * teardown all run against a live peer.
 *
 * Each case skips (reports SKIPPED) on a headless environment, since it needs a real top-level peer.
 * Realized frames are disposed on every exit path so no peer leaks. The window recomposer runs on the
 * window's own Swing frame-clock timer, so the test body runs on the EDT and yields it back between
 * checks until a bounded deadline.
 */
class WindowRecomposerTest {
    @Test
    fun twoCompositionsUnderOneRealWindowShareOneRecomposer() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        try {
            // Two content compositions share one Compose state. If both join the window's single
            // recomposer, one state change recomposes both; if each had spun up its own recomposer,
            // one clock could not drive the other.
            var shared by mutableStateOf("v0")
            val compositionA = onEdtChild(frame)
            val compositionB = onEdtChild(frame)
            compositionA.setContent { Label(text = "a=$shared") }
            compositionB.setContent { Label(text = "b=$shared") }

            awaitUntil("both content compositions render the initial shared state") {
                labelTextOrNull(compositionA) == "a=v0" && labelTextOrNull(compositionB) == "b=v0"
            }

            val windowRecomposer = frame.swingRecomposerOrNull()
            assertNotNull(windowRecomposer, "mounting content into a realized window must create its recomposer")
            assertSame(
                windowRecomposer.recomposer,
                compositionA.findParentCompositionContext(),
                "composition A did not resolve to the window's shared recomposer",
            )
            assertSame(
                windowRecomposer.recomposer,
                compositionB.findParentCompositionContext(),
                "composition B did not resolve to the window's shared recomposer",
            )

            shared = "v1"
            awaitUntil("both content compositions recompose to the changed shared state") {
                labelTextOrNull(compositionA) == "a=v1" && labelTextOrNull(compositionB) == "b=v1"
            }
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun theRecomposerIsCreatedLazilyAndMemoizedPerWindow() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        try {
            assertNull(
                frame.swingRecomposerOrNull(),
                "a window that nothing has mounted into must have no recomposer yet",
            )

            val first = frame.getOrCreateRecomposer()
            val second = frame.getOrCreateRecomposer()
            assertSame(first, second, "getOrCreateRecomposer must memoize one recomposer per window")

            // Also observable through the non-creating lookup.
            val memoized = frame.swingRecomposerOrNull()
            assertNotNull(memoized, "the created recomposer must be memoized on the window")
            assertSame(
                first.recomposer,
                memoized.recomposer,
                "swingRecomposerOrNull must return the recomposer getOrCreateRecomposer created",
            )
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun aClosedWindowHoldsARecomposerOnlyWhileContentComposesInIt() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        val composition = onEdtChild(frame)
        try {
            frame.getOrCreateRecomposer()
            frame.dispose()
            awaitUntil("the closed window clears its recomposer slot") { frame.swingRecomposerOrNull() == null }

            // A closed window announces no second end - Window.dispose() posts windowClosed only while
            // the window is displayable - so nothing but the content registered with it ends a recomposer
            // created for it now. Content composes where it stands all the same, reporting CREATED until
            // a window is realized around it.
            val handle = composition.setContent { Label(text = "composed") }
            awaitUntil("content mounted into a closed window composes where it stands") {
                labelTextOrNull(composition) == "composed"
            }
            assertNotNull(
                frame.swingRecomposerOrNull(),
                "mounting into a closed window must give it a recomposer, so that the disposal below is " +
                    "what ends one rather than finding none",
            )

            // Registered content is the whole of what holds that recomposer: disposing the last of it
            // leaves the closed window holding nothing, rather than one settling the toolkit's events
            // and holding its clock for the life of the process.
            handle.dispose()
            awaitUntil("disposing the last content composition ends the closed window's recomposer") {
                frame.swingRecomposerOrNull() == null
            }
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun compositionsInAWindowThatHasNoPeerYetShareTheRecomposerTheFirstOneCreated() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        // A window that is no RootPaneContainer stamps its scope nowhere, so the second content
        // composition resolves its parent from the window itself. The window must answer with the
        // recomposer the first one created, rather than wait for a peer it does not need to own one.
        val window = AwtWindow(null)
        val first = JPanel().also { window.add(it, BorderLayout.NORTH) }
        val second = JPanel().also { window.add(it, BorderLayout.SOUTH) }
        try {
            first.setContent { Label(text = "first") }
            val recomposer =
                assertNotNull(
                    window.swingRecomposerOrNull(),
                    "the first content composition must create the window's recomposer",
                )

            second.setContent { Label(text = "second") }

            assertSame(
                recomposer,
                window.swingRecomposerOrNull(),
                "both content compositions must compose on the one recomposer the window owns",
            )
            assertEquals(
                "second",
                labelTextOrNull(second),
                "a content composition in a window with no peer must compose",
            )
        } finally {
            // Realized before it is disposed, so the window posts the windowClosed its recomposer ends from.
            window.pack()
            window.dispose()
        }
    }

    @Test
    fun closingTheWindowTearsTheRecomposerDown() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        try {
            frame.getOrCreateRecomposer()
            assertNotNull(
                frame.swingRecomposerOrNull(),
                "resolving the window must have created its recomposer",
            )

            // Disposing the window fires windowClosed, whose teardown clears the memoized slot.
            frame.dispose()
            awaitUntil("the closed window clears its recomposer slot") { frame.swingRecomposerOrNull() == null }
            assertNull(
                frame.swingRecomposerOrNull(),
                "closing the window must clear its recomposer slot",
            )
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun aWindowLeftWithNoContentDisposesItsRecomposer() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        try {
            val composition = onEdtChild(frame)
            val handle = composition.setContent { Label(text = "only") }
            awaitUntil("the window's only content composes") { labelTextOrNull(composition) == "only" }
            val first = assertNotNull(frame.swingRecomposerOrNull())

            // Nobody is handed a window's recomposer, so nothing outside could know when to end it: it
            // disposes itself once the last content composition registered with it is gone, on the turn
            // after the one that took it away.
            handle.dispose()
            awaitUntil("the emptied window disposes its recomposer") { frame.swingRecomposerOrNull() == null }

            // The stamp that recomposer left on the root pane goes with it, so this resolves the window
            // afresh. A stamp left standing would compose this content under an ended context, which
            // would never recompose.
            composition.setContent { Label(text = "again") }
            awaitUntil("the emptied window takes content again") { labelTextOrNull(composition) == "again" }
            // Read as non-null before it is compared: swingRecomposerOrNull answers null for a window
            // holding none, which is the very state this has to tell a fresh recomposer apart from.
            val second = assertNotNull(frame.swingRecomposerOrNull(), "the window must hold a recomposer again")
            assertNotSame(first, second, "content mounted after a window emptied must be given a fresh recomposer")
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun contentThatLeavesItsWindowAndReturnsInOneTurnKeepsItsRecomposer() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        val other = realizedFrame()
        try {
            val holder = onEdtChild(frame)
            val composition = JPanel().also { holder.add(it) }
            var text by mutableStateOf("before")
            composition.setContent { Label(text = text) }
            awaitUntil("the content composes in the window it starts in") {
                labelTextOrNull(composition) == "before"
            }
            val recomposer = assertNotNull(frame.swingRecomposerOrNull())

            // Out of its window and back again with no turn of the event queue in between - what a tool
            // bar does when it is floated and docked straight back. Leaving withdraws the registration,
            // which leaves this window's recomposer with nothing registered; the rejoin that takes it up
            // again is queued ahead of that, so the recomposer hears the answer before it counts itself
            // unused. Were it disposed in between, the rejoin would publish an ended context and register
            // with nothing, leaving this content composing on a cancelled recomposer - live, still in the
            // tree, and never recomposing again, which is what the wait below catches.
            holder.remove(composition)
            other.contentPane.add(composition)
            other.contentPane.remove(composition)
            holder.add(composition)

            text = "after"
            awaitUntil("the returned content goes on recomposing") { labelTextOrNull(composition) == "after" }
            assertSame(
                recomposer,
                frame.swingRecomposerOrNull(),
                "a window whose content came straight back must keep the recomposer it had",
            )
        } finally {
            frame.dispose()
            other.dispose()
        }
    }

    @Test
    fun closingTheWindowDisposesItsCompositions() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        try {
            val composition = onEdtChild(frame)
            var effectDisposed = false
            composition.setContent {
                DisposableEffect(Unit) {
                    onDispose { effectDisposed = true }
                }
                Label(text = "composed")
            }
            awaitUntil("the content composition renders") { labelTextOrNull(composition) == "composed" }

            // Disposing the window fires windowClosed, whose teardown disposes the content
            // compositions in it - not only the recomposer they recomposed on.
            frame.dispose()
            awaitUntil("closing the window runs the composition's effect disposal") { effectDisposed }

            // The disposed composition withdrew its registration with it, so the container takes
            // content again; a registration left standing would refuse this second setContent.
            // It composes where it stands; pack() realizes the window again so this test's own
            // dispose() reaps what it left.
            composition.setContent { Label(text = "remounted") }
            frame.pack()
            awaitUntil("the container takes content again") {
                labelTextOrNull(composition) == "remounted"
            }
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun closingTheWindowDisposesACompositionStandingOnItsOwnRecomposer() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        // The teardown under test is the window's own, installed with its recomposer, so the window has
        // to own one - which content composing on a caller's recomposer never asks it for.
        frame.getOrCreateRecomposer()
        // A recomposer built for a component no window holds: content named this context composes on
        // the caller's own recomposer and registers with no window's, so nothing but the walk over the
        // closing window's tree reaches it.
        val own = SwingRecomposer.create(JPanel())
        try {
            val composition = onEdtChild(frame)
            var effectDisposed = false
            composition.setContent(parent = own.compositionContext) {
                DisposableEffect(Unit) {
                    onDispose { effectDisposed = true }
                }
                Label(text = "composed")
            }
            awaitUntil("the content composition renders") { labelTextOrNull(composition) == "composed" }

            frame.dispose()
            awaitUntil("closing the window disposes content composing on its caller's own recomposer") {
                effectDisposed
            }

            // The disposed composition withdrew its registration with it, so the container takes
            // content again; a registration left standing would refuse this second setContent.
            // It composes where it stands; pack() realizes the window again so this test's own
            // dispose() reaps what it left.
            composition.setContent { Label(text = "remounted") }
            frame.pack()
            awaitUntil("the container takes content again") {
                labelTextOrNull(composition) == "remounted"
            }
        } finally {
            frame.dispose()
            own.dispose()
        }
    }

    @Test
    fun closingTheWindowDisposesACompositionWhoseContainerLeftIt() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        try {
            val composition = onEdtChild(frame)
            var effectDisposed = false
            composition.setContent {
                DisposableEffect(Unit) {
                    onDispose { effectDisposed = true }
                }
                Label(text = "composed")
            }
            awaitUntil("the content composition renders") { labelTextOrNull(composition) == "composed" }

            // Taken out of the window but not disposed: a container in no window has arrived nowhere,
            // so the composition keeps composing under the window's recomposer while hanging off no
            // tree the window's teardown can walk.
            frame.contentPane.remove(composition)

            frame.dispose()
            awaitUntil("closing the window runs the detached composition's effect disposal") { effectDisposed }
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun closingTheWindowDisposesAnAdoptedCompositionWhoseContainerLeftIt() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        try {
            var host: CompositionContext? = null
            onEdtChild(frame).setContent {
                host = rememberCompositionContext()
                Label(text = "host")
            }
            awaitUntil("the host composition publishes its composition context") { host != null }

            // Composed while attached to nothing, under a context published by the window's own
            // content: a caller-named context that is no window's own names no window, so this content
            // composition can register with a window recomposer only by adopting the first window it
            // reaches.
            val composition = JPanel()
            var effectDisposed = false
            composition.setContent(parent = host ?: error("no host context")) {
                DisposableEffect(Unit) {
                    onDispose { effectDisposed = true }
                }
                Label(text = "composed")
            }
            awaitUntil("the detached content composes on the call") { labelTextOrNull(composition) == "composed" }

            // Adopt the window, then leave it again: the container is out of every tree the window's
            // teardown can walk, so only the registration taken on adoption reaches this composition.
            frame.contentPane.add(composition)
            frame.contentPane.remove(composition)

            frame.dispose()
            awaitUntil("closing the adopted window runs the detached composition's effect disposal") {
                effectDisposed
            }
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun closingTheWindowMidMoveLeavesTheCompositionComposingInItsNewWindow() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val first = realizedFrame()
        val second = realizedFrame()
        try {
            var text by mutableStateOf("v0")
            val composition = onEdtChild(first)
            composition.setContent { Label(text = text) }
            awaitUntil("the content composition renders in its first window") {
                labelTextOrNull(composition) == "v0"
            }

            // One EDT turn, close first: the windowClosed event is queued before the move queues its
            // rejoin, so the closing window's teardown runs while the composition still stands mid-move.
            first.dispose()
            second.contentPane.add(composition)

            text = "v1"
            awaitUntil("the content composition keeps recomposing in the window it joined") {
                labelTextOrNull(composition) == "v1"
            }
        } finally {
            first.dispose()
            second.dispose()
        }
    }

    @Test
    fun closingTheWindowDisposesACompositionThatLeftItMidMove() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val first = realizedFrame()
        val second = realizedFrame()
        try {
            val composition = onEdtChild(first)
            var effectDisposed = false
            composition.setContent {
                DisposableEffect(Unit) {
                    onDispose { effectDisposed = true }
                }
                Label(text = "composed")
            }
            awaitUntil("the content composition renders in its first window") {
                labelTextOrNull(composition) == "composed"
            }

            // One EDT turn: the composition enters the second window and leaves it again before the
            // queued rejoin can compose it there, so the queue drains with it in no window and the
            // first window's composition still its own.
            second.contentPane.add(composition)
            second.contentPane.remove(composition)
            // Yield the EDT so the queued rejoin runs before the close: the close must find what the
            // undone move left behind, not race it.
            repeat(4) { yield() }

            first.dispose()
            awaitUntil("closing the window the mid-move composition left runs its effect disposal") {
                effectDisposed
            }
        } finally {
            first.dispose()
            second.dispose()
        }
    }

    @Test
    fun closingTheWindowDisposesACompositionInItsMenuBar() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        try {
            val menuBar = JMenuBar()
            frame.jMenuBar = menuBar
            val composition = JPanel().also { menuBar.add(it) }
            var effectDisposed = false
            composition.setContent {
                DisposableEffect(Unit) {
                    onDispose { effectDisposed = true }
                }
                Label(text = "composed")
            }
            awaitUntil("the menu bar composition renders its content") {
                labelTextOrNull(composition) == "composed"
            }

            frame.dispose()
            awaitUntil("closing the window runs the menu bar composition's effect disposal") { effectDisposed }
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

    /** Adds and returns a fresh child container inside [frame]'s content pane. Must be on the EDT. */
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

    @Test
    fun closingADeclarativeWindowEndsACompositionLeftOpenInIt() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var compositionComposed = false
        var compositionEnded = false
        awaitApplication {
            Window(
                onCloseRequest = ::exitApplication,
                content = {
                    val window = LocalWindow.current
                    DisposableEffect(Unit) {
                        // The handle is deliberately never disposed: closing the window is the only
                        // thing left that can end this content composition.
                        val panel = JPanel()
                        window?.add(panel)
                        panel.setContent {
                            DisposableEffect(Unit) {
                                compositionComposed = true
                                onDispose { compositionEnded = true }
                            }
                        }
                        onDispose { }
                    }
                    LaunchedEffect(Unit) {
                        // The window closes only once the content composition is up: one that never
                        // composed would leave nothing to dispose and pass this vacuously.
                        withTimeout(SETTLE_TIMEOUT) { while (!compositionComposed) yield() }
                        this@awaitApplication.exitApplication()
                    }
                },
            )
        }
        awaitUntil("the content composition the caller never disposed ends with the window that held it") {
            compositionEnded
        }
    }

    private companion object {
        const val FRAME_SIZE: Int = 200
        val SETTLE_TIMEOUT = 10.seconds
    }
}
