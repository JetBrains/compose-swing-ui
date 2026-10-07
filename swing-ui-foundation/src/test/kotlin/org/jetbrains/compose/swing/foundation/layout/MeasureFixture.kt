package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Recomposer
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.function.Executable
import java.awt.Component
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JPanel
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Composes [panel] as a [SwingNode]'s component, so its layout measures, places and paints as a
 * composed [Layout]'s does. A test class calling this registers
 * [ComposedPanels], which disposes the composition after each test. Call on the event dispatch thread.
 */
internal fun composed(panel: ConstrainedPanel): ConstrainedPanel {
    val recomposer = Recomposer(EmptyCoroutineContext)
    val handle =
        JPanel().setContent(parent = recomposer) {
            SwingNode<ConstrainedPanel>(factory = { panel })
        }
    ComposedPanels.handles +=
        DisposableHandle {
            try {
                handle.dispose()
            } finally {
                recomposer.cancel()
            }
        }
    return panel
}

/** Disposes every composition [composed] made during a test. */
internal class ComposedPanels : AfterEachCallback {
    override fun afterEach(context: ExtensionContext) {
        runSwingTest {
            val pending = handles.toList()
            handles.clear()
            assertAll(pending.map { handle -> Executable { handle.dispose() } })
        }
    }

    companion object {
        val handles: MutableList<DisposableHandle> = mutableListOf()
    }
}

/** A component asking for one fixed extent and declaring no maximum of its own. */
internal class FixedSizeChild(
    private val width: Int = 0,
    private val height: Int = 0,
) : JPanel() {
    override fun getPreferredSize(): Dimension = Dimension(width, height)
}

/**
 * Where a row or a column's policy puts [children] when it is measured under [constraints] outright -
 * the entry point a caller reaches when it measures without asking the intrinsic functions first, and the
 * only way to hand a policy an extent `layoutContainer` never offers, such as an unbounded axis.
 *
 * Each child is registered under what it declares, and the bounds come back in declaration order.
 * Call on the event dispatch thread.
 */
internal fun measuredUnder(
    policy: RowColumnMeasurePolicy,
    constraints: Constraints,
    vararg children: Pair<Component, LinearConstraint?>,
): List<Rectangle> {
    val panel =
        composed(
            ConstrainedPanel(
                MeasurePolicyLayout(
                    MeasurePolicy { measurables, _ ->
                        with(policy) { PolicyMeasureScope.measure(measurables, constraints) }
                    },
                    null,
                ),
            ),
        )
    children.forEach { (child, declared) -> panel.add(child, declared) }
    panel.setSize(1000, 1000) // Large enough that the panel never decides an extent itself.
    panel.doLayout()
    return panel.childrenInDeclarationOrder().map { it.bounds }
}

/** The default Row policy for direct policy tests. */
internal fun rowPolicy(
    arrangement: Arrangement.Horizontal = Arrangement.Start,
    alignment: Alignment.Vertical = Alignment.Top,
): RowMeasurePolicy = RowMeasurePolicy(arrangement, alignment)

/** The default Column policy for direct policy tests. */
internal fun columnPolicy(
    arrangement: Arrangement.Vertical = Arrangement.Top,
    alignment: Alignment.Horizontal = Alignment.Start,
): ColumnMeasurePolicy = ColumnMeasurePolicy(arrangement, alignment)

/** A panel laid out by a Row policy, for direct Swing tests. */
internal fun rowPolicyPanel(
    arrangement: Arrangement.Horizontal = Arrangement.Start,
    alignment: Alignment.Vertical = Alignment.Top,
): ConstrainedPanel = ConstrainedPanel(MeasurePolicyLayout(rowPolicy(arrangement, alignment), LinearParentDataProtocol))

/** A panel laid out by a Column policy, for direct Swing tests. */
internal fun columnPolicyPanel(
    arrangement: Arrangement.Vertical = Arrangement.Top,
    alignment: Alignment.Horizontal = Alignment.Start,
): ConstrainedPanel =
    ConstrainedPanel(MeasurePolicyLayout(columnPolicy(arrangement, alignment), LinearParentDataProtocol))

/**
 * Runs [body] with [root] under a frame that grants it a peer - what makes a container hold between two
 * Swing calls what a pass measured - on the dispatch thread that frame lays its own content out on. The
 * frame is never shown, so nothing takes focus. Call on the event dispatch thread.
 */
internal fun peered(
    root: JComponent,
    body: () -> Unit,
) {
    assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
    val frame = JFrame()
    try {
        frame.contentPane.layout = null
        frame.contentPane.add(root)
        frame.addNotify()
        body()
    } finally {
        frame.dispose()
    }
}
