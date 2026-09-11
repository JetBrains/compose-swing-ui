package org.jetbrains.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.click
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.SwingMatcher.Companion.isOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.BorderLayout
import java.awt.Container
import java.awt.Cursor
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLayer
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioral tests for the glass pane a [LayerScope.GlassPane] declares: which points the pane answers
 * for, and what the layer is left carrying once the declaration goes away.
 *
 * The pane is laid out over the whole layer, so what it answers for a point decides what everything that
 * finds a component by geometry reaches - the cursor shown, the drop target found. The tests lay the
 * layer's tree out by hand at the size they set, which gives the pane and its children the bounds they
 * are hit-tested against.
 *
 * Which component a click reaches is decided by the window the layer stands in, so the case about clicks
 * composes under a realized `Window` and is skipped in headless environments.
 */
class LayerGlassPaneTest {
    @Test
    fun theGlassPaneAnswersForAPointOnlyWhereOneOfItsShownChildrenLies() = runComposeSwingTest {
        setContent {
            Layer(onPaint = { _, _, _, paintView -> paintView() }) {
                Label(text = "body", modifier = SwingModifier.view())
                GlassPane {
                    // Covers the top of the pane alone, leaving points below it to the view.
                    Label(
                        text = "hint",
                        modifier =
                            SwingModifier
                                .layoutConstraint(BorderLayout.NORTH)
                                .preferredSize(Dimension(60, 20)),
                    )
                }
            }
        }

        val layer = onNodeOfType<JLayer<*>>().fetch()
        layOut(layer)
        val view = assertNotNull(layer.view, "the declared child should fill the view region")
        val pane = assertNotNull(layer.glassPane, "the declaration should install a pane of its own")
        val hint = pane.components.single()
        val x = SIZE.width / 2
        val overTheChild = hint.height / 2
        val besideTheChild = (hint.height + SIZE.height) / 2

        assertSame(
            view,
            SwingUtilities.getDeepestComponentAt(layer, x, besideTheChild),
            "a query by geometry must reach the view where the pane holds no child, where a pane " +
                "answering for every point in itself would stop it",
        )
        assertSame(
            hint,
            SwingUtilities.getDeepestComponentAt(layer, x, overTheChild),
            "and stop at the pane's own child where one lies",
        )

        assertTrue(pane.contains(x, overTheChild), "the pane answers for a point one of its children covers")
        assertFalse(pane.contains(x, besideTheChild), "and for no point beside them")

        hint.isVisible = false
        assertFalse(pane.contains(x, overTheChild), "a child that is not shown covers no point")
    }

    @Test
    fun theGlassPaneAnswersForAPointItHasItsOwnReasonToTake() = runComposeSwingTest {
        setContent {
            Layer(onPaint = { _, _, _, paintView -> paintView() }) {
                Label(text = "body", modifier = SwingModifier.view())
                GlassPane {}
            }
        }

        val layer = onNodeOfType<JLayer<*>>().fetch()
        layOut(layer)
        val pane = assertNotNull(layer.glassPane, "the declaration should install a pane of its own")
        val listener = object : MouseAdapter() {}
        val x = SIZE.width / 2
        val y = SIZE.height / 2

        assertFalse(pane.contains(x, y), "a pane holding nothing and listening for nothing answers for no point")

        // Each reason is taken away again, so that the next one is what the answer after it turns on.
        pane.addMouseListener(listener)
        assertTrue(pane.contains(x, y), "a pane carrying a mouse listener takes the point")
        pane.removeMouseListener(listener)
        assertFalse(pane.contains(x, y), "and lets it through once that listener is gone")

        pane.addMouseMotionListener(listener)
        assertTrue(pane.contains(x, y), "a mouse motion listener is a reason of its own")
        pane.removeMouseMotionListener(listener)
        assertFalse(pane.contains(x, y), "and lets the point through once it is gone")

        pane.addMouseWheelListener(listener)
        assertTrue(pane.contains(x, y), "so is a mouse wheel listener")
        pane.removeMouseWheelListener(listener)
        assertFalse(pane.contains(x, y), "and lets the point through once it is gone")

        pane.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        assertTrue(pane.contains(x, y), "so is a cursor of its own, which only the pane can show")
        assertFalse(pane.contains(x, SIZE.height + 1), "though never for a point outside its own bounds")
    }

    @Test
    fun aClickReachesTheViewThroughAPaneWithACursorAndNotThroughOneThatListens() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var viewClicks = 0
        setContent {
            // The window is realized but never shown: its peer is what dispatches a click to the component
            // under the pointer.
            Window(onCloseRequest = {}, title = WINDOW_TITLE, visible = false) {
                Layer(onPaint = { _, _, _, paintView -> paintView() }) {
                    Button(text = "view", onClick = { viewClicks++ }, modifier = SwingModifier.view())
                    GlassPane {}
                }
            }
        }

        val window = onWindowWithTitle(WINDOW_TITLE)
        val frame = window.fetch<JFrame>()
        frame.size = Dimension(320, 240)
        layOutTree(frame)
        val layer = window.onNode(isOfType<JLayer<*>>()).fetch<JLayer<*>>()
        val pane = assertNotNull(layer.glassPane, "the declaration should install a pane of its own")
        val overTheView = SwingUtilities.convertPoint(layer, layer.width / 2, layer.height / 2, frame)

        pane.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        assertSame(
            pane,
            SwingUtilities.getDeepestComponentAt(frame, overTheView.x, overTheView.y),
            "a pane with a cursor of its own is what a query by geometry finds, so the cursor shown is its own",
        )
        frame.click(overTheView)
        assertEquals(1, viewClicks, "a pane with only a cursor of its own should let the click reach the view")

