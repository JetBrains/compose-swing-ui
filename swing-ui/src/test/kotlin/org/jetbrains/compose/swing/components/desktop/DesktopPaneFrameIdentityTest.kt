package org.jetbrains.compose.swing.components.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.test.SwingMatcher.Companion.hasAnyDescendant
import org.jetbrains.compose.swing.test.SwingMatcher.Companion.hasText
import org.jetbrains.compose.swing.test.SwingMatcher.Companion.hasTitle
import org.jetbrains.compose.swing.test.SwingMatcher.Companion.isOfType
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Rectangle
import javax.swing.JDesktopPane
import javax.swing.JInternalFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * What identifies a declared frame decides which realized `JInternalFrame` a later composition lands on,
 * and so whether a frame survives the removal of a frame declared before it. A frame declared at a place
 * of its own keeps its window whatever happens to the frames around it; frames declared from one place, as
 * a loop over a list declares them, are told apart by the position they are declared in unless each is
 * wrapped in a [key].
 *
 * The position a user drags a frame to is the frame's own and nothing declares it, so it is the sharpest
 * witness of whether a survivor is still the frame the user left behind: a survivor that lost its identity
 * is a different window carrying its predecessor's geometry.
 */
class DesktopPaneFrameIdentityTest {
    @Test
    fun removingAKeyedFrameLeavesTheFramesDeclaredAfterItAsTheUserLeftThem() = runComposeSwingTest {
        var titles by mutableStateOf(listOf("Editor", "Console"))
        setContent {
            DesktopPane {
                titles.forEach { title ->
                    key(title) {
                        InternalFrame(title = title, bounds = Rectangle(10, 10, 120, 90)) {
                            Label(text = title.lowercase())
                        }
                    }
                }
            }
        }

        val console =
            onNode(isOfType<JInternalFrame>() and hasAnyDescendant(hasText("console"))).fetch<JInternalFrame>()
        onNodeOfType<JDesktopPane>().fetch().dragFrameTo(console, DRAGGED_X, DRAGGED_Y)

        titles = listOf("Console")
        awaitIdle()

        onAllNodesOfType<JInternalFrame>().assertCountEquals(1)
        onNodeOfType<JInternalFrame>().assert(hasTitle("Console"))
        val survivor = onNodeOfType<JInternalFrame>().fetch()
        assertSame(console, survivor, "the surviving frame should be the window the user dragged")
        assertEquals(
            Rectangle(DRAGGED_X, DRAGGED_Y, 120, 90),
            console.bounds,
            "the surviving frame should stay where the user dragged it",
        )
    }

    @Test
    fun removingAStateDeclaredFrameLeavesTheFramesDeclaredAfterItAsTheUserLeftThem() = runComposeSwingTest {
        val editor = InternalFrameState(Rectangle(0, 0, 100, 100))
        val console = InternalFrameState(Rectangle(10, 10, 120, 90))
        var frames by mutableStateOf(listOf("Editor" to editor, "Console" to console))
        setContent {
            DesktopPane {
                frames.forEach { (title, state) ->
                    key(state) {
                        InternalFrame(title = title, state = state) { Label(text = title.lowercase()) }
                    }
                }
            }
        }

        val consoleFrame =
            onNode(isOfType<JInternalFrame>() and hasAnyDescendant(hasText("console"))).fetch<JInternalFrame>()
        onNodeOfType<JDesktopPane>().fetch().dragFrameTo(consoleFrame, DRAGGED_X, DRAGGED_Y)
        awaitIdle()

        frames = listOf("Console" to console)
        awaitIdle()

        onAllNodesOfType<JInternalFrame>().assertCountEquals(1)
        onNodeOfType<JInternalFrame>().assert(hasTitle("Console"))
        val survivor = onNodeOfType<JInternalFrame>().fetch()
        assertSame(consoleFrame, survivor, "the surviving frame should be the window the user dragged")
        assertEquals(
            Rectangle(DRAGGED_X, DRAGGED_Y, 120, 90),
            console.bounds,
            "the surviving frame's state should still hold the geometry the user gave it",
        )
    }

