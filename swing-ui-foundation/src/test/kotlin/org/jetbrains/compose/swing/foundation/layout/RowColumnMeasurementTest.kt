package org.jetbrains.compose.swing.foundation.layout

import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.extension.ExtendWith
import java.awt.Component
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A row or column measures a child once and reuses that measurement until its container is invalidated -
 * the generic policy manager's invalidation contract exists for.
 *
 * Each case drives a realized frame, because that is the only place the measurements are held. AWT
 * carries a child's invalidation up to its container only while `isValid` reports true of it, and that
 * requires a peer. On an unrealized container the manager measures afresh every pass and holds nothing.
 */
@ExtendWith(ComposedPanels::class)
class RowColumnMeasurementTest {
    @Test
    fun aPassWithNothingInvalidatedAsksTheChildrenNothing() {
        val children = List(CHILD_COUNT) { MeasuredChild(Dimension(30, 40)) }
        inRealized(rowPolicyPanel(), children) { row ->
            children.forEach { it.forgetMeasurements() }
            row.doLayout()

            assertEquals(
                List(CHILD_COUNT) { 0 },
                children.map { it.measurements },
                "a second pass with nothing invalidated between the two re-measures nobody",
            )
        }
    }

    @Test
    fun aPassAfterAnInvalidationPlacesTheChildAtWhatItNowPrefers() {
        val child = MeasuredChild(Dimension(30, 40))
        inRealized(rowPolicyPanel(), listOf(child)) { row ->
            assertEquals(30, child.width, "the extent the child preferred when it was first measured")

            child.prefers(Dimension(70, 40))
            row.invalidate()
            row.doLayout()

            assertEquals(70, child.width, "the extent it prefers once the container has been invalidated")
        }
    }

    @Test
    fun aChildThatLeavesTakesItsMeasurementWithIt() {
        val leaving = MeasuredChild(Dimension(30, 40))
        val staying = MeasuredChild(Dimension(70, 40))
        inRealized(rowPolicyPanel(), listOf(leaving, staying)) { row ->
            // Lay out while the row is invalid, so the remove below does not invalidate it in turn and
            // the measurements taken here survive into the next pass.
            row.invalidate()
            row.doLayout()
            assertEquals(listOf(30, 70), row.childWidths(), "each child at the extent it prefers")

            row.remove(leaving)
            row.doLayout()

            assertEquals(
                listOf(70),
                row.childWidths(),
                "the child that took the removed one's place keeps its own extent",
            )
        }
    }

    /**
     * A nested Foundation container that changes its size and revalidates itself is measured again by a column
     * that was already invalid, which Swing's own invalidation leaves alone, and the column lays it out at that size.
     */
    @Test
    fun aNestedContainerThatChangesSizeIsMeasuredAgainByAColumnAlreadyInvalid() {
        val nested = NestedContainer(Dimension(30, 40))
        val sibling = MeasuredChild(Dimension(30, 40))
        inRealized(columnPolicyPanel(), listOf(nested.panel, sibling)) { column ->
            column.invalidate()
            assertEquals(80, column.preferredSize.height, "the column asks for both children stacked")

            nested.extent = Dimension(30, 60)
            nested.panel.revalidate()

            assertEquals(100, column.preferredSize.height, "the column asks for the nested container's new height")
            SwingUtilities.getWindowAncestor(column).validate()
            assertEquals(listOf(60, 40), column.childrenInDeclarationOrder().map { it.height })
            assertEquals(60, sibling.y, "the sibling moves below the nested container's new height")
        }
    }

    /** A nested Foundation container that changes its size leaves what its siblings prefer measured. */
    @Test
    fun aNestedContainerThatChangesSizeLeavesItsSiblingMeasured() {
        val nested = NestedContainer(Dimension(30, 40))
        val sibling = MeasuredChild(Dimension(30, 40))
        inRealized(columnPolicyPanel(), listOf(nested.panel, sibling)) { column ->
            column.invalidate()
            column.preferredSize
            sibling.forgetMeasurements()

            nested.extent = Dimension(30, 60)
            nested.panel.revalidate()
            column.preferredSize

            assertEquals(0, sibling.measurements, "only the nested container's change is measured again")
        }
    }

    /**
     * A sibling that changes what it prefers and revalidates itself while the column is still invalid from a
     * nested container's revalidation is measured again too, as Swing measures an invalid component again.
     */
    @Test
    fun aSiblingChangedAfterANestedContainerRevalidatesIsMeasuredAgain() {
        val nested = NestedContainer(Dimension(30, 40))
        val sibling = MeasuredChild(Dimension(30, 40))
        inRealized(columnPolicyPanel(), listOf(nested.panel, sibling)) { column ->
            column.invalidate()
            assertEquals(80, column.preferredSize.height, "the column asks for both children stacked")

            nested.extent = Dimension(30, 60)
            nested.panel.revalidate()

            sibling.prefers(Dimension(30, 70))
            sibling.revalidate()

            SwingUtilities.getWindowAncestor(column).validate()

            assertEquals(
                listOf(60, 70),
                column.childrenInDeclarationOrder().map { it.height },
                "the sibling's own change must be measured again even though the column was already invalid",
            )
        }
    }
}

/**
 * Realizes [container] holding [children] and runs [body] against it on the event dispatch thread. The frame is
 * disposed however [body] ends.
 */
private fun inRealized(
    container: ConstrainedPanel,
    children: List<Component>,
    body: (ConstrainedPanel) -> Unit,
) {
    assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
    onEventDispatchThread {
        val frame = JFrame()
        try {
            val panel = composed(container)
            children.forEach(panel::add)
            frame.contentPane.add(panel)
            // Large enough that the container never runs out of space for what a child prefers. Sized before the
            // frame is realized: the window system reports each resize of a realized frame back later, so a size
            // set before another one could land after it and shrink the frame under the test.
            frame.setSize(600, 300)
            frame.addNotify()
            frame.validate()

            assertTrue(panel.isValid, "the container must be realized and valid for its manager to hold measurements")
            body(panel)
        } finally {
            frame.dispose()
        }
    }
}

/** A composed Foundation container whose policy asks for [extent], read on every measure. */
private class NestedContainer(
    var extent: Dimension,
) {
    val panel: ConstrainedPanel =
        composed(ConstrainedPanel(MeasurePolicyLayout({ _, _ -> layout(extent.width, extent.height) {} }, null)))
}

/** The width the row assigned each of its children, in declaration order. */
private fun ConstrainedPanel.childWidths(): List<Int> = childrenInDeclarationOrder().map { it.width }

/** A raw component counting the times its container asked for the extent it prefers. */
private class MeasuredChild(
    private var extent: Dimension,
) : JPanel() {
    var measurements: Int = 0
        private set

    fun forgetMeasurements() {
        measurements = 0
    }

    /** Changes what this child asks for without invalidating anything - each case drives that itself. */
    fun prefers(extent: Dimension) {
        this.extent = extent
    }

    override fun getPreferredSize(): Dimension {
        measurements++
        return Dimension(extent)
    }
}
