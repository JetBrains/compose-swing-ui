package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.components.layout.ScrollablePanel
import java.awt.Dimension

/**
 * The panel a [MeasurePolicyLayout] lays out, and the one component in this library that answers a
 * constrained question.
 *
 * Every container built on a measure policy is one of these - [Row], [Column], [Box] and a container a
 * caller writes - which is what lets constraints cross any depth of them and stop at the first stock
 * widget or container from elsewhere.
 */
internal open class ConstrainedPanel(
    private val policyLayout: MeasurePolicyLayout,
) : ScrollablePanel(policyLayout),
    ConstrainedSize {
    private var measured: Dimension = Dimension()

    final override val constrainedWidth: Int get() = measured.width

    final override val constrainedHeight: Int get() = measured.height

    /** Placed by its own parent; see [ChildMeasurables.reshaped] for what that placement must not undo. */
    final override fun setBounds(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) = policyLayout.measurables.reshaped { super.setBounds(x, y, width, height) }

    /**
     * A size set on the component outright answers for it, as it does for `getPreferredSize()`: setting
     * one overrides what the layout manager would work out, and a constrained question is the same
     * question asked with an argument. The policy measures only where nothing has been set.
     */
    final override fun measure(constraints: Constraints) {
        measured = if (isPreferredSizeSet) preferredSize else policyLayout.measurables.measuredSize(this, constraints)
    }
}
