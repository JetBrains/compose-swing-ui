package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.interaction.onChild
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.window.Window
import org.jetbrains.compose.swing.window.WindowState
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GraphicsEnvironment
import java.awt.GridLayout
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A panel's layout parameters are composition state like any other, so the layout manager the panel
 * runs follows the values it is given rather than keeping whatever it was built with. Each test
 * changes a parameter after the first composition, reads the property back off the live layout, and
 * declares the first value again to confirm the panel keeps following. The manager is updated in
 * place: it records where each child goes, so a panel that swapped it out would lose that record.
 */
class PanelLayoutReactivityTest {
    @Test
    fun aFlowPanelFollowsItsAlignmentAndGaps() = runComposeSwingTest {
        // The opening values are all different from a FlowLayout's own defaults, so the panel the
        // first composition builds is only correct if it was built from the declaration.
        var alignment by mutableIntStateOf(FlowLayout.LEADING)
        var hgap by mutableIntStateOf(FIRST_GAP)
        var vgap by mutableIntStateOf(FIRST_GAP)
        setContent {
            FlowPanel(
                alignment = alignment,
                hgap = hgap,
                vgap = vgap,
            ) {
                Label("child")
            }
        }

        val panel = onRoot().onChild().fetch<JPanel>()
        val layout = panel.layout as FlowLayout

        assertEquals(FlowLayout.LEADING, layout.alignment, "the alignment first declared")
        assertEquals(FIRST_GAP, layout.hgap, "the hgap first declared")
        assertEquals(FIRST_GAP, layout.vgap, "the vgap first declared")

        alignment = FlowLayout.TRAILING
        hgap = SECOND_GAP
        vgap = SECOND_GAP
        awaitIdle()

        assertEquals(FlowLayout.TRAILING, layout.alignment, "alignment")
        assertEquals(SECOND_GAP, layout.hgap, "hgap")
        assertEquals(SECOND_GAP, layout.vgap, "vgap")

        // Back to a flow's own default alignment, which a panel that refused to write a default
        // would leave on the previous value.
        alignment = FlowLayout.CENTER
        awaitIdle()
        assertEquals(FlowLayout.CENTER, layout.alignment, "the default alignment declared later")

        alignment = FlowLayout.LEADING
        hgap = FIRST_GAP
        vgap = FIRST_GAP
        awaitIdle()

        assertEquals(FlowLayout.LEADING, layout.alignment, "the alignment declared again")
        assertEquals(FIRST_GAP, layout.hgap, "the hgap declared again")
        assertEquals(FIRST_GAP, layout.vgap, "the vgap declared again")
        assertSame(layout, panel.layout, "the panel should keep the layout it started with")
        assertEquals(1, panel.componentCount, "the child should stay attached")
    }

    @Test
    fun aBorderPanelFollowsItsGaps() = runComposeSwingTest {
        var hgap by mutableIntStateOf(FIRST_GAP)
        var vgap by mutableIntStateOf(FIRST_GAP)
        setContent {
            BorderPanel(hgap = hgap, vgap = vgap) {
                Label("child", SwingModifier.center())
            }
        }

        val panel = onRoot().onChild().fetch<JPanel>()
        val layout = panel.layout as BorderLayout

        // The opening gaps are not a BorderLayout's own defaults, so the layout the first composition
        // builds carries them only if it was built from the declaration.
        assertEquals(FIRST_GAP, layout.hgap, "the hgap first declared")
        assertEquals(FIRST_GAP, layout.vgap, "the vgap first declared")

        hgap = SECOND_GAP
        vgap = SECOND_GAP
        awaitIdle()

        assertEquals(SECOND_GAP, layout.hgap, "hgap")
        assertEquals(SECOND_GAP, layout.vgap, "vgap")

        hgap = FIRST_GAP
        vgap = FIRST_GAP
        awaitIdle()

        assertEquals(FIRST_GAP, layout.hgap, "the hgap declared again")
        assertEquals(FIRST_GAP, layout.vgap, "the vgap declared again")
        assertSame(layout, panel.layout, "the panel should keep the layout it started with")
        // A border layout records the region each child was added under, so the child is still
        // placed once the gaps have been edited.
        assertSame(
            panel.getComponent(0),
            layout.getLayoutComponent(BorderLayout.CENTER),
            "the center child should keep its region",
        )
    }

    @Test
    fun aBoxPanelFollowsItsAxis() = runComposeSwingTest {
        var axis by mutableIntStateOf(BoxLayout.Y_AXIS)
        setContent {
            BoxPanel(axis = axis) {
                Label("child")
            }
        }

        val panel = onRoot().onChild().fetch<JPanel>()
        assertEquals(BoxLayout.Y_AXIS, (panel.layout as BoxLayout).axis, "the declared axis")

        axis = BoxLayout.X_AXIS
        awaitIdle()

        // A BoxLayout cannot change axis, so the panel must be running a different instance, and that
        // instance must target this very panel or it refuses to lay it out.
        assertEquals(BoxLayout.X_AXIS, (panel.layout as BoxLayout).axis, "the new axis")
        assertEquals(1, panel.componentCount, "the child survives the layout swap")

        axis = BoxLayout.Y_AXIS
        awaitIdle()

        assertEquals(BoxLayout.Y_AXIS, (panel.layout as BoxLayout).axis, "the axis declared again")
        assertEquals(1, panel.componentCount, "the child survives the second layout swap")
    }

