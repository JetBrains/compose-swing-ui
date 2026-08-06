package org.jetbrains.compose.swing.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.core.compositionContext
import org.jetbrains.compose.swing.core.recomposerOrNull
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.window.LocalWindow
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Component
import java.awt.Container
import java.awt.GraphicsEnvironment
import java.awt.Window
import java.awt.image.BufferedImage
import javax.swing.CellRendererPane
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** A [androidx.compose.runtime.CompositionLocal] a host composition provides to whatever nests into it. */
private val LocalGreeting = compositionLocalOf { "none" }

/**
 * Behavioral tests for the parent a `setContent` composes under, on the half a window answers: what a
 * container with no parent named waits for, what a window's own context states, what a host
 * composition's context hands down, and what a container that moves between windows joins.
 *
 * A mount watches the window its container is in: it composes the content again when the container ends
 * up in another window, and leaves it exactly where it is for every other move - a container joining the
 * window its parent composition is already in, one adopted by the pane that paints it, one in transit
 * between two windows, one going back where it came from.
 *
 * Every case realizes a real top-level peer, so each skips (reports SKIPPED) on a headless environment.
 * Frames are packed rather than shown - that realizes the peer without flashing a window on screen - and
 * disposed on every exit path so no peer leaks. Content driven by a window's own real Swing frame-clock
 * timer is awaited by yielding the EDT back between checks, until a bounded deadline.
 */
