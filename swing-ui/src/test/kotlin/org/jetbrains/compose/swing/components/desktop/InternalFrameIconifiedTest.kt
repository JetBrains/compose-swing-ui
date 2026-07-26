package org.jetbrains.compose.swing.components.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.toolTip
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Rectangle
import java.beans.PropertyVetoException
import java.beans.VetoableChangeListener
import javax.swing.JInternalFrame
import javax.swing.JInternalFrame.JDesktopIcon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * An internal frame's iconified state is two-way when the frame is declared with an [InternalFrameState]:
 * assigning to the state iconifies or deiconifies the realized frame, and the user's own iconify control
 * writes the new value back into the state.
 *
 * An iconified frame leaves the desktop and its icon takes its place, so the icon is how the frame is
 * reached while it is iconified. The transition needs a desktop to put that icon on, which a frame reaches
 * only after the composition pass that declares it has run - so a frame declared iconified from the start
 * is iconified the moment the desktop takes it, and every later change as it is declared.
 */
class InternalFrameIconifiedTest {
    @Test
    fun aFrameOpensIconifiedWhenItsStateSaysSo() = runComposeSwingTest {
        val state = InternalFrameState(Rectangle(10, 20, 300, 200), iconified = true)
        setContent {
            DesktopPane {
                internalFrame(
                    title = "Editor",
                    state = state,
                    controls = InternalFrameControls(iconifiable = true),
                ) { Label(text = "body") }
            }
        }

        val frame = onNodeOfType<JDesktopIcon>().fetch().internalFrame
        assertTrue(frame.isIcon, "the frame should open iconified as its state declares")
        onNodeOfType<JInternalFrame>().assertDoesNotExist()
        assertEquals(Rectangle(10, 20, 300, 200), frame.bounds, "the frame keeps the geometry it reappears on")
        assertTrue(state.iconified, "the declaration should stand")
    }

    @Test
    fun assigningIconifiedIconifiesAndDeiconifiesTheFrame() = runComposeSwingTest {
        val state = InternalFrameState(Rectangle(10, 20, 300, 200))
        setContent {
            DesktopPane {
                internalFrame(
                    title = "Editor",
                    state = state,
                    controls = InternalFrameControls(iconifiable = true),
                ) { Label(text = "body") }
            }
        }

        onNodeOfType<JInternalFrame>().assertExists()

        state.iconified = true
        awaitIdle()
        assertTrue(
            onNodeOfType<JDesktopIcon>().fetch().internalFrame.isIcon,
            "assigning iconified should iconify the frame",
        )
        onNodeOfType<JInternalFrame>().assertDoesNotExist()

        state.iconified = false
        awaitIdle()
        val frame = onNodeOfType<JInternalFrame>().fetch()
        assertFalse(frame.isIcon, "assigning iconified back should deiconify the frame")
        assertEquals(Rectangle(10, 20, 300, 200), frame.bounds, "the frame should reappear on the geometry it kept")
    }

    @Test
    fun theUsersIconifyControlWritesBack() = runComposeSwingTest {
        val state = InternalFrameState(Rectangle(10, 20, 300, 200))
        setContent {
            DesktopPane {
                internalFrame(
                    title = "Editor",
                    state = state,
                    controls = InternalFrameControls(iconifiable = true),
                ) { Label(text = "body") }
            }
        }

        // The iconify control iconifies the frame through the frame itself, which is where a mouse click on
        // the title pane lands.
        onNodeOfType<JInternalFrame>().fetch().isIcon = true
        awaitIdle()
        assertTrue(state.iconified, "the user's iconify should reach the state driving the frame")

        onNodeOfType<JDesktopIcon>().fetch().internalFrame.isIcon = false
        awaitIdle()
        assertFalse(state.iconified, "the user's deiconify should reach the state driving the frame")
        onNodeOfType<JInternalFrame>().assertExists()
    }

