package org.jetbrains.compose.swing.window

import androidx.compose.runtime.Composable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Menu
import org.jetbrains.compose.swing.components.MenuItem
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Container
import java.awt.GraphicsEnvironment
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JMenuBar
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Behavioral tests for the window every island reads as its [LocalWindow]: content mounted into a
 * window reads that window without anyone stating it, whether the content is set on the window, on a
 * container that reaches one later, on a menu bar that is installed on one afterwards, or on a
 * container inside a window the composition itself realized - and whichever island of that window it
 * is. Content composed away from any window keeps reading the window it was composed under.
 *
 * A window a composition declares is a window of its own, so an island in it reads the peer it is in
 * rather than the window the declaration was composed under - which for a dialog declared in a window's
 * content is the frame behind it.
 *
 * Every case realizes a real top-level peer, so each skips (reports SKIPPED) on a headless
 * environment. Frames the test owns are packed rather than shown - that realizes the peer without
 * flashing a window on screen - and disposed on every exit path so no peer leaks; a declared window is
 * realized unshown and lives and dies with the composition that declares it. Content is driven by the
 * window's own real Swing frame-clock timer; the test body runs on the EDT and yields it back between
 * checks, letting that timer fire, until a bounded deadline.
 */
class LocalWindowTest {
    @Test
    fun contentSetOnAWindowReadsThatWindow() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        try {
            val island = WindowReader()
            frame.setContent { island.Read() }

            awaitComposed("the content set on the window composes", island)
            assertSame(frame, island.seen, "content set on a window must read that window as its LocalWindow")
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun contentSetOnADetachedContainerReadsTheWindowItIsLaterAttachedTo() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        try {
            // The mount is deferred until the container gains a window ancestor, so the window it reads
            // is the one that resolved the mount - not one that had to be known at the setContent call.
            val panel = JPanel()
            val island = WindowReader()
            panel.setContent { island.Read() }

            frame.contentPane.add(panel)
            frame.pack()

            awaitComposed("the deferred content composes once attached", island)
            assertSame(frame, island.seen, "content mounted on attach must read the window it was attached to")
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun aMenuTreeReadsTheWindowItsBarIsInstalledOn() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        try {
            // A menu bar is its own composition, built before it is installed: the window it reads is
            // the one it reaches when it is, which is what an item's callback needs to act on.
            val menuBar = JMenuBar()
            val menu = WindowReader()
            menuBar.setContent {
                menu.Read()
                Menu("File") { MenuItem("Open", onClick = {}) }
            }

            frame.jMenuBar = menuBar
            frame.pack()

            awaitComposed("the menu tree composes once its bar is installed", menu)
            assertSame(frame, menu.seen, "a menu tree must read the window its bar is installed on")
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun everyIslandOfAWindowReadsIt() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        try {
            // The everyday shape of an application: a menu bar and two islands in the window's own Swing
            // layout, each composing components of its own. Whichever island mounts first is the one
            // that creates the window's shared composition scope, so this pins that the window is stated
            // to every island of it rather than only to that first one.
            //
            // Each island gets a container to itself: an applier addresses the children of the container
            // it composes into by index, so an island sharing a container with another would be
            // composing into an index space holding children it does not account for.
            val menuBar = JMenuBar()
            val menu = WindowReader()
            frame.jMenuBar = menuBar
            menuBar.setContent {
                menu.Read()
                Menu("File") { MenuItem("Open", onClick = {}) }
            }

            val first = WindowReader()
            JPanel().also { frame.contentPane.add(it) }.setContent {
                first.Read()
                Label(text = "first island")
            }

            val second = WindowReader()
            JPanel().also { frame.contentPane.add(it) }.setContent {
                second.Read()
                Label(text = "second island")
            }
            frame.pack()

            awaitComposed("the menu bar composes", menu)
            awaitComposed("the first content island composes", first)
            awaitComposed("the second content island composes", second)
            assertSame(frame, menu.seen, "the menu bar must read the window it is installed on")
            assertSame(frame, first.seen, "an island of a window must read that window")
            assertSame(frame, second.seen, "a further island of the same window must read that window too")
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun aComposedCellReadsTheWindowItsListIsComposedIn() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        try {
            // A cell is composed into a renderer host that hangs off no window at all - the widget
            // stamps it to paint a row and never adds it to the tree. What the cell is under is the
            // composition it belongs to, so it reads the window that composition is in.
            val cell = WindowReader()
            frame.setContent {
                ListBox(
                    items = listOf("row"),
                    itemContent = { item ->
                        cell.Read()
                        Label(text = item)
                    },
                )
            }
            frame.pack()

            awaitComposed("the list stamps its cell", cell)
            assertSame(frame, cell.seen, "a composed cell must read the window its list is composed in")
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun contentSetOnAnOwnedDialogReadsTheDialogAndNotItsOwner() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val owner = realizedFrame()
        val dialog = JDialog(owner).apply { pack() }
        try {
            // An owned window's Swing parent is the window that owns it, so the window a dialog is in
            // has to be answered as the dialog itself. Content reading its owner instead would anchor a
            // peer of its own - a file chooser, a nested dialog - to the frame behind it.
            val island = WindowReader()
            (dialog as Container).setContent { island.Read() }

            awaitComposed("the content set on the dialog composes", island)
            assertSame(dialog, island.seen, "content set on an owned dialog must read that dialog")
        } finally {
            dialog.dispose()
            owner.dispose()
        }
    }

    @Test
    fun anIslandInsideAComposedWindowReadsThatWindow() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        // The declaration is composed under no window at all, and the peer it realizes is a window of
        // its own: an island nested in that peer is in the window the peer is, whatever the composition
        // the declaration was made in is under.
        val host = JPanel()
        setContent {
            Window(onCloseRequest = {}, title = ISLAND_HOST_WINDOW_TITLE, visible = false) {
                SwingNode(factory = { host })
            }
        }

        val peer = onWindowWithTitle(ISLAND_HOST_WINDOW_TITLE).fetch<JFrame>()
        val island = WindowReader()
        val handle = host.setContent { island.Read() }
        awaitIdle()

        assertTrue(island.composed, "an island mounted in a composed window must compose")
        assertSame(peer, island.seen, "an island mounted inside a composed window must read that window")
        handle.dispose()
    }

    @Test
    fun anIslandInsideAComposedDialogReadsTheDialogAndNotTheWindowAroundIt() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        // The dialog is declared in the content of a window, so the two windows differ: an island in the
        // dialog is in the dialog, not in the window the declaration hangs off. Reading the window
        // instead would anchor a peer opened out of the island - a nested dialog, a file chooser - to the
        // frame behind the dialog it was opened from.
        val host = JPanel()
        setContent {
            Window(onCloseRequest = {}, title = ISLAND_HOST_OWNER_TITLE, visible = false) {
                Dialog(onCloseRequest = {}, title = ISLAND_HOST_DIALOG_TITLE, visible = false) {
                    SwingNode(factory = { host })
                }
            }
        }

        val peer = onWindowWithTitle(ISLAND_HOST_DIALOG_TITLE).fetch<JDialog>()
        val island = WindowReader()
        val handle = host.setContent { island.Read() }
        awaitIdle()

        assertTrue(island.composed, "an island mounted in a composed dialog must compose")
        assertSame(peer, island.seen, "an island mounted inside a composed dialog must read that dialog")
        handle.dispose()
    }

    /**
     * Records the [LocalWindow] of wherever [Read] is composed, and whether it has composed at all.
     *
     * The two are separate so a case can wait for the composition to have happened and then assert on
     * the window it read: a wrong window is then reported as that window, not as a wait that timed out.
     */
    private class WindowReader {
        var composed: Boolean = false
            private set

        var seen: java.awt.Window? = null
            private set

        @Composable
        fun Read() {
            seen = LocalWindow.current
            composed = true
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

    /**
     * Suspends on the EDT until [reader] has composed, yielding the EDT back between checks so the
     * window's real frame-clock timer can fire and the window recomposer can mount the content. Content
     * that never composes fails the test at the deadline, naming [description], instead of hanging.
     */
    private suspend fun awaitComposed(
        description: String,
        reader: WindowReader,
    ) {
        try {
            withTimeout(SETTLE_TIMEOUT) {
                while (!reader.composed) {
                    yield()
                }
            }
        } catch (timedOut: TimeoutCancellationException) {
            throw AssertionError("Timed out after $SETTLE_TIMEOUT waiting until $description", timedOut)
        }
    }

    private companion object {
        val SETTLE_TIMEOUT = 10.seconds
        const val ISLAND_HOST_WINDOW_TITLE: String = "local-window-island-host"
        const val ISLAND_HOST_OWNER_TITLE: String = "local-window-island-dialog-owner"
        const val ISLAND_HOST_DIALOG_TITLE: String = "local-window-island-dialog"
    }
}
