package org.jetbrains.compose.swing.components.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.toolTip
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import java.awt.Rectangle
import java.beans.PropertyVetoException
import java.beans.VetoableChangeListener
import javax.swing.JDesktopPane
import javax.swing.JInternalFrame
import javax.swing.JInternalFrame.JDesktopIcon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * An internal frame's maximized state is two-way when the frame is declared with an [InternalFrameState]:
 * assigning to the state maximizes or restores the realized frame, and the user's own maximize and restore
 * controls write the new value back into the state.
 *
 * A maximized frame is spread across the whole desktop, which needs a desktop to spread it across - a frame
 * reaches one only after the composition pass that declares it has run, so a frame declared maximized from
 * the start is maximized the moment the desktop takes it, and every later change as it is declared. The
 * geometry the state carries is the one a restore returns the frame to, and the desktop-filling geometry
 * never displaces it.
 */
class InternalFrameMaximizedTest {
    /**
     * A desktop of a fixed size, so the geometry a maximized frame takes is a known one and differs from
     * every frame geometry these tests declare.
     */
    private val desktopSize = Dimension(400, 300)

    private fun ComposeSwingTest.declareFrame(
        state: InternalFrameState,
        toolTip: String = "Edits the document",
    ) = setContent {
        DesktopPane(modifier = SwingModifier.preferredSize(desktopSize)) {
            internalFrame(
                title = "Editor",
                state = state,
                controls = InternalFrameControls(maximizable = true, iconifiable = true),
                modifier = SwingModifier.toolTip(toolTip),
            ) { Label(text = "body") }
        }
    }

    @Test
    fun aFrameOpensMaximizedWhenItsStateSaysSo() = runComposeSwingTest {
        val state = InternalFrameState(Rectangle(10, 20, 100, 80), maximized = true)
        declareFrame(state)

        val frame = onNodeOfType<JInternalFrame>().fetch()
        assertTrue(frame.isMaximum, "the frame should open maximized as its state declares")
        assertEquals(
            Rectangle(10, 20, 100, 80),
            state.bounds,
            "the geometry the state carries is where a restore returns the frame to",
        )

        state.maximized = false
        awaitIdle()
        assertEquals(
            Rectangle(10, 20, 100, 80),
            frame.bounds,
            "restoring should take the frame to the geometry it opened declaring",
        )
    }

    @Test
    fun assigningMaximizedMaximizesAndRestoresTheFrame() = runComposeSwingTest {
        val state = InternalFrameState(Rectangle(10, 20, 100, 80))
        declareFrame(state)

        val desktop = onNodeOfType<JDesktopPane>().fetch()
        val frame = onNodeOfType<JInternalFrame>().fetch()
        state.maximized = true
        awaitIdle()
        assertTrue(frame.isMaximum, "assigning maximized should maximize the frame")
        assertEquals(
            Rectangle(0, 0, desktop.width, desktop.height),
            frame.bounds,
            "the maximized frame should fill the desktop",
        )

        state.maximized = false
        awaitIdle()
        assertFalse(frame.isMaximum, "assigning maximized back should restore the frame")
        assertEquals(
            Rectangle(10, 20, 100, 80),
            frame.bounds,
            "the restored frame should return to the geometry the state carries",
        )
    }

    @Test
    fun theUsersMaximizeControlWritesBack() = runComposeSwingTest {
        val state = InternalFrameState(Rectangle(10, 20, 100, 80))
        declareFrame(state)

        // The maximize and restore controls maximize and restore the frame through the frame itself, which
        // is where a click on the title pane lands.
        val frame = onNodeOfType<JInternalFrame>().fetch()
        frame.isMaximum = true
        awaitIdle()
        assertTrue(state.maximized, "the user's maximize should reach the state driving the frame")

        frame.isMaximum = false
        awaitIdle()
        assertFalse(state.maximized, "the user's restore should reach the state driving the frame")
    }

    @Test
    fun theUsersMaximizeLeavesTheGeometryTheStateCarries() = runComposeSwingTest {
        val state = InternalFrameState(Rectangle(10, 20, 100, 80))
        declareFrame(state)

        val frame = onNodeOfType<JInternalFrame>().fetch()
        frame.isMaximum = true
        awaitIdle()
        assertEquals(
            Rectangle(10, 20, 100, 80),
            state.bounds,
            "the desktop-filling geometry should not displace the geometry the state carries",
        )

        frame.isMaximum = false
        awaitIdle()
        assertEquals(
            Rectangle(10, 20, 100, 80),
            state.bounds,
            "the restore should leave the state on the geometry the frame came back to",
        )
        assertEquals(Rectangle(10, 20, 100, 80), frame.bounds, "the frame should come back where it was")
    }