        var paneClicks = 0
        pane.addMouseListener(
            object : MouseAdapter() {
                override fun mouseClicked(event: MouseEvent) {
                    paneClicks++
                }
            },
        )
        frame.click(overTheView)
        assertEquals(1, paneClicks, "a pane with a mouse listener should take the click")
        assertEquals(1, viewClicks, "and keep it from the view")
    }

    @Test
    fun anOutgoingDeclarationGivesBackTheCarriedPaneShownAsItWasShown() = runComposeSwingTest {
        var showPane by mutableStateOf(false)
        setContent {
            Layer(onPaint = { _, _, _, paintView -> paintView() }) {
                Label(text = "body", modifier = SwingModifier.view())
                if (showPane) GlassPane { Label(text = "hint") }
            }
        }

        val layer = onNodeOfType<JLayer<*>>().fetch()
        val carried = assertNotNull(layer.glassPane, "a layer builds a glass pane of its own in its constructor")
        // A layer's own pane starts hidden, and what a declaration displaces is the pane as it stands.
        carried.isVisible = true

        showPane = true
        awaitIdle()

        assertNotSame(carried, layer.glassPane, "the declared pane should displace the one the layer carried")

        showPane = false
        awaitIdle()

        assertSame(carried, layer.glassPane, "the layer should carry the pane it had before the declaration")
        assertTrue(carried.isVisible, "shown as that pane was shown, which here is shown")
    }

    @Test
    fun aLayerCarryingNoGlassPaneIsLeftCarryingNone() = runComposeSwingTest {
        var showPane by mutableStateOf(false)
        setContent {
            Layer(onPaint = { _, _, _, paintView -> paintView() }) {
                Label(text = "body", modifier = SwingModifier.view())
                if (showPane) GlassPane { Label(text = "hint") }
            }
        }

        val layer = onNodeOfType<JLayer<*>>().fetch()
        // What an outgoing declaration gives back is whatever the layer carried, which is a pane a layer
        // holds until something empties the slot - and no pane at all after that.
        layer.glassPane = null

        showPane = true
        awaitIdle()

        val declared = assertNotNull(layer.glassPane, "the declaration should install a pane of its own")
        assertTrue(
            declared.isVisible,
            "a layer hands an arriving pane the visibility of the pane it replaces, so a pane arriving " +
                "where the layer carried none is installed hidden and the declaration must show it",
        )

        showPane = false
        awaitIdle()

        assertNull(layer.glassPane, "the layer should be left carrying no pane, as it was")
    }

    @Test
    fun anOutgoingDeclarationLeavesAPaneItDidNotInstallAlone() = runComposeSwingTest {
        var showPane by mutableStateOf(true)
        setContent {
            Layer(onPaint = { _, _, _, paintView -> paintView() }) {
                Label(text = "body", modifier = SwingModifier.view())
                if (showPane) GlassPane { Label(text = "hint") }
            }
        }

        val layer = onNodeOfType<JLayer<*>>().fetch()
        // An uninstall gives the carried pane back only while the pane it installed still stands, so a
        // pane installed over it after that is left alone.
        val standing = JPanel()
        layer.glassPane = standing

        showPane = false
        awaitIdle()

        assertSame(standing, layer.glassPane, "an outgoing declaration should leave a pane it did not install")
    }

    @Test
    fun swappingTheGlassPaneExternallyDuringADeclarationLeavesNoStalePaneForALaterCycle() = runComposeSwingTest {
        var showPane by mutableStateOf(true)
        setContent {
            Layer(onPaint = { _, _, _, paintView -> paintView() }) {
                Label(text = "body", modifier = SwingModifier.view())
                if (showPane) GlassPane { Label(text = "hint") }
            }
        }

        val layer = onNodeOfType<JLayer<*>>().fetch()
        val firstDeclared = assertNotNull(layer.glassPane, "the declaration should install a pane of its own")

        // External code swaps the pane while the declaration still stands, so what the declaration would
        // otherwise give back on its way out is no longer the pane it carried when it attached.
        val swapped = JPanel()
        layer.glassPane = swapped

        showPane = false
        awaitIdle()

        // The declaration's own pane no longer stands, so its uninstall leaves the swapped-in pane alone
        // rather than restoring what it carried before it attached.
        assertSame(swapped, layer.glassPane, "an outgoing declaration should leave a pane it did not install")

        showPane = true
        awaitIdle()

        val secondDeclared = assertNotNull(layer.glassPane, "a later declaration should install a pane of its own")
        assertNotSame(firstDeclared, secondDeclared, "a fresh declaration installs a pane of its own")

        showPane = false
        awaitIdle()

        // The later declaration attached while the layer carried the swapped-in pane, so that is what its
        // own uninstall must give back - not the stale pane an earlier, already-superseded cycle carried.
        assertSame(
            swapped,
            layer.glassPane,
            "a declaration must restore the pane the layer carried when it attached, not a stale pane an " +
                "earlier declaration carried before an external swap displaced it",
        )
    }

    /** Gives [component] and everything under it the bounds a hit test reads. */
    private fun layOut(component: JComponent) {
        component.size = SIZE
        layOutTree(component)
    }

    /** Runs each container's layout, top down. */
    private fun layOutTree(container: Container) {
        container.doLayout()
        for (child in container.components) {
            if (child is Container) layOutTree(child)
        }
    }

    private companion object {
        val SIZE = Dimension(120, 80)

        const val WINDOW_TITLE = "layer glass pane clicks"
    }
}
