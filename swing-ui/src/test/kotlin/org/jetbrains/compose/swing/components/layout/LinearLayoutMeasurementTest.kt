package org.jetbrains.compose.swing.components.layout

import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.lang.reflect.InvocationTargetException
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A row or column measures a child once and reuses that measurement until its container is invalidated -
 * the contract [LinearLayout.invalidateLayout] exists for.
 *
 * Each case drives a realized frame, because that is the only place the measurements are held. AWT
 * carries a child's invalidation up to its container only while `isValid` reports true of it, and that
 * requires a peer. On an unrealized container the manager measures afresh every pass and holds nothing.
 */
class LinearLayoutMeasurementTest {
    @Test
    fun aPassWithNothingInvalidatedAsksTheChildrenNothing() {
        val children = List(CHILD_COUNT) { MeasuredChild(NARROW) }
        inRealizedRow(children) { row ->
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
        val child = MeasuredChild(NARROW)
        inRealizedRow(listOf(child)) { row ->
            assertEquals(NARROW.width, child.width, "the extent the child preferred when it was first measured")

            child.prefers(WIDE)
            row.invalidate()
            row.doLayout()

            assertEquals(WIDE.width, child.width, "the extent it prefers once the container has been invalidated")
        }
    }

    @Test
    fun aChildThatLeavesTakesItsMeasurementWithIt() {
        val leaving = MeasuredChild(NARROW)
        val staying = MeasuredChild(WIDE)
        inRealizedRow(listOf(leaving, staying)) { row ->
            // Lay out while the row is invalid, so the remove below does not invalidate it in turn and
            // the measurements taken here survive into the next pass.
            row.invalidate()
            row.doLayout()
            assertEquals(listOf(NARROW.width, WIDE.width), row.childWidths(), "each child at the extent it prefers")

            row.remove(leaving)
            row.doLayout()

            assertEquals(
                listOf(WIDE.width),
                row.childWidths(),
                "the child that took the removed one's place keeps its own extent",
            )
        }
    }

    private companion object {
        val NARROW = Dimension(30, 40)
        val WIDE = Dimension(70, 40)
    }
}

/**
 * Realizes a horizontal [LinearLayout] holding [children] and runs [body] against it on the event
 * dispatch thread. The frame is disposed however [body] ends.
 */
private fun inRealizedRow(
    children: List<MeasuredChild>,
    body: (JPanel) -> Unit,
) {
    assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
    onEventDispatchThread {
        val frame = JFrame()
        try {
            val row =
                JPanel(
                    LinearLayout(
                        axis = LayoutAxis.Horizontal,
                        placements = ChildPlacements(),
                        arrangement = HorizontalAxisArrangement(Arrangement.Start),
                        alignment = VerticalAxisAlignment(Alignment.Top),
                    ),
                )
            children.forEach(row::add)
            frame.contentPane.add(row)
            frame.pack()
            frame.setSize(ROW_FRAME_SIZE)
            frame.validate()

            assertTrue(row.isValid, "the row must be realized and valid for its manager to hold measurements")
            body(row)
        } finally {
            frame.dispose()
        }
    }
}

/** Large enough that the row never runs out of room for what a child prefers. */
private val ROW_FRAME_SIZE = Dimension(600, 300)

/** Runs [body] on the event dispatch thread, unwrapping a failure it raises from the invocation. */
private fun onEventDispatchThread(body: () -> Unit) {
    try {
        SwingUtilities.invokeAndWait(body)
    } catch (invocation: InvocationTargetException) {
        throw invocation.cause ?: invocation
    }
}

/** The width the row assigned each of its children, in declaration order. */
private fun JPanel.childWidths(): List<Int> = components.map { it.width }

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
