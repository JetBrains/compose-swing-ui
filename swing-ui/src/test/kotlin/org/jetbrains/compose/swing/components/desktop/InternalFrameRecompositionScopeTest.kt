package org.jetbrains.compose.swing.components.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.test.SwingMatcher.Companion.hasAnyDescendant
import org.jetbrains.compose.swing.test.SwingMatcher.Companion.hasText
import org.jetbrains.compose.swing.test.SwingMatcher.Companion.isOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Rectangle
import javax.swing.JDesktopPane
import javax.swing.JInternalFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The state a frame is declared with is read where that frame is composed, so what a user does to one
 * frame reaches that frame's composition and no other's: a drag writes the new geometry back into the
 * state driving the dragged frame, and the desktop declaring the frames - along with the frames standing
 * beside it, whose bodies that declaration carries - is left as it is.
 *
 * A drag reports every position the frame passes through, so a desktop that its frames' moves recompose
 * would re-collect every frame it holds for each of them.
 */
class InternalFrameRecompositionScopeTest {
    @Test
    fun draggingAFrameRecomposesNeitherTheDesktopNorTheFramesBesideIt() = runComposeSwingTest {
        val editor = InternalFrameState(Rectangle(0, 0, 100, 100))
        val console = InternalFrameState(Rectangle(200, 0, 120, 90))
        var consoleTitle by mutableStateOf("Console")
        val desktopPasses = IntArray(1)
        val consolePasses = IntArray(1)
        setContent {
            DesktopPane {
                desktopPasses[0]++
                InternalFrame(title = "Editor", state = editor) { Label(text = "editor") }
                InternalFrame(title = consoleTitle, state = console) {
                    consolePasses[0]++
                    Label(text = "console")
                }
            }
        }
        awaitIdle()
        val settledDesktop = desktopPasses[0]
        val settledConsole = consolePasses[0]

        val editorFrame =
            onNode(isOfType<JInternalFrame>() and hasAnyDescendant(hasText("editor"))).fetch<JInternalFrame>()
        onNodeOfType<JDesktopPane>().fetch().dragFrameTo(editorFrame, 55, 65)
        awaitIdle()

        assertEquals(Rectangle(55, 65, 100, 100), editor.bounds, "the drag should reach the state driving the frame")
        assertEquals(settledDesktop, desktopPasses[0], "a frame's move should leave the desktop declaring it alone")
        assertEquals(settledConsole, consolePasses[0], "a frame's move should leave the frames beside it alone")

        // A change the desktop's own declaration reads does recompose it and the frames it declares, which
        // is what makes the counts above counts of recompositions that did not happen rather than of
        // counters that stopped.
        consoleTitle = "Log"
        awaitIdle()
        assertTrue(desktopPasses[0] > settledDesktop, "the desktop should recompose on the state its body reads")
        assertTrue(consolePasses[0] > settledConsole, "a recomposed desktop should re-declare the frames it holds")
    }
}