    @Test
    fun aGridPanelFollowsItsGridAndGaps() = runComposeSwingTest {
        var rows by mutableIntStateOf(2)
        var cols by mutableIntStateOf(0)
        var hgap by mutableIntStateOf(FIRST_GAP)
        var vgap by mutableIntStateOf(FIRST_GAP)
        setContent {
            GridPanel(
                rows = rows,
                cols = cols,
                hgap = hgap,
                vgap = vgap,
            ) {
                Label("child")
            }
        }

        val panel = onRoot().onChild().fetch<JPanel>()
        val layout = panel.layout as GridLayout

        // The opening grid and gaps are none of a GridLayout's own defaults, so the layout the first
        // composition builds carries them only if it was built from the declaration.
        assertEquals(2, layout.rows, "the rows first declared")
        assertEquals(0, layout.columns, "the columns first declared")
        assertEquals(FIRST_GAP, layout.hgap, "the hgap first declared")
        assertEquals(FIRST_GAP, layout.vgap, "the vgap first declared")

        // Zeroing the rows while the columns are still zero is what a grid refuses, so this also
        // pins the order the two dimensions are written in.
        rows = 0
        cols = 3
        hgap = SECOND_GAP
        vgap = SECOND_GAP
        awaitIdle()

        assertEquals(0, layout.rows, "rows")
        assertEquals(3, layout.columns, "columns")
        assertEquals(SECOND_GAP, layout.hgap, "hgap")
        assertEquals(SECOND_GAP, layout.vgap, "vgap")

        // The opposite order on the way back: the columns are zeroed while the rows are non-zero.
        rows = 2
        cols = 0
        hgap = FIRST_GAP
        vgap = FIRST_GAP
        awaitIdle()

        assertEquals(2, layout.rows, "the rows declared again")
        assertEquals(0, layout.columns, "the columns declared again")
        assertEquals(FIRST_GAP, layout.hgap, "the hgap declared again")
        assertEquals(FIRST_GAP, layout.vgap, "the vgap declared again")
        assertSame(layout, panel.layout, "the panel should keep the layout it started with")
        assertEquals(1, panel.componentCount, "the child should stay attached")
    }

    @Test
    fun aCardPanelFollowsItsGaps() = runComposeSwingTest {
        var hgap by mutableIntStateOf(FIRST_GAP)
        var vgap by mutableIntStateOf(FIRST_GAP)
        setContent {
            CardPanel(selectedCard = "only", hgap = hgap, vgap = vgap) {
                Label("child", SwingModifier.card("only"))
            }
        }

        val panel = onRoot().onChild().fetch<JPanel>()
        val layout = panel.layout as CardLayout

        // The opening gaps are not a CardLayout's own defaults, so the layout the first composition
        // builds carries them only if it was built from the declaration.
        assertEquals(FIRST_GAP, layout.hgap, "the hgap first declared")
        assertEquals(FIRST_GAP, layout.vgap, "the vgap first declared")

        hgap = SECOND_GAP
        vgap = SECOND_GAP
        awaitIdle()

        assertEquals(SECOND_GAP, layout.hgap, "hgap")
        assertEquals(SECOND_GAP, layout.vgap, "vgap")

        hgap = FIRST_GAP
        vgap = FIRST_GAP
        awaitIdle()

        assertEquals(FIRST_GAP, layout.hgap, "the hgap declared again")
        assertEquals(FIRST_GAP, layout.vgap, "the vgap declared again")
        assertSame(layout, panel.layout, "the panel should keep the layout it started with")
        // The deck a card layout flips through is its own record of the children, so the selected
        // card is still the one on top once the gaps have been edited.
        onNodeWithText("child").assertIsVisible()
    }

    @Test
    fun aChangedGridLaysTheChildrenOutAgain() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var rows by mutableIntStateOf(1)
        var cols by mutableIntStateOf(2)
        val state = WindowState(size = Dimension(320, 240))
        setContent {
            // On a realized window, nothing lays a panel out again on its own: editing the manager
            // settles the geometry it would produce, and only the relayout the write asks for turns
            // that into the bounds the children actually get.
            Window(onCloseRequest = {}, state = state, title = "grid-relayout") {
                GridPanel(rows = rows, cols = cols) {
                    Label("left")
                    Label("right")
                }
            }
        }

        val window = onWindowWithTitle("grid-relayout")
        val left = window.onNodeWithText("left").fetch<JLabel>()
        val right = window.onNodeWithText("right").fetch<JLabel>()
        assertEquals(left.y, right.y, "a single-row grid should put both children on one line")

        rows = 2
        cols = 1
        awaitIdle()

        assertTrue(
            right.y > left.y,
            "a two-row grid should stack the children: they are still at y=" +
                "${left.y} and ${right.y}, so no layout pass followed the change",
        )
    }

    private companion object {
        const val FIRST_GAP = 2
        const val SECOND_GAP = 17
    }
}
