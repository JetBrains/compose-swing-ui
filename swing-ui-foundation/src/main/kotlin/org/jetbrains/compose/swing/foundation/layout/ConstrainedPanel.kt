package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.graphics.Decoration
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Rectangle
import javax.accessibility.Accessible
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.JComponent
import javax.swing.JViewport
import javax.swing.Scrollable
import javax.swing.SwingConstants

/**
 * The panel a [MeasurePolicyLayout] lays out, and the one component in this library that answers a
 * constrained question.
 *
 * Every container built on a measure policy is one of these - [Row], [Column], [Box] and a container a
 * caller writes - which is what lets constraints cross any depth of them and stop at the first stock
 * widget or container from elsewhere. It also carries Swing's viewport-scrolling contract.
 *
 * It extends [JComponent] rather than [javax.swing.JPanel], so it installs no UI delegate: a `PanelUI` would paint a
 * background of its own even where the panel answers `false` to [isOpaque]. It is transparent by default and paints
 * its background itself when opaque. [JComponent] does not implement [Accessible], so [getAccessibleContext] is
 * declared here as `JPanel.AccessibleJPanel` does. Without it, a name set through the `accessibleName` modifier has
 * nowhere to land.
 */
@Suppress("TooManyFunctions") // Every function but the declaration-order accessors overrides a supertype member.
internal open class ConstrainedPanel(
    private val policyLayout: MeasurePolicyLayout,
) : JComponent(),
    Accessible,
    Scrollable,
    Constrainable,
    Decoratable {
    override var decoration: Decoration = Decoration.None

    init {
        layout = policyLayout
    }

    /** Declaration order adjusted by any stacking parent data understood by this container. */
    val stackingOrder: StackingOrder =
        StackingOrder(this) {
            (policyLayout.measurables.declaredBy(it) as? StackingParentData)?.zIndex ?: 0f
        }

    override fun contains(
        x: Int,
        y: Int,
    ): Boolean = decoration.contains(this, x, y)

    override fun isOpaque(): Boolean = super.isOpaque() && decoration.isOpaque(this)

    /**
     * A child repainting itself alone would otherwise be painted around this panel's decoration; see
     * [paintImmediately].
     */
    public override fun isPaintingOrigin(): Boolean = decoration.isDecorated

    /**
     * Paints the area grown by the decoration's outsets, which a blur or a shadow spreads a change in the area into.
     */
    override fun paintImmediately(
        x: Int,
        y: Int,
        w: Int,
        h: Int,
    ) {
        val reach = decoration.steps.outsets
        // Not the Rectangle overload, which calls back here.
        super.paintImmediately(
            x - reach.left,
            y - reach.top,
            w + reach.left + reach.right,
            h + reach.top + reach.bottom,
        )
    }

    private val paintContent: (Graphics) -> Unit = { graphics ->
        val decoration = decoration
        val outsets = decoration.heldPaintOutsets
        val width = decoration.layoutWidth(this)
        val height = decoration.layoutHeight(this)
        if (super.isOpaque() && background != null) {
            graphics.color = background
            graphics.fillRect(outsets.left, outsets.top, width, height)
        }
        border?.paintBorder(this, graphics, outsets.left, outsets.top, width, height)
        paintChildren(graphics)
    }

    override fun paint(g: Graphics) = decoration.paint(this, g, paintContent)

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

    override fun getAccessibleContext(): AccessibleContext =
        accessibleContext ?: AccessibleConstrainedPanel().also { accessibleContext = it }

    private inner class AccessibleConstrainedPanel : AccessibleJComponent() {
        override fun getAccessibleRole(): AccessibleRole = AccessibleRole.PANEL

        /**
         * The children in paint order, which is declaration order where no child declares a z-index. [StackingOrder]
         * keeps the component array topmost first, so this lists it from the end.
         */
        override fun getAccessibleChild(i: Int): Accessible? = super.getAccessibleChild(accessibleChildrenCount - 1 - i)
    }

    override fun isOptimizedDrawingEnabled(): Boolean = false

    /**
     * The one child this panel answers a scroll pane for, or `null` where it holds anything but one [Scrollable]
     * child. A pane asks only the view it holds, so a panel wrapping content answers on that content's behalf, and
     * the content scrolls by its own rows or lines as it does without the panel.
     */
    private fun content(): Scrollable? = if (componentCount == 1) getComponent(0) as? Scrollable else null

    /** The one scrollable child's answer plus this panel's insets, so a border around it is not clipped. */
    override fun getPreferredScrollableViewportSize(): Dimension {
        val content = content()?.preferredScrollableViewportSize ?: return preferredSize
        val insets = insets
        return Dimension(
            content.width.grownBy(insets.left + insets.right),
            content.height.grownBy(insets.top + insets.bottom),
        )
    }

    override fun getScrollableUnitIncrement(
        visibleRect: Rectangle,
        orientation: Int,
        direction: Int,
    ): Int = content()?.getScrollableUnitIncrement(visibleRect, orientation, direction) ?: getFontMetrics(font).height

    override fun getScrollableBlockIncrement(
        visibleRect: Rectangle,
        orientation: Int,
        direction: Int,
    ): Int =
        content()?.getScrollableBlockIncrement(visibleRect, orientation, direction)
            ?: if (orientation == SwingConstants.VERTICAL) visibleRect.height else visibleRect.width

    /**
     * Whether the panel is laid out at the viewport's width: where its content asks for that, and where the viewport
     * is wider than the panel asks to be, which is the stretch a pane gives a view that is no [Scrollable].
     */
    override fun getScrollableTracksViewportWidth(): Boolean =
        content()?.scrollableTracksViewportWidth == true || fillsViewport { it.width }

    /** Whether the panel is laid out at the viewport's height; see [getScrollableTracksViewportWidth]. */
    override fun getScrollableTracksViewportHeight(): Boolean =
        content()?.scrollableTracksViewportHeight == true || fillsViewport { it.height }
}

/** Whether this component's viewport is larger than its preferred size on [side]'s axis; `false` outside one. */
internal inline fun Component.fillsViewport(side: (Dimension) -> Int): Boolean {
    val viewport = parent as? JViewport ?: return false
    return side(viewport.size) > side(preferredSize)
}