class SetContentParentWindowTest {
    @Test
    fun withNoParentNamedAContainerWaitsForTheWindowItIsAddedTo() = runBlocking(Dispatchers.Swing) {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        try {
            // Nothing answers what this container composes under yet: no parent named, no stamped
            // ancestor, no window. The mount waits rather than composing under a parent of its own.
            val panel = JPanel()
            val island = IslandRecorder()
            val handle =
                panel.setContent {
                    island.Read()
                    Label(text = "deferred")
                }

            assertTrue(island.windows.isEmpty(), "a container with no resolvable parent must not compose on the call")
            assertEquals(
                0,
                panel.componentCount,
                "a waiting mount must add no children before the container is in a window",
            )

            frame.contentPane.add(panel)
            frame.pack()

            awaitUntil("the waiting content composes once the container is in a window") {
                island.windows.isNotEmpty()
            }
            assertSame(
                frame,
                island.windows.last(),
                "content mounted on attach must compose under the window it reached",
            )
            assertEquals(listOf("deferred"), labelTexts(panel), "the mounted island must hold its content")
            // Both watchers stay: the mount follows the window the container is in for as long as it
            // composes there, and so does the lifecycle of the content it mounted.
            assertEquals(
                2,
                panel.hierarchyListeners.size,
                "a mounted island must watch the container's place with one HierarchyListener for the mount " +
                    "and one for the content's lifecycle",
            )

            handle.dispose()
            assertEquals(0, panel.hierarchyListeners.size, "disposing after the mount must leave no listener behind")
            assertEquals(emptyList(), labelTexts(panel), "disposing must take the mounted content down")
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun aWindowsOwnContextStatesThatWindowFromTheFirstPass() = runBlocking(Dispatchers.Swing) {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        try {
            // The container is in no window at all; the window is named, through the context that window
            // shares. Its content belongs to that window from the pass it first composes in - which is
            // what a dialog opened out of it, or a callback acting on it, has to reach right away.
            val panel = JPanel()
            val island = IslandRecorder()
            val handle =
                panel.setContent(parent = frame.compositionContext()) {
                    island.Read()
                    Label(text = "named")
                }

            assertEquals(
                listOf<Window?>(frame),
                island.windows,
                "content under a window's own context must compose under that window on its first pass",
            )
            handle.dispose()
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun aHostContextHandsDownItsLocalsAndTheWindowItIsUnder() = runBlocking(Dispatchers.Swing) {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        try {
            // A host composition inside the window publishes its context from under a CompositionLocal it
            // provides. An island nesting into that context is part of that composition, so it reads what
            // the host provides - the window included, which the host itself reads from the window it is
            // an island of.
            val host = hostIn(frame)

            // Nothing but the host context connects this container to the window: it is in none.
            val island = JPanel()
            val recorder = IslandRecorder()
            val islandHandle =
                island.setContent(parent = host.context) {
                    recorder.Read()
                    Label(text = "island")
                }

            assertEquals(
                "from-host",
                recorder.greeting,
                "an island under a host context must read the locals that host provides",
            )
            assertSame(
                frame,
                recorder.windows.first(),
                "an island under a host context must read the window that host is under",
            )

            islandHandle.dispose()
            host.handle.dispose()
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun anIslandUnderAHostContextIsNotComposedAgainWhenItsContainerJoinsThatWindow() = runBlocking(Dispatchers.Swing) {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        try {
            // The island is composed under a host that is itself an island of this window, and its
            // container is then given a place in that same window. It has not moved between windows, so
            // the composition it belongs to is untouched: the host's locals still reach it, what it
            // remembered is still there, and the owner its content reads is the one it opened with.
            val host = hostIn(frame)
            val island = JPanel()
            val recorder = IslandRecorder()
            val handle =
                island.setContent(parent = host.context) {
                    recorder.Read()
                    Label(text = "island")
                }
            val composedOnce = recorder.remembered
            val ownerOnMount = recorder.owner
            assertSame(
                host.owner,
                ownerOnMount,
                "an island reads the owner of the composition it nests into, so one hanging off nothing " +
                    "still stands under the owner its host was given",
            )

            frame.contentPane.add(island)
            frame.pack()

            assertSame(
                composedOnce,
                recorder.remembered,
                "a container joining the window its parent composition is already in must keep the " +
                    "composition it has, remembered state and all",
            )
            assertEquals(
                "from-host",
                recorder.greeting,
                "an island that has not changed windows must go on reading the locals its host provides",
            )
            assertSame(
                ownerOnMount,
                recorder.owner,
                "an island that has not changed windows must go on reading the owner it opened with",
            )
            assertNotEquals(
                Lifecycle.State.DESTROYED,
                ownerOnMount?.lifecycle?.currentState,
                "an island that has not changed windows must not have its owner ended",
            )
            assertEquals(listOf("island"), labelTexts(island), "the island must still hold its content")

            handle.dispose()
            host.handle.dispose()
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun anIslandUnderAHostContextJoinsTheOtherWindowItsContainerIsMovedTo() = runBlocking(Dispatchers.Swing) {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val first = realizedFrame()
        val second = realizedFrame()
        try {
            // The same island, moved out of the window its host is in and into another one. Where it
            // hangs in the Swing tree is what the second window can account for, so it is composed again
            // under that window and reads it.
            val host = hostIn(first)
            val island = JPanel().also { first.contentPane.add(it) }
            val recorder = IslandRecorder()
            val handle =
                island.setContent(parent = host.context) {
                    recorder.Read()
                    Label(text = "island")
                }
            val composedOnce = recorder.remembered
            assertSame(first, recorder.windows.last(), "the island must start out under the window its host is in")

            second.contentPane.add(island)
            second.pack()

            awaitUntil("the island composes under the window it was moved to") {
                recorder.windows.last() === second
            }
            assertSame(
                second,
                recorder.windows.last(),
                "a container moved to another window must read the window it is then in",
            )
            assertNotSame(
                composedOnce,
                recorder.remembered,
                "joining another window's composition means composing again there",
            )
            assertEquals(listOf("island"), labelTexts(island), "the island composed again must hold its content")

            handle.dispose()
            host.handle.dispose()
        } finally {
            second.dispose()
            first.dispose()
        }
    }

    @Test
    fun aContainerAddedToAnotherWindowComposesUnderTheWindowItIsThenIn() = runBlocking(Dispatchers.Swing) {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val first = realizedFrame()
        val second = realizedFrame()
        try {
            // Mounted under one window's context, the container is then taken out of that window and
            // added to another one, passing through a place that belongs to no window on the way. Its
            // content is composed again under the window it is now in, and keeps composing through the
            // move.
            val panel = JPanel().also { first.contentPane.add(it) }
            val island = IslandRecorder()
            val handle =
                panel.setContent(parent = first.compositionContext()) {
                    island.Read()
                    Label(text = "moved")
                }
            assertSame(
                first,
                island.windows.first(),
                "the island must start out under the window whose context was named",
            )

            second.contentPane.add(panel)
            second.pack()

            awaitUntil("the island composes under the window it was added to") { island.windows.last() === second }
            assertSame(
                second,
                island.windows.last(),
                "a container added to another window must compose under that window",
            )
            assertEquals(listOf("moved"), labelTexts(panel), "the island composed again must hold its content")

            handle.dispose()
            assertEquals(0, panel.hierarchyListeners.size, "disposing after a move must leave no listener behind")
            assertEquals(emptyList(), labelTexts(panel), "disposing after a move must take the content down")
        } finally {
            second.dispose()
            first.dispose()
        }
    }

    @Test
    fun anIslandWithNoParentNamedFollowsItsContainerToAnotherWindowAndOutlivesTheOneItLeft() =
        runBlocking(Dispatchers.Swing) {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val first = realizedFrame()
            val second = realizedFrame()
            try {
                // A container with no parent named composes under the window it is in, and every island of
                // one window recomposes with the rest of it - which has to hold after a move, or the
                // content left behind on the first window's recomposer goes quiet the moment that window
                // is disposed.
                val panel = JPanel().also { first.contentPane.add(it) }
                val recorder = IslandRecorder()
                var text by mutableStateOf("v0")
                val handle =
                    panel.setContent {
                        recorder.Read()
                        Label(text = text)
                    }
                awaitUntil("the island composes under the window it was mounted in") { recorder.windows.isNotEmpty() }
                assertSame(first, recorder.windows.last(), "the island must start out under the window it is in")

                // Out of the first window and in no window at all: nothing to join yet, so the island
                // keeps composing where it is rather than being torn down on the way.
                first.contentPane.remove(panel)
                text = "in-transit"
                awaitUntil("the island in no window goes on recomposing") { labelTexts(panel) == listOf("in-transit") }
                assertSame(
                    first,
                    recorder.windows.last(),
                    "a container in no window must keep composing under the window it came from",
                )

                second.contentPane.add(panel)
                second.pack()

                awaitUntil("the island composes under the window it arrived in") { recorder.windows.last() === second }
                text = "arrived"
                awaitUntil("the island recomposes with the window it arrived in") {
                    labelTexts(panel) == listOf("arrived")
                }

                // The window it left owns the recomposer it was mounted on; disposing that window cancels
                // it. Content that had joined the second window is driven by that one and carries on.
                first.dispose()
                awaitUntil("the window it left releases its runtime") { first.recomposerOrNull() == null }

                text = "outlived"
                awaitUntil("the island keeps recomposing once the window it left is disposed") {
                    labelTexts(panel) == listOf("outlived")
                }

                handle.dispose()
            } finally {
                second.dispose()
                first.dispose()
            }
        }

    @Test
    fun anIslandTakenOutOfItsWindowKeepsComposingAndJoinsNothingWhenItGoesBack() = runBlocking(Dispatchers.Swing) {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        try {
            // Taken out of the window and put back into it, the container has been in exactly one window
            // throughout. Nothing it passed through is another window, so the composition it belongs to is
            // never given up - not while it hangs off nothing, and not when it arrives back.
            val host = hostIn(frame)
            val island = JPanel().also { frame.contentPane.add(it) }
            val recorder = IslandRecorder()
            var text by mutableStateOf("v0")
            val handle =
                island.setContent(parent = host.context) {
                    recorder.Read()
                    Label(text = text)
                }
            val composedOnce = recorder.remembered
            frame.pack()

            frame.contentPane.remove(island)
            text = "in-transit"
            awaitUntil("the island in no window goes on recomposing") { labelTexts(island) == listOf("in-transit") }
            assertSame(
                composedOnce,
                recorder.remembered,
                "a container in no window must keep the composition it has",
            )
            assertEquals(
                "from-host",
                recorder.greeting,
                "a container in no window must go on reading the locals its host provides",
            )

            frame.contentPane.add(island)
            text = "back"
            awaitUntil("the island back in its window goes on recomposing") { labelTexts(island) == listOf("back") }
            assertSame(
                composedOnce,
                recorder.remembered,
                "arriving back in the window it was in is no move between windows, so the composition stays",
            )
            assertSame(
                frame,
                recorder.windows.last(),
                "an island that has been in one window throughout must read that window",
            )

            handle.dispose()
            host.handle.dispose()
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun aCellIslandIsNotComposedAgainWhenTheRendererPaneAdoptsItForPainting() = runBlocking(Dispatchers.Swing) {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        try {
            // A cell is composed into a renderer host that hangs off nothing, and the pane that paints it
            // adopts it the first time it is painted: `CellRendererPane.paintComponent` adds the component
            // it is handed to itself. That is a place change within the very window the list is in, and
            // the cell's composition - the list's own - must be left alone by it.
            val host = hostIn(frame)
            val rendererPane = CellRendererPane().also { frame.contentPane.add(it) }
            val cell = JPanel()
            val recorder = IslandRecorder()
            val handle =
                cell.setContent(parent = host.context) {
                    recorder.Read()
                    Label(text = "cell")
                }
            val composedOnce = recorder.remembered
            frame.pack()

            paintThrough(rendererPane, cell, frame.contentPane)

            assertSame(rendererPane, cell.parent, "the pane must have adopted the cell it painted")
            assertSame(
                composedOnce,
                recorder.remembered,
                "a cell adopted by the pane that paints it must keep the composition it was stamped from",
            )
            assertEquals(
                "from-host",
                recorder.greeting,
                "an adopted cell must go on reading the locals of the composition it belongs to",
            )

            handle.dispose()
            host.handle.dispose()
        } finally {
            frame.dispose()
        }
    }

    /**
     * Records what one island's content reads on every pass it composes: the window it is under, the
     * locals reaching it, the owner it composes with, and a value it remembers - which is the same object
     * for as long as one composition lives and another object once the content is composed again.
     */
    @Test
    fun anIslandUnderAHostInAnotherWindowReadsItsHostsWindow() = runBlocking(Dispatchers.Swing) {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val hostWindow = realizedFrame()
        val islandWindow = realizedFrame()
        try {
            // A detached peer's content joined to the composition of another window - what
            // `rememberCompositionContext` threaded into a second window's `setContent` builds. The island
            // reads the window of the composition it joined, not the one its own container hangs in: it
            // recomposes with that composition and reads the locals it provides, and a window read off the
            // container would disagree with both.
            val host = hostIn(hostWindow)
            val island = JPanel().also { islandWindow.contentPane.add(it) }
            val recorder = IslandRecorder()
            val handle = island.setContent(parent = host.context) { recorder.Read() }

            awaitUntil("the island composes under its host") { recorder.windows.isNotEmpty() }
            assertSame(
                hostWindow,
                recorder.windows.last(),
                "an island joining a host's composition must read the window that composition is under",
            )
            assertNotSame(
                islandWindow,
                recorder.windows.last(),
                "the window an island's own container hangs in must not override the one it joined",
            )

            handle.dispose()
            host.handle.dispose()
        } finally {
            islandWindow.dispose()
            hostWindow.dispose()
        }
    }

    @Test
    fun anIslandUnderAWindowlessParentReadsTheWindowItsContainerIsAddedTo() = runBlocking(Dispatchers.Swing) {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        // A runtime of its own belongs to no window, so a caller naming it as the parent composes content
        // that stands under none - the shape a page built before it is shown anywhere takes.
        val runtime = SwingRecomposer.create(JPanel())
        val frame = realizedFrame()
        try {
            val panel = JPanel()
            val island = IslandRecorder()
            val handle = panel.setContent(parent = runtime.compositionContext) { island.Read() }

            awaitUntil("the island composes under its named parent") { island.windows.isNotEmpty() }
            assertEquals(
                listOf<Window?>(null),
                island.windows,
                "a parent belonging to no window leaves its island standing under none",
            )
            val remembered = island.remembered

            frame.contentPane.add(panel)
            frame.pack()

            awaitUntil("the island reads the window its container was added to") { island.windows.last() === frame }
            assertSame(
                remembered,
                island.remembered,
                "adopting a window must publish it to the standing composition rather than compose a new one",
            )

            handle.dispose()
        } finally {
            frame.dispose()
            runtime.dispose()
        }
    }

    @Test
    fun anIslandReadsALifecycleOwnerProvidedOverTheCompositionItJoins() = runBlocking(Dispatchers.Swing) {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        try {
            // An owner provided from outside the library - by a caller, or by a navigation library -
            // stands over what a root would otherwise state, so a root defers to it rather than
            // replacing it with one of its own.
            val provided = ProvidedLifecycleOwner()
            var published: CompositionContext? = null
            val hostPanel = JPanel().also { frame.contentPane.add(it) }
            val hostHandle =
                hostPanel.setContent {
                    CompositionLocalProvider(LocalLifecycleOwner provides provided) {
                        published = rememberCompositionContext()
                        Label(text = "host")
                    }
                }
            awaitUntil("the host composition publishes its context") { published != null }

            val island = JPanel()
            val recorder = IslandRecorder()
            val handle = island.setContent(parent = checkNotNull(published)) { recorder.Read() }

            awaitUntil("the island composes under its host") { recorder.owner != null }
            assertSame(
                provided,
                recorder.owner,
                "an owner provided over the composition an island joins must reach that island's content",
            )

            handle.dispose()
            hostHandle.dispose()
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun anIslandUnderAHostGoesOnReadingItsHostsWindowWhenItsContainerJoinsAnother() = runBlocking(Dispatchers.Swing) {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val hostWindow = realizedFrame()
        val otherWindow = realizedFrame()
        try {
            // The island composes under a host in one window while hanging off nothing, and its container
            // is then given a place in another. It reads the window of the composition it joined: where a
            // container hangs answers only where that composition names no window at all.
            val host = hostIn(hostWindow)
            val island = JPanel()
            val recorder = IslandRecorder()
            val handle = island.setContent(parent = host.context) { recorder.Read() }

            awaitUntil("the island composes under its host") { recorder.windows.isNotEmpty() }
            assertSame(hostWindow, recorder.windows.last(), "an island must read the window of the host it joined")

            otherWindow.contentPane.add(island)
            otherWindow.pack()

            assertSame(
                hostWindow,
                recorder.windows.last(),
                "a container joining a window of its own must not displace the window its island inherited",
            )
            assertTrue(
                recorder.windows.none { it === otherWindow },
                "the window an island's own container hangs in must never be read where one was inherited",
            )

            handle.dispose()
            host.handle.dispose()
        } finally {
            otherWindow.dispose()
            hostWindow.dispose()
        }
    }

    @Test
    fun anIslandUnderAWindowlessParentReadsTheWindowItsContainerAlreadyStandsIn() = runBlocking(Dispatchers.Swing) {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        // The container already hangs in a window as the mount composes, and the parent it was given
        // belongs to none. Nothing is inherited and no move follows, so the window the container stands
        // in is the only thing left to answer with.
        val runtime = SwingRecomposer.create(JPanel())
        val frame = realizedFrame()
        try {
            val panel = JPanel().also { frame.contentPane.add(it) }
            frame.pack()
            val island = IslandRecorder()
            val handle = panel.setContent(parent = runtime.compositionContext) { island.Read() }

            awaitUntil("the island composes under its named parent") { island.windows.isNotEmpty() }
            assertSame(
                frame,
                island.windows.last(),
                "a parent belonging to no window leaves its island reading the window it stands in",
            )

            handle.dispose()
        } finally {
            frame.dispose()
            runtime.dispose()
        }
    }

    private class IslandRecorder {
        private val recorded = mutableListOf<Window?>()

        val windows: List<Window?> get() = recorded

        var greeting: String? = null
            private set

        var owner: LifecycleOwner? = null
            private set

        var remembered: Any? = null
            private set

        @Composable
        fun Read() {
            recorded += LocalWindow.current
            greeting = LocalGreeting.current
            owner = LocalLifecycleOwner.current
            remembered = remember { Any() }
        }
    }

    /** A [LifecycleOwner] standing in for one a caller or a navigation library provides. */
    private class ProvidedLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)

        override val lifecycle: Lifecycle get() = registry
    }

    /**
     * A host composition inside a window: the context it publishes for islands to nest into, and the
     * [LifecycleOwner] its own content composes with, which is what an island hanging under it reads.
     */
    private class Host(
        val context: CompositionContext,
        val owner: LifecycleOwner,
        val handle: DisposableHandle,
    )

    /**
     * Composes a host island into [frame] that provides a local and publishes its own context, so an
     * island nesting into it is part of a composition of this window rather than of the window root.
     * Must be called on the EDT.
     */
    private suspend fun hostIn(frame: JFrame): Host {
        var published: CompositionContext? = null
        var owner: LifecycleOwner? = null
        val panel = JPanel().also { frame.contentPane.add(it) }
        val handle =
            panel.setContent {
                CompositionLocalProvider(LocalGreeting provides "from-host") {
                    published = rememberCompositionContext()
                    owner = LocalLifecycleOwner.current
                    Label(text = "host")
                }
            }
        awaitUntil("the host composition publishes its context") { published != null }
        return Host(checkNotNull(published), checkNotNull(owner), handle)
    }

    /**
     * Paints [cell] the way a list paints one of its rows: through the [pane] that adopts the component
     * it is handed, into an image of its own rather than onto a screen. Must be called on the EDT.
     */
    private fun paintThrough(
        pane: CellRendererPane,
        cell: Component,
        host: Container,
    ) {
        val image = BufferedImage(CELL_SIDE, CELL_SIDE, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            pane.paintComponent(graphics, cell, host, 0, 0, CELL_SIDE, CELL_SIDE)
        } finally {
            graphics.dispose()
        }
    }

    /**
     * A realized, off-screen [JFrame] with a live peer. Packing realizes the peer without showing the
     * frame. Must be called on the EDT.
     */
    private fun realizedFrame(): JFrame = JFrame().apply {
        defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
        pack()
    }

    /** The text of every [JLabel] in [container]'s subtree, in tree order. Must be called on the EDT. */
    private fun labelTexts(container: Container): List<String> {
        val texts = mutableListOf<String>()

        fun visit(c: Container) {
            for (child in c.components) {
                if (child is JLabel) texts += child.text
                if (child is Container) visit(child)
            }
        }
        visit(container)
        return texts
    }

    /**
     * Suspends on the EDT until [condition] holds, yielding the EDT back between checks so a window's
     * real frame-clock timer can fire and its recomposer can mount and recompose content. A condition
     * that never holds fails the test at the deadline, naming [description], instead of hanging.
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
        val SETTLE_TIMEOUT = 10.seconds
        const val CELL_SIDE: Int = 40
    }
}
