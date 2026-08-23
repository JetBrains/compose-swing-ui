package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.underMetal
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import javax.swing.JFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Which apply pass a [ToolBar] settles its declared `floating` state in.
 *
 * One pass: the pass that declares a floating bar is the pass that moves it into a window of its own,
 * and the pass that declares one no bar can take is the pass that hands the caller the docked state it
 * settled for. A bar hangs nowhere while its own update block runs, so the state is settled at the end of
 * the change pass instead - the point at which the bar already stands in the container that declared it,
 * and so has a window to open its own beside.
 */
class ToolBarFloatingSettlesInOnePassTest {
    @Test
    fun aBarDeclaredFloatingAsItArrivesIsMovedOutByThePassThatDeclaresIt() = underMetal {
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val reported = mutableListOf<Boolean>()
            var shown by mutableStateOf(false)
            setContent {
                // Composed `visible = false`: sizing to content realizes the peer, which is all the
                // bar needs to have a window to open its own beside.
                Window(onCloseRequest = {}, title = WINDOW_TITLE, visible = false) {
                    if (shown) {
                        ToolBar(floating = true, onFloatingChange = { reported += it }) {
                            Button(text = "New", onClick = {})
                        }
                    }
                }
            }
            val frame = onWindowWithTitle(WINDOW_TITLE).fetch<JFrame>()
            assertNull(toolBarIn(frame), "the window should open holding no tool bar")

            mainClock.autoAdvance = false
            shown = true
            awaitIdle()
            mainClock.advanceTimeByFrame()

            val bar = assertNotNull(toolBarIn(frame), "the pass declaring the bar should mount it")
            assertTrue(
                bar.isFloatingNow,
                "the pass that declares a floating bar should leave it in a window of its own, not " +
                    "docked for a later pass to move",
            )
            assertEquals(
                emptyList(),
                reported,
                "a declaration the bar takes is the caller's own, so nothing is reported back to it",
            )
        }
    }

    @Test
    fun aBarThatCannotFloatHandsTheCallerItsAnswerInThePassThatDeclaredIt() = runComposeSwingTest {
        val reported = mutableListOf<Boolean>()
        // No frame is sent for the whole test: the composition that mounts the bar is the pass under
        // measurement, and it applies without one.
        mainClock.autoAdvance = false
        setContent {
            ToolBar(floating = true, onFloatingChange = { reported += it }) {
                Button(text = "New", onClick = {})
            }
        }

        assertEquals(
            listOf(false),
            reported,
            "the pass declaring a bar that has no window to float out of should hand the caller the " +
                "docked state it settled for, rather than leave it to a later pass",
        )
    }

    private companion object {
        const val WINDOW_TITLE = "tool-bar-one-pass-float-test"
    }
}