    @Test
    fun aFrameDeclaredAtItsOwnPlaceKeepsItsWindowThroughTheRemovalOfAnother() = runComposeSwingTest {
        // Each frame is declared at a place of its own, so neither needs a key of its own to be told
        // apart: the composition holds the two declarations apart by where they stand.
        var showEditor by mutableStateOf(true)
        setContent {
            DesktopPane {
                if (showEditor) {
                    InternalFrame(title = "Editor", bounds = Rectangle(0, 0, 100, 100)) { Label(text = "editor") }
                }
                InternalFrame(title = "Console", bounds = Rectangle(10, 10, 120, 90)) { Label(text = "console") }
            }
        }

        val console =
            onNode(isOfType<JInternalFrame>() and hasAnyDescendant(hasText("console"))).fetch<JInternalFrame>()
        onNodeOfType<JDesktopPane>().fetch().dragFrameTo(console, DRAGGED_X, DRAGGED_Y)

        showEditor = false
        awaitIdle()

        onAllNodesOfType<JInternalFrame>().assertCountEquals(1)
        assertSame(
            console,
            onNodeOfType<JInternalFrame>().fetch(),
            "the frame declared on its own should keep its window",
        )
        assertEquals(
            Rectangle(DRAGGED_X, DRAGGED_Y, 120, 90),
            console.bounds,
            "the surviving frame should stay where the user dragged it",
        )
    }

    @Test
    fun reorderingKeyedFramesReordersTheirStackingOrder() = runComposeSwingTest {
        // Every frame is keyed, so a reordered declaration moves the frames that stand instead of
        // rebuilding them, and the desktop's stacking order is what carries their order: the frame
        // declared earlier paints above the ones declared after it, the same rule LayeredPane holds to.
        var titles by mutableStateOf(listOf("Editor", "Console"))
        setContent {
            DesktopPane {
                titles.forEach { title ->
                    key(title) {
                        InternalFrame(title = title, bounds = Rectangle(10, 10, 120, 90)) {
                            Label(text = title.lowercase())
                        }
                    }
                }
            }
        }

        val desktop = onNodeOfType<JDesktopPane>().fetch()
        val editor =
            onNode(isOfType<JInternalFrame>() and hasAnyDescendant(hasText("editor"))).fetch<JInternalFrame>()
        val console =
            onNode(isOfType<JInternalFrame>() and hasAnyDescendant(hasText("console"))).fetch<JInternalFrame>()
        assertTrue(
            desktop.getIndexOf(editor) < desktop.getIndexOf(console),
            "the frame declared first should stand ahead of the one declared after it",
        )

        titles = listOf("Console", "Editor")
        awaitIdle()

        assertSame(
            editor,
            onNode(isOfType<JInternalFrame>() and hasAnyDescendant(hasText("editor"))).fetch(),
            "reordering the declaration should move a keyed frame, not rebuild it",
        )
        assertTrue(
            desktop.getIndexOf(console) < desktop.getIndexOf(editor),
            "reordering the declaration should reorder the frames' stacking position on the desktop",
        )
    }

    @Test
    fun unkeyedFramesDeclaredInOneLoopAreIdentifiedByTheirPosition() = runComposeSwingTest {
        // The documented fallback: with nothing to identify them by, the frames declared after a removed
        // one take over the windows of the frames they shift into.
        var titles by mutableStateOf(listOf("Editor", "Console"))
        setContent {
            DesktopPane {
                titles.forEach { title ->
                    InternalFrame(title = title, bounds = Rectangle(10, 10, 120, 90)) {
                        Label(text = title.lowercase())
                    }
                }
            }
        }

        val editor =
            onNode(isOfType<JInternalFrame>() and hasAnyDescendant(hasText("editor"))).fetch<JInternalFrame>()
        val console =
            onNode(isOfType<JInternalFrame>() and hasAnyDescendant(hasText("console"))).fetch<JInternalFrame>()

        titles = listOf("Console")
        awaitIdle()

        val survivor =
            onNode(isOfType<JInternalFrame>() and hasAnyDescendant(hasText("console"))).fetch<JInternalFrame>()
        assertSame(editor, survivor, "the survivor should take over the window of the position it shifted into")
        assertNotSame(console, survivor, "the frame declared second should not keep its own window")
    }

    private companion object {
        const val DRAGGED_X: Int = 55
        const val DRAGGED_Y: Int = 65
    }
}
