package org.jetbrains.compose.swing.core

import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCompositionContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import java.awt.Window
import javax.swing.CellRendererPane
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioral tests for the parent a `setContent` composes under, on the half a window answers: what a
 * container with no parent named waits for, what a window's own context states, and what a host
 * composition's context hands down. A container that moves between windows is
 * [SetContentMoveTest]'s half.
 *
 * Every case realizes a real top-level peer, so each skips (reports SKIPPED) on a headless environment.
 * Frames are packed rather than shown - that realizes the peer without flashing a window on screen - and
 * disposed on every exit path so no peer leaks. Content driven by a window's own real Swing frame-clock
 * timer is awaited by yielding the EDT back between checks, until a bounded deadline.
 */
class SetContentParentWindowTest {
    @Test
    fun withNoParentNamedAContainerWaitsForTheWindowItIsAddedTo() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        try {
            // Nothing answers what this container composes under yet: no parent named, no stamped
            // ancestor, no window. The content waits rather than composing under a parent of its own.
            val panel = JPanel()
            val recorder = CompositionRecorder()
            val handle =
                panel.setContent {
                    recorder.Read()
                    Label(text = "deferred")
                }

            assertTrue(recorder.windows.isEmpty(), "a container with no resolvable parent must not compose on the call")
            assertEquals(
                0,
                panel.componentCount,
                "waiting content must add no children before the container is in a window",
            )

            frame.contentPane.add(panel)
            frame.pack()

            awaitUntil("the waiting content composes once the container is in a window") {
                recorder.windows.isNotEmpty()
            }
            assertSame(
                frame,
                recorder.windows.last(),
                "content mounted on attach must compose under the window it reached",
            )
            assertEquals(listOf("deferred"), labelTexts(panel), "the mounted composition must hold its content")
            // Both watchers stay: the composition follows the window the container is in for as long as
            // it composes there, and so does the lifecycle of the content it mounted.
            assertEquals(
                2,
                panel.hierarchyListeners.size,
                "a mounted composition must watch the container's place with one HierarchyListener for " +
                    "mounting and one for the content's lifecycle",
            )

            handle.dispose()
            assertEquals(0, panel.hierarchyListeners.size, "disposing after mounting must leave no listener behind")
            assertEquals(emptyList(), labelTexts(panel), "disposing must take the mounted content down")
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun aWindowsOwnContextStatesThatWindowFromTheFirstPass() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        try {
            // The container is in no window at all; the window is named, through the context that window
            // shares. Its content belongs to that window from the pass it first composes in - which is
            // what a dialog opened out of it, or a callback acting on it, has to reach right away.
            val panel = JPanel()
            val recorder = CompositionRecorder()
            val handle =
                panel.setContent(parent = frame.compositionContext()) {
                    recorder.Read()
                    Label(text = "named")
                }

            assertEquals(
                listOf<Window?>(frame),
                recorder.windows,
                "content under a window's own context must compose under that window on its first pass",
            )
            handle.dispose()
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun aHostContextHandsDownItsLocalsAndTheWindowItIsUnder() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        try {
            // A host composition inside the window publishes its context from under a CompositionLocal it
            // provides. Content nesting into that context is part of that composition, so it reads what
            // the host provides - the window included, which the host itself reads from the window its
            // own container stands in.
            val host = hostIn(frame)

            // Nothing but the host context connects this container to the window: it is in none.
            val composition = JPanel()
            val recorder = CompositionRecorder()
            val compositionHandle =
                composition.setContent(parent = host.context) {
                    recorder.Read()
                    Label(text = "nested")
                }

            assertEquals(
                "from-host",
                recorder.greeting,
                "content under a host context must read the locals that host provides",
            )
            assertSame(
                frame,
                recorder.windows.first(),
                "content under a host context must read the window that host is under",
            )

            compositionHandle.dispose()
            host.handle.dispose()
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun aCompositionUnderAHostContextIsNotComposedAgainWhenItsContainerJoinsThatWindow() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        try {
            // The content is composed under a host that is itself a content composition of this window,
            // and its container is then given a place in that same window. It has not moved between
            // windows, so the composition it belongs to is untouched: the host's locals still reach it,
            // what it remembered is still there, and the owner its content reads is the one it opened
            // with.
            val host = hostIn(frame)
            val composition = JPanel()
            val recorder = CompositionRecorder()
            val handle =
                composition.setContent(parent = host.context) {
                    recorder.Read()
                    Label(text = "nested")
                }
            val composedOnce = recorder.remembered
            val ownerOnMount = recorder.owner
            assertSame(
                host.owner,
                ownerOnMount,
                "content reads the owner of the composition it nests into, so content hanging off nothing " +
                    "still stands under the owner its host was given",
            )

            frame.contentPane.add(composition)
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
                "content that has not changed windows must go on reading the locals its host provides",
            )
            assertSame(
                ownerOnMount,
                recorder.owner,
                "content that has not changed windows must go on reading the owner it opened with",
            )
            assertNotEquals(
                Lifecycle.State.DESTROYED,
                ownerOnMount?.lifecycle?.currentState,
                "content that has not changed windows must not have its owner ended",
            )
            assertEquals(listOf("nested"), labelTexts(composition), "the composition must still hold its content")

            handle.dispose()
            host.handle.dispose()
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun aCellCompositionIsNotComposedAgainWhenTheRendererPaneAdoptsItForPainting() = runSwingTest {
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
            val recorder = CompositionRecorder()
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

    @Test
    fun aCompositionUnderAHostInAnotherWindowReadsItsHostsWindow() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val hostWindow = realizedFrame()
        val containerWindow = realizedFrame()
        try {
            // A detached peer's content joined to the composition of another window - what
            // `rememberCompositionContext` threaded into a second window's `setContent` builds. The
            // content reads the window of the composition it joined, not the one its own container hangs
            // in: it recomposes with that composition and reads the locals it provides, and a window read
            // off the container would disagree with both.
            val host = hostIn(hostWindow)
            val composition = JPanel().also { containerWindow.contentPane.add(it) }
            val recorder = CompositionRecorder()
            val handle = composition.setContent(parent = host.context) { recorder.Read() }

            awaitUntil("the content composes under its host") { recorder.windows.isNotEmpty() }
            assertSame(
                hostWindow,
                recorder.windows.last(),
                "content joining a host's composition must read the window that composition is under",
            )
            assertNotSame(
                containerWindow,
                recorder.windows.last(),
                "the window a composition's own container hangs in must not override the one it joined",
            )

            handle.dispose()
            host.handle.dispose()
        } finally {
            containerWindow.dispose()
            hostWindow.dispose()
        }
    }

    @Test
    fun aCompositionUnderAWindowlessParentReadsTheWindowItsContainerIsAddedTo() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        // A recomposer of its own belongs to no window, so a caller naming it as the parent composes
        // content that stands under none - the shape a page built before it is shown anywhere takes.
        val recomposer = SwingRecomposer.create(JPanel())
        val frame = realizedFrame()
        try {
            val panel = JPanel()
            val recorder = CompositionRecorder()
            val handle = panel.setContent(parent = recomposer.compositionContext) { recorder.Read() }

            awaitUntil("the content composes under its named parent") { recorder.windows.isNotEmpty() }
            assertEquals(
                listOf<Window?>(null),
                recorder.windows,
                "a parent belonging to no window leaves its content standing under none",
            )
            val remembered = recorder.remembered

            frame.contentPane.add(panel)
            frame.pack()

            awaitUntil("the content reads the window its container was added to") { recorder.windows.last() === frame }
            assertSame(
                remembered,
                recorder.remembered,
                "adopting a window must publish it to the standing composition rather than compose a new one",
            )

            handle.dispose()
        } finally {
            frame.dispose()
            recomposer.dispose()
        }
    }

    @Test
    fun contentReadsALifecycleOwnerProvidedOverTheCompositionItJoins() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        try {
            // An owner provided from outside the library - by a caller, or by a navigation library -
            // stands over what a root would otherwise state, so a root defers to it rather than
            // replacing it with one of its own.
            val provided = HostLifecycleOwner()
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

            val composition = JPanel()
            val recorder = CompositionRecorder()
            val handle = composition.setContent(parent = checkNotNull(published)) { recorder.Read() }

            awaitUntil("the content composes under its host") { recorder.owner != null }
            assertSame(
                provided,
                recorder.owner,
                "an owner provided over the composition content joins must reach that content",
            )

            handle.dispose()
            hostHandle.dispose()
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun aCompositionUnderAWindowlessParentReadsTheWindowItsContainerAlreadyStandsIn() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        // The container already hangs in a window as the content composes, and the parent it was given
        // belongs to none. Nothing is inherited and no move follows, so the window the container stands
        // in is the only thing left to answer with.
        val recomposer = SwingRecomposer.create(JPanel())
        val frame = realizedFrame()
        try {
            val panel = JPanel().also { frame.contentPane.add(it) }
            frame.pack()
            val recorder = CompositionRecorder()
            val handle = panel.setContent(parent = recomposer.compositionContext) { recorder.Read() }

            awaitUntil("the content composes under its named parent") { recorder.windows.isNotEmpty() }
            assertSame(
                frame,
                recorder.windows.last(),
                "a parent belonging to no window leaves its content reading the window it stands in",
            )

            handle.dispose()
        } finally {
            frame.dispose()
            recomposer.dispose()
        }
    }
}
