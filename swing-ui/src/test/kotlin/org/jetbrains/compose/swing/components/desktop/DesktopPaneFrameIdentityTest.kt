package org.jetbrains.compose.swing.components.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.test.SwingMatcher.Companion.hasAnyDescendant
import org.jetbrains.compose.swing.test.SwingMatcher.Companion.hasText
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

/**
 * What identifies a declared frame decides which realized `JInternalFrame` a later composition lands on,
 * and so whether a frame survives the removal of a frame declared before it. A frame is identified by the
 * [InternalFrameState] it was declared with, by the key it was given, and failing both by its position
 * among the declared frames.
 *
 * The position a user drags a frame to is the frame's own and nothing declares it, so it is the sharpest
 * witness of whether a survivor is still the frame the user left behind: a survivor that lost its identity
 * is a different window carrying its predecessor's geometry.
 */
class DesktopPaneFrameIdentityTest {
    @Test
    fun removingAKeyedFrameLeavesTheFramesDeclaredAfterItAsTheUserLeftThem() = runComposeSwingTest {
        var showEditor by mutableStateOf(true)
        setContent {
            DesktopPane {
                if (showEditor) {
                    internalFrame(title = "Editor", bounds = Rectangle(0, 0, 100, 100), key = "editor") {
                        Label(text = "editor")
                    }
                }
                internalFrame(title = "Console", bounds = Rectangle(10, 10, 120, 90), key = "console") {
                    Label(text = "console")
                }
            }
        }

        val console =
            onNode(isOfType<JInternalFrame>() and hasAnyDescendant(hasText("console"))).fetch<JInternalFrame>()
        onNodeOfType<JDesktopPane>().fetch().dragFrameTo(console, DRAGGED_X, DRAGGED_Y)

        showEditor = false
        awaitIdle()

        onAllNodesOfType<JInternalFrame>().assertCountEquals(1)
        val survivor = onNodeOfType<JInternalFrame>().fetch()
        assertEquals("Console", survivor.title, "only the frame still declared should remain")
        assertSame(console, survivor, "the surviving frame should be the window the user dragged")
        assertEquals(
            Rectangle(DRAGGED_X, DRAGGED_Y, 120, 90),
            console.bounds,
            "the surviving frame should stay where the user dragged it",
        )
    }

    @Test
    fun removingAStateDeclaredFrameLeavesTheFramesDeclaredAfterItAsTheUserLeftThem() = runComposeSwingTest {
        var showEditor by mutableStateOf(true)
        val editor = InternalFrameState(Rectangle(0, 0, 100, 100))
        val console = InternalFrameState(Rectangle(10, 10, 120, 90))
        setContent {
            DesktopPane {
                if (showEditor) {
                    internalFrame(title = "Editor", state = editor) { Label(text = "editor") }
                }
                internalFrame(title = "Console", state = console) { Label(text = "console") }
            }
        }

        val consoleFrame =
            onNode(isOfType<JInternalFrame>() and hasAnyDescendant(hasText("console"))).fetch<JInternalFrame>()
        onNodeOfType<JDesktopPane>().fetch().dragFrameTo(consoleFrame, DRAGGED_X, DRAGGED_Y)
        awaitIdle()

        showEditor = false
        awaitIdle()

        onAllNodesOfType<JInternalFrame>().assertCountEquals(1)
        val survivor = onNodeOfType<JInternalFrame>().fetch()
        assertEquals("Console", survivor.title, "only the frame still declared should remain")
        assertSame(consoleFrame, survivor, "the surviving frame should be the window the user dragged")
        assertEquals(
            Rectangle(DRAGGED_X, DRAGGED_Y, 120, 90),
            console.bounds,
            "the surviving frame's state should still hold the geometry the user gave it",
        )
    }

    @Test
    fun aCallerKeyIsNeverTakenForThePositionOfAnUnkeyedFrame() = runComposeSwingTest {
        // The unkeyed frame stands at position 0 and the keyed one names 0, so a shared key space would
        // hand the survivor its predecessor's window.
        var showEditor by mutableStateOf(true)
        setContent {
            DesktopPane {
                if (showEditor) {
                    internalFrame(title = "Editor", bounds = Rectangle(0, 0, 100, 100)) { Label(text = "editor") }
                }
                internalFrame(title = "Console", bounds = Rectangle(10, 10, 120, 90), key = 0) {
                    Label(text = "console")
                }
            }
        }

        val console =
            onNode(isOfType<JInternalFrame>() and hasAnyDescendant(hasText("console"))).fetch<JInternalFrame>()
        onNodeOfType<JDesktopPane>().fetch().dragFrameTo(console, DRAGGED_X, DRAGGED_Y)

        showEditor = false
        awaitIdle()

        assertSame(
            console,
            onNode(isOfType<JInternalFrame>() and hasAnyDescendant(hasText("console"))).fetch<JInternalFrame>(),
            "the keyed frame should keep its own window",
        )
        assertEquals(
            Rectangle(DRAGGED_X, DRAGGED_Y, 120, 90),
            console.bounds,
            "the keyed frame should stay where the user dragged it",
        )
    }

    @Test
    fun anUnkeyedPlainBoundsFrameIsIdentifiedByItsPosition() = runComposeSwingTest {
        // The documented fallback: with nothing to identify them by, the frames declared after a removed
        // one take over the windows of the frames they shift into.
        var showEditor by mutableStateOf(true)
        setContent {
            DesktopPane {
                if (showEditor) {
                    internalFrame(title = "Editor", bounds = Rectangle(0, 0, 100, 100)) { Label(text = "editor") }
                }
                internalFrame(title = "Console", bounds = Rectangle(10, 10, 120, 90)) { Label(text = "console") }
            }
        }

        val editor =
            onNode(isOfType<JInternalFrame>() and hasAnyDescendant(hasText("editor"))).fetch<JInternalFrame>()
        val console =
            onNode(isOfType<JInternalFrame>() and hasAnyDescendant(hasText("console"))).fetch<JInternalFrame>()

        showEditor = false
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