    @Test
    fun aGeometryDeclaredWhileMaximizedIsWhereTheFrameIsRestoredTo() = runComposeSwingTest {
        val state = InternalFrameState(Rectangle(10, 20, 100, 80))
        declareFrame(state)

        val desktop = onNodeOfType<JDesktopPane>().fetch()
        val frame = onNodeOfType<JInternalFrame>().fetch()
        state.maximized = true
        awaitIdle()

        state.bounds = Rectangle(40, 50, 120, 90)
        awaitIdle()
        assertTrue(frame.isMaximum, "a geometry declared while the frame is maximized should not restore it")
        assertEquals(
            Rectangle(0, 0, desktop.width, desktop.height),
            frame.bounds,
            "a maximized frame should go on filling the desktop",
        )

        state.maximized = false
        awaitIdle()
        assertEquals(
            Rectangle(40, 50, 120, 90),
            frame.bounds,
            "the restore should take the frame to the geometry declared while it was maximized",
        )
        assertEquals(Rectangle(40, 50, 120, 90), state.bounds, "the declaration should stand")
    }

    @Test
    fun aMaximizedFrameStaysMaximizedAcrossARecomposition() = runComposeSwingTest {
        val state = InternalFrameState(Rectangle(10, 20, 100, 80), maximized = true)
        var tip by mutableStateOf("Edits the document")
        setContent {
            DesktopPane(modifier = SwingModifier.preferredSize(desktopSize)) {
                internalFrame(
                    title = "Editor",
                    state = state,
                    controls = InternalFrameControls(maximizable = true),
                    modifier = SwingModifier.toolTip(tip),
                ) { Label(text = "body") }
            }
        }

        tip = "Holds the open document"
        awaitIdle()

        val frame = onNodeOfType<JInternalFrame>().fetch()
        assertEquals("Holds the open document", frame.toolTipText, "the recomposition should have reached the frame")
        assertTrue(frame.isMaximum, "a recomposition should leave a maximized frame maximized")
        assertEquals(
            Rectangle(10, 20, 100, 80),
            state.bounds,
            "a recomposition should leave the state on the geometry a restore returns the frame to",
        )
    }

    @Test
    fun aVetoedMaximizeLeavesTheStateMatchingTheFrame() = runComposeSwingTest {
        val state = InternalFrameState(Rectangle(10, 20, 100, 80))
        declareFrame(state)

        val frame = onNodeOfType<JInternalFrame>().fetch()
        frame.addVetoableChangeListener(
            VetoableChangeListener { event ->
                if (event.propertyName == JInternalFrame.IS_MAXIMUM_PROPERTY) throw PropertyVetoException("no", event)
            },
        )

        state.maximized = true
        awaitIdle()

        assertFalse(frame.isMaximum, "a vetoed transition should leave the frame as it was")
        assertFalse(state.maximized, "the state should report what the frame actually is")
        assertEquals(Rectangle(10, 20, 100, 80), frame.bounds, "the frame should keep the geometry it stands on")
    }

    @Test
    fun aFrameBecomesIconifiedAndMaximizedInOnePass() = runComposeSwingTest {
        val state = InternalFrameState(Rectangle(10, 20, 100, 80))
        declareFrame(state)

        // Both window states change before the next pass runs, so one pass applies both to a frame the
        // desktop already holds. A desktop manager maximizes an iconified frame by putting it back on the
        // desktop first, so the frame has to be maximized before it is iconified: the other order
        // deiconifies the frame the same pass has just iconified, leaving it in full view.
        state.iconified = true
        state.maximized = true
        awaitIdle()

        val frame = onNodeOfType<JDesktopIcon>().fetch().internalFrame
        assertTrue(frame.isIcon, "the frame should stand on the desktop as its icon")
        assertTrue(frame.isMaximum, "the frame behind the icon should be maximized")
        assertTrue(state.iconified, "the state should report the frame standing as its icon")
        assertTrue(state.maximized, "the state should report the frame maximized")

        state.iconified = false
        awaitIdle()
        assertTrue(
            onNodeOfType<JInternalFrame>().fetch().isMaximum,
            "deiconifying should bring back a maximized frame",
        )
        assertEquals(
            Rectangle(10, 20, 100, 80),
            state.bounds,
            "the geometry the state carries is where a restore returns the frame to",
        )
    }

    @Test
    fun aFrameIsIconifiedAndMaximizedAtOnce() = runComposeSwingTest {
        val state = InternalFrameState(Rectangle(10, 20, 100, 80), iconified = true, maximized = true)
        declareFrame(state)

        val frame = onNodeOfType<JDesktopIcon>().fetch().internalFrame
        assertTrue(frame.isIcon, "the frame should open as its icon")
        assertTrue(frame.isMaximum, "the frame behind the icon should be maximized")

        state.iconified = false
        awaitIdle()
        assertTrue(
            onNodeOfType<JInternalFrame>().fetch().isMaximum,
            "deiconifying should bring back a maximized frame",
        )
        assertTrue(state.maximized, "the state should still report the frame maximized")
    }

