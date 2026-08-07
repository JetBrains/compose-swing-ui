package org.jetbrains.compose.swing.components.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.test.SwingMatcher.Companion.hasTitle
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Rectangle
import javax.swing.JInternalFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * One [InternalFrameState] drives one frame of a [DesktopPane]. A state driving two frames at once would
 * receive both frames' write-backs, each undoing what the other's user interaction wrote, so the second
 * frame to take a state stops the composition instead.
 *
 * The claim a frame has on its state lasts exactly as long as the frame does, so a state one frame gives
 * up is free for the next: a state passing from a leaving frame to an arriving one in a single pass
 * passes freely, and the arriving frame drives on it.
 */
class DesktopPaneStateClaimTest {
    @Test
    fun aStateDeclaredByTwoFramesAtOnceStopsTheComposition() = runComposeSwingTest {
        val shared = InternalFrameState(Rectangle(0, 0, 120, 90))

        val failure =
            assertFailsWith<IllegalArgumentException> {
                setContent {
                    DesktopPane {
                        InternalFrame(title = "Editor", state = shared) { Label(text = "editor") }
                        InternalFrame(title = "Console", state = shared) { Label(text = "console") }
                    }
                }
            }

        val message = failure.message.orEmpty()
        assertTrue(
            "DesktopPane frame state $shared is declared by more than one frame" in message,
            "the refusal should name the state the second frame tried to take: $message",
        )
    }

    @Test
    fun aStateHandedFromOneFrameToAnotherInOnePassDrivesTheFrameThatTookIt() = runComposeSwingTest {
        val shared = InternalFrameState(Rectangle(0, 0, 120, 90))
        // Each frame is keyed, so the pass that replaces one title with the other removes the frame
        // holding the state and adds a different frame declaring it, both in the same pass.
        var titles by mutableStateOf(listOf("Editor"))
        setContent {
            DesktopPane {
                titles.forEach { title ->
                    key(title) {
                        InternalFrame(title = title, state = shared) { Label(text = title.lowercase()) }
                    }
                }
            }
        }

        titles = listOf("Console")
        awaitIdle()

        onAllNodesOfType<JInternalFrame>().assertCountEquals(1)
        onNodeOfType<JInternalFrame>().assert(hasTitle("Console"))
        val frame = onNodeOfType<JInternalFrame>().fetch()

        shared.bounds = Rectangle(40, 50, 250, 150)
        awaitIdle()
        assertEquals(
            Rectangle(40, 50, 250, 150),
            frame.bounds,
            "the state should drive the frame that took it over",
        )
    }
}
