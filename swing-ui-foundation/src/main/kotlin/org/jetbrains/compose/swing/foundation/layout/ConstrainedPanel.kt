package org.jetbrains.compose.swing.foundation.layout

import java.awt.Component
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JPanel
import javax.swing.JViewport
import javax.swing.Scrollable
import javax.swing.SwingConstants

/** A panel carrying Swing's viewport-scrolling contract. */
internal open class ScrollablePanel(
    layout: MeasurePolicyLayout,
) : JPanel(layout),
    Scrollable {
    override fun isOptimizedDrawingEnabled(): Boolean = false

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize

    override fun getScrollableUnitIncrement(
        visibleRect: Rectangle,
        orientation: Int,
        direction: Int,
    ): Int = getFontMetrics(font).height

    override fun getScrollableBlockIncrement(
        visibleRect: Rectangle,
        orientation: Int,
        direction: Int,
    ): Int = if (orientation == SwingConstants.VERTICAL) visibleRect.height else visibleRect.width

    override fun getScrollableTracksViewportWidth(): Boolean = fillsViewport { it.width }

    override fun getScrollableTracksViewportHeight(): Boolean = fillsViewport { it.height }

    /** Whether a viewport is larger than this panel's preferred size on [side]'s axis. */
    private fun fillsViewport(side: (Dimension) -> Int): Boolean {
        val viewport = parent as? JViewport ?: return false
        return side(viewport.size) > side(preferredSize)
    }
}

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
    Constrainable {
    /** Declaration order adjusted by any stacking parent data understood by this container. */
    val stackingOrder: StackingOrder =
        StackingOrder(this) {
            (policyLayout.measurables.declaredBy(it) as? StackingParentData)?.zIndex ?: 0f
        }

    override fun addImpl(
        comp: Component,
        constraints: Any?,
        index: Int,
    ) {
        try {
            super.addImpl(comp, constraints, index)
        } finally {
            stackingOrder.declared(comp, index)
        }
        stackingOrder.restack()
    }

    override fun remove(index: Int) {
        val dropped = getComponent(index)
        stackingOrder.dropped(dropped)
        super.remove(index)
    }

    override fun removeAll() {
        stackingOrder.cleared()
        super.removeAll()
    }

    /** Runs [action] in composition order, before [StackingOrder] rearranges Swing's component array. */
    internal inline fun forEachChildInDeclarationOrder(action: (Component) -> Unit) {
        stackingOrder.forEachInDeclarationOrder(action)
    }

    /** The child composed first, before [StackingOrder] rearranges Swing's component array. */
    internal fun firstChildInDeclarationOrder(): Component? = stackingOrder.firstDeclaredChild()

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
