package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.graphics.Decoration
import org.jetbrains.compose.swing.foundation.graphics.DecorationSteps
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
@Suppress("TooManyFunctions") // Every non-private function overrides a Swing or Constrainable member.
internal open class ConstrainedPanel(
    val policyLayout: MeasurePolicyLayout,
) : JComponent(),
    Accessible,
    Scrollable,
    Constrainable,
    Decoratable {
    override var decoration: Decoration = Decoration.None

    init {
        layout = policyLayout
        policyLayout.measurables.panel = this
        linkTo(childMeasurables = policyLayout.measurables)
    }

    /** Declaration order, and the component array sorted by the z-index each child was last placed with. */
    val stackingOrder: StackingOrder = StackingOrder(this, policyLayout.measurables)

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
     * Paints the whole panel while a layer rotates or scales it, since a repaint a descendant asks for names the area
     * it would take unturned and unscaled. Otherwise, it paints the area grown by the decoration's outsets, which a
     * blur or a shadow spreads a change in the area into.
     */
    override fun paintImmediately(
        x: Int,
        y: Int,
        w: Int,
        h: Int,
    ) {
        val steps = decoration.steps
        if (steps.isTransformed) return super.paintImmediately(0, 0, width, height)
        val reach = steps.outsets
        // Not the Rectangle overload, which calls back here.
        super.paintImmediately(
            x - reach.left,
            y - reach.top,
            w + reach.left + reach.right,
            h + reach.top + reach.bottom,
        )
    }

    /** What [paintBlock] paints into, held only while [paint] runs. */
    private var paintGraphics: Graphics? = null

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

    // Stored once and reading its graphics from a field, so observing a paint allocates nothing per call.
    private val paintBlock: () -> Unit = {
        decoration.paint(this, checkNotNull(paintGraphics), paintContent)
    }

    override fun paint(g: Graphics) {
        paintGraphics = g
        try {
            policyLayout.observe(PaintReads, paintBlock)
        } finally {
            paintGraphics = null
        }
    }

    /**
     * A child's paint is observed by the child itself, so a paint that skips a child drops none of its
     * reads, and a plain component reading state while it paints is not observed.
     */
    override fun paintChildren(g: Graphics) = Snapshot.withoutReadObservation { super.paintChildren(g) }

    /**
     * Validates this panel and its descendants inside one snapshot that observes no read. Each answer a policy
     * computes during the validation observes its reads through that snapshot instead of entering one of its own, and
     * a read made outside an answer is not observed.
     */
    override fun validateTree() = Snapshot.withoutReadObservation { super.validateTree() }

    override fun addImpl(
        comp: Component,
        constraints: Any?,
        index: Int,
    ) {
        // In step, the add itself stacks the child where a restack would.
        val inStep = stackingOrder.isInStep
        try {
            super.addImpl(comp, constraints, stackingOrder.stackedIndex(comp, index))
        } finally {
            stackingOrder.declared(comp, index)
        }
        if (!inStep) stackingOrder.restack()
    }

    override fun remove(index: Int) {
        stackingOrder.dropped(getComponent(index))
        super.remove(index)
    }

    override fun removeAll() {
        stackingOrder.cleared()
        super.removeAll()
    }

    /**
     * A child moved by a placement replay reports an invalidation that replay has already answered, and so
     * does the [StackingOrder.restack] it ends with: `setComponentZOrder` invalidates this panel, and a
     * change of z-order needs no layout.
     */
    override fun invalidate() {
        if (policyLayout.measurables.isPlacingAgain) return
        super.invalidate()
    }

    private var measured: Dimension = Dimension()

    final override val constrainedWidth: Int get() = measured.width

    final override val constrainedHeight: Int get() = measured.height

    /** Placed by its own parent; see [ChildMeasurables.during] for what that placement must not undo. */
    final override fun setBounds(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) = policyLayout.measurables.during(RunningCause.ParentPlacement) { super.setBounds(x, y, width, height) }

    /**
     * A size set on the component outright answers for it, as it does for `getPreferredSize()`: setting
     * one overrides what the layout manager would work out, and a constrained question is the same
     * question asked with an argument. The policy measures only where nothing has been set.
     */
    final override fun measure(constraints: Constraints) {
        measured =
            if (isPreferredSizeSet) {
                super.getPreferredSize()
            } else {
                policyLayout.measurables.measuredSize(constraints)
            }
    }

    /**
     * The lines of the last [measure], moved by the insets it measured inside, worked out on each read; see
     * [ChildMeasurables.alignmentLinesOf]. None where a set preferred size answered it. A Foundation parent reads
     * them on its first read of a line after a measure, holds them with what it measured, and reads them again once
     * this panel places its children again.
     */
    final override val alignmentLines: Map<AlignmentLine, Int>
        get() {
            val result = policyLayout.measurables.measured
            if (isPreferredSizeSet || result == null) return emptyMap()
            val insets = getInsets()
            return policyLayout.measurables.alignmentLinesOf(
                result,
                insets.left,
                insets.top,
                componentOrientation.isLeftToRight,
            )
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

/**
 * This value with [steps], [hasOpaqueSteps] and the links; this value itself where every field is unchanged, and the
 * shared [Decoration.None] where it has no steps and no links.
 */
internal fun Decoration.fitted(
    steps: DecorationSteps = this.steps,
    hasOpaqueSteps: Boolean = this.hasOpaqueSteps,
    parentMeasurables: ChildMeasurables? = this.parentMeasurables,
    childMeasurables: ChildMeasurables? = this.childMeasurables,
): Decoration =
    when {
        holds(steps, hasOpaqueSteps, parentMeasurables, childMeasurables) -> this
        Decoration.None.holds(steps, hasOpaqueSteps, parentMeasurables, childMeasurables) -> Decoration.None
        else -> Decoration(steps, heldPaintOutsets, parentMeasurables, childMeasurables, hasOpaqueSteps)
    }

/** Whether this value holds [steps], [hasOpaqueSteps] and the links [parentMeasurables] and [childMeasurables]. */
private fun Decoration.holds(
    steps: DecorationSteps,
    hasOpaqueSteps: Boolean,
    parentMeasurables: ChildMeasurables?,
    childMeasurables: ChildMeasurables?,
): Boolean =
    steps == this.steps &&
        hasOpaqueSteps == this.hasOpaqueSteps &&
        parentMeasurables === this.parentMeasurables &&
        childMeasurables === this.childMeasurables

/**
 * Writes this component's decoration with [parentMeasurables] and [childMeasurables] as its links, where they
 * differ.
 */
internal fun Decoratable.linkTo(
    parentMeasurables: ChildMeasurables? = decoration.parentMeasurables,
    childMeasurables: ChildMeasurables? = decoration.childMeasurables,
) {
    val held = decoration
    if (parentMeasurables === held.parentMeasurables && childMeasurables === held.childMeasurables) return
    decoration =
        Decoration(
            held.steps,
            held.heldPaintOutsets,
            parentMeasurables,
            childMeasurables,
            held.hasOpaqueSteps,
        )
}

/** Reads behind the container's own pixels; its children's paint is observed by each child. */
private val PaintReads: (LayoutObservationNode) -> Unit = { it.component.repaint() }