    @Test
    fun restoringAnIconifiedFrameReturnsItToTheGeometryTheStateCarries() = runComposeSwingTest {
        val state = InternalFrameState(Rectangle(10, 20, 100, 80), iconified = true, maximized = true)
        declareFrame(state)

        state.maximized = false
        awaitIdle()
        assertTrue(
            onNodeOfType<JDesktopIcon>().fetch().internalFrame.isIcon,
            "restoring should leave the frame standing as its icon",
        )

        state.iconified = false
        awaitIdle()
        val frame = onNodeOfType<JInternalFrame>().fetch()
        assertFalse(frame.isMaximum, "the frame should come back restored")
        assertEquals(
            Rectangle(10, 20, 100, 80),
            frame.bounds,
            "the frame should come back on the geometry the state carries",
        )
        assertEquals(Rectangle(10, 20, 100, 80), state.bounds, "the state should stand on its own geometry")
    }

    @Test
    fun aRestoreReturnsTheFrameToTheStateAfterTheDesktopHasMaximizedItAgain() = runComposeSwingTest {
        val state = InternalFrameState(Rectangle(10, 20, 100, 80), maximized = true)
        declareFrame(state)

        // A frame is spread across its desktop by the desktop manager, and a desktop spreads a frame that is
        // already maximized across itself again once it knows its own size. That is also when the manager
        // records where a restore is to take the frame - by then the frame stands on the desktop it was
        // spread across, so the record is no longer where the user left it, and only the state still is.
        val frame = onNodeOfType<JInternalFrame>().fetch()
        onNodeOfType<JDesktopPane>().fetch().desktopManager.maximizeFrame(frame)
        awaitIdle()

        state.maximized = false
        awaitIdle()
        assertEquals(
            Rectangle(10, 20, 100, 80),
            frame.bounds,
            "the restore should take the frame to the geometry the state carries",
        )
        assertEquals(Rectangle(10, 20, 100, 80), state.bounds, "the state should stand on its own geometry")
    }

    @Test
    fun maximizingAnIconifiedFrameReportsWhatTheFrameBecomes() = runComposeSwingTest {
        val state = InternalFrameState(Rectangle(10, 20, 100, 80), iconified = true)
        declareFrame(state)

        // A desktop manager maximizes an iconified frame by putting it back on the desktop first, so the
        // frame stops being an icon; the state reports the frame it now has.
        state.maximized = true
        awaitIdle()

        val frame = onNodeOfType<JInternalFrame>().fetch()
        assertTrue(frame.isMaximum, "the frame should be maximized")
        assertFalse(frame.isIcon, "the frame should have come back onto the desktop")
        assertFalse(state.iconified, "the state should report the frame standing on the desktop again")
        assertEquals(
            Rectangle(10, 20, 100, 80),
            state.bounds,
            "the geometry the state carries is where a restore returns the frame to",
        )
    }

    @Test
    fun theStateFollowsTheFrameItIsHandedOnRecomposition() = runComposeSwingTest {
        val first = InternalFrameState(Rectangle(0, 0, 100, 100))
        val second = InternalFrameState(Rectangle(0, 0, 100, 100))
        var useSecond by mutableStateOf(false)
        setContent {
            DesktopPane(modifier = SwingModifier.preferredSize(desktopSize)) {
                internalFrame(
                    title = "Editor",
                    state = if (useSecond) second else first,
                    controls = InternalFrameControls(maximizable = true),
                ) { Label(text = "body") }
            }
        }

        useSecond = true
        awaitIdle()

        onNodeOfType<JInternalFrame>().fetch().isMaximum = true
        awaitIdle()
        assertTrue(second.maximized, "the newly declared state should receive the maximize")
        assertFalse(first.maximized, "the state that left the declaration should be left alone")
    }

    @Test
    fun anUndeclaredMaximizeStandsAcrossARecomposition() = runComposeSwingTest {
        var tip by mutableStateOf("Edits the document")
        setContent {
            DesktopPane(modifier = SwingModifier.preferredSize(desktopSize)) {
                internalFrame(
                    title = "Editor",
                    bounds = Rectangle(10, 20, 100, 80),
                    controls = InternalFrameControls(maximizable = true),
                    modifier = SwingModifier.toolTip(tip),
                ) { Label(text = "body") }
            }
        }

        val frame = onNodeOfType<JInternalFrame>().fetch()
        frame.isMaximum = true
        awaitIdle()

        tip = "Holds the open document"
        awaitIdle()

        assertEquals("Holds the open document", frame.toolTipText, "the recomposition should have reached the frame")
        assertTrue(
            frame.isMaximum,
            "a frame that declares no maximized state is left in the state the user put it in",
        )
    }
}