    @Test
    fun anIconifiedFrameStaysIconifiedAcrossARecomposition() = runComposeSwingTest {
        val state = InternalFrameState(Rectangle(10, 20, 300, 200), iconified = true)
        var tip by mutableStateOf("Edits the document")
        setContent {
            DesktopPane {
                internalFrame(
                    title = "Editor",
                    state = state,
                    controls = InternalFrameControls(iconifiable = true),
                    modifier = SwingModifier.toolTip(tip),
                ) { Label(text = "body") }
            }
        }

        tip = "Holds the open document"
        awaitIdle()

        val frame = onNodeOfType<JDesktopIcon>().fetch().internalFrame
        assertEquals("Holds the open document", frame.toolTipText, "the recomposition should have reached the frame")
        assertTrue(frame.isIcon, "a recomposition should leave an iconified frame iconified")
        onNodeOfType<JInternalFrame>().assertDoesNotExist()
    }

    @Test
    fun anUndeclaredIconifyStandsAcrossARecomposition() = runComposeSwingTest {
        var tip by mutableStateOf("Edits the document")
        setContent {
            DesktopPane {
                internalFrame(
                    title = "Editor",
                    bounds = Rectangle(10, 20, 300, 200),
                    controls = InternalFrameControls(iconifiable = true),
                    modifier = SwingModifier.toolTip(tip),
                ) { Label(text = "body") }
            }
        }

        val frame = onNodeOfType<JInternalFrame>().fetch()
        frame.isIcon = true
        awaitIdle()

        tip = "Holds the open document"
        awaitIdle()

        assertEquals("Holds the open document", frame.toolTipText, "the recomposition should have reached the frame")
        assertTrue(
            frame.isIcon,
            "a frame that declares no iconified state is left in the state the user put it in",
        )
        onNodeOfType<JInternalFrame>().assertDoesNotExist()
    }

    @Test
    fun theStateFollowsTheFrameItIsHandedOnRecomposition() = runComposeSwingTest {
        val first = InternalFrameState(Rectangle(0, 0, 100, 100))
        val second = InternalFrameState(Rectangle(0, 0, 100, 100))
        var useSecond by mutableStateOf(false)
        setContent {
            DesktopPane {
                internalFrame(
                    title = "Editor",
                    state = if (useSecond) second else first,
                    controls = InternalFrameControls(iconifiable = true),
                ) { Label(text = "body") }
            }
        }

        useSecond = true
        awaitIdle()

        onNodeOfType<JInternalFrame>().fetch().isIcon = true
        awaitIdle()
        assertTrue(second.iconified, "the newly declared state should receive the iconify")
        assertFalse(first.iconified, "the state that left the declaration should be left alone")
    }

    @Test
    fun aVetoedIconifyLeavesTheStateMatchingTheFrame() = runComposeSwingTest {
        val state = InternalFrameState(Rectangle(10, 20, 300, 200))
        setContent {
            DesktopPane {
                internalFrame(
                    title = "Editor",
                    state = state,
                    controls = InternalFrameControls(iconifiable = true),
                ) { Label(text = "body") }
            }
        }

        val frame = onNodeOfType<JInternalFrame>().fetch()
        frame.addVetoableChangeListener(
            VetoableChangeListener { event ->
                if (event.propertyName == JInternalFrame.IS_ICON_PROPERTY) throw PropertyVetoException("no", event)
            },
        )

        state.iconified = true
        awaitIdle()

        assertFalse(frame.isIcon, "a vetoed transition should leave the frame as it was")
        assertFalse(state.iconified, "the state should report what the frame actually is")
        onNodeOfType<JInternalFrame>().assertExists()
    }

    @Test
    fun droppingAnIconifiedFrameDeclaredByItsStateTakesItsIconWithIt() = runComposeSwingTest {
        val state = InternalFrameState(Rectangle(0, 0, 100, 100), iconified = true)
        var show by mutableStateOf(true)
        setContent {
            DesktopPane {
                if (show) {
                    internalFrame(
                        title = "Editor",
                        state = state,
                        controls = InternalFrameControls(iconifiable = true),
                    ) { Label(text = "body") }
                }
            }
        }

        onNodeOfType<JDesktopIcon>().assertExists()

        show = false
        awaitIdle()
        // The icon standing in for the frame goes with it, so nothing of the frame is left on the desktop.
        onNodeOfType<JDesktopIcon>().assertDoesNotExist()
        onNodeOfType<JInternalFrame>().assertDoesNotExist()
    }
}
