package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.components.layout.ScrollablePanel
import java.awt.Component
import java.awt.ComponentOrientation
import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager2
import java.awt.Rectangle
import java.util.IdentityHashMap

/**
 * The layout manager behind [Box]: it stacks every visible child in the same place, each at the extent it
 * prefers, capped at the container's, and sitting where the alignment it declares puts it, or where
 * [alignment] puts it when it declares none. Which of the stacked children is on top is [OverlapPanel]'s
 * to arrange; this manager only tells it that a child's constraint has been written again.
 *
 * A child that declared a match of its container's extent is offered the whole inner rectangle in place
 * of the extent it prefers, and is passed over while the container's own extent is worked out - so it
 * takes the size the other children settle rather than adding to it.
 *
 * [alignment] is the value the current composition declares; the container that owns this manager
 * writes it as it changes.
 *
 * One manager lays out the one container it belongs to: a box builds one alongside the panel it creates.
 */
internal class OverlapLayout(
    var alignment: Alignment,
) : LayoutManager2 {
    /** The extents measured for this container's children, see [PreferredSizeCache]. */
    private val measured = PreferredSizeCache()

    /**
     * What each child was registered under. A child added under nothing carries none, and is laid out at
     * the size it prefers, where the container's own alignment puts it.
     */
    internal val declared = IdentityHashMap<Component, BoxConstraint>()

    /**
     * Takes what [component] was registered under, and refuses a constraint of any other kind the way
     * `BorderLayout` and `GridBagLayout` refuse one they cannot read.
     */
    override fun addLayoutComponent(
        component: Component,
        constraints: Any?,
    ) {
        when (constraints) {
            null -> declared.remove(component)
            is BoxConstraint -> declared[component] = constraints
            else -> throw IllegalArgumentException(foreignConstraint(component, constraints))
        }
        // A child registered again is one whose modifier ran again, which is how a changed zIndex
        // arrives: the component keeps its parent, and only what it is registered under changes. The
        // removal that precedes such a write has given up the value to compare against, so the panel
        // stacks its children again either way; the reordering is what is held to whatever moved.
        (component.parent as? OverlapPanel)?.stackingOrder?.restack()
    }

    override fun addLayoutComponent(
        name: String?,
        component: Component,
    ): Unit = Unit

    /** Gives up what [component] was registered under, and the extent measured for it. */
    override fun removeLayoutComponent(component: Component) {
        measured.forget(component)
        declared.remove(component)
    }

    override fun invalidateLayout(target: Container): Unit = measured.invalidate()

    override fun preferredLayoutSize(parent: Container): Dimension = combinedSize(parent, ::preferredSizeOf)

    /**
     * Minimum extents are read fresh every time. The cache holds preferred extents only, and a container
     * is asked for its minimum once per validate rather than once per pass.
     */
    override fun minimumLayoutSize(parent: Container): Dimension = combinedSize(parent) { it.minimumSize }

    /** The extent [child] prefers, measured once and reused until the next invalidation. */
    internal fun preferredSizeOf(child: Component): Dimension = measured.preferredSizeOf(child)

    /** A box takes any extent it is offered and places its children inside it. */
    override fun maximumLayoutSize(target: Container): Dimension = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)

    /**
     * What the visible child on top of the stack reports - the front of the component array, see
     * [StackingOrder]; see [firstVisibleChildAlignment].
     */
    override fun getLayoutAlignmentX(target: Container): Float = firstVisibleChildAlignment(target) { it.alignmentX }

    /** What the visible child on top of the stack reports; see [getLayoutAlignmentX]. */
    override fun getLayoutAlignmentY(target: Container): Float = firstVisibleChildAlignment(target) { it.alignmentY }

    override fun layoutContainer(parent: Container) {
        val insets = parent.insets
        val inner =
            Rectangle(
                insets.left,
                insets.top,
                (parent.width - insets.left - insets.right).coerceAtLeast(0),
                (parent.height - insets.top - insets.bottom).coerceAtLeast(0),
            )
        val orientation = parent.componentOrientation
        for (index in 0 until parent.componentCount) {
            val child = parent.getComponent(index)
            if (child.isVisible) place(child, inner, orientation)
        }
    }
}

/**
 * Puts [child] in [inner] - the container's rectangle inside its insets: at the extent it prefers, capped
 * at the container's and at an explicit `maximumSize`, where its own alignment or the container's puts it.
 * A child is never asked what it prefers along an axis it fills, or along either where it matches the
 * container's extent; it is offered the whole of [inner] there, and its alignment places it in whatever
 * an explicit `maximumSize` leaves free.
 */
private fun OverlapLayout.place(
    child: Component,
    inner: Rectangle,
    orientation: ComponentOrientation,
) {
    val constraint = declared[child]
    val matches = constraint?.matchesParentSize == true
    val fillsWidth = matches || constraint?.fillsWidth == true
    val fillsHeight = matches || constraint?.fillsHeight == true
    // A child that fills both axes is never asked what it prefers; neither extent below would read it.
    val preferred = if (fillsWidth && fillsHeight) inner.size else preferredSizeOf(child)
    val maximum = if (child.isMaximumSizeSet) child.maximumSize else null
    val width = extent(if (fillsWidth) inner.width else preferred.width, inner.width, maximum?.width)
    val height = extent(if (fillsHeight) inner.height else preferred.height, inner.height, maximum?.height)
    val place = (constraint?.alignment ?: alignment).align(Dimension(width, height), inner.size, orientation)
    child.setBounds(inner.x + place.x, inner.y + place.y, width, height)
}

/** Where in the stack [child] declared it sits, and `0f` where it declared nothing. */
private fun OverlapLayout.zIndexOf(child: Component): Float = declared[child]?.zIndex ?: 0f

/** The message refusing a constraint a box cannot read. */
private fun foreignConstraint(
    child: Component,
    constraint: Any,
): String =
    "A Box places a child by the alignment it is declared with, and by align() / matchParentSize() / " +
        "fillWidth() / fillHeight() / " +
        "zIndex() on the child's own modifier, so '$child' can carry no layout constraint, but it was " +
        "added under '$constraint'."

/**
 * The extent a child occupies along one axis: as much of the [available] extent as it [requested], and
 * no more than an explicit `maximumSize` where the child declares one.
 */
private fun extent(
    requested: Int,
    available: Int,
    ceiling: Int?,
): Int = minOf(requested, available, ceiling ?: requested)

/**
 * The extent the container asks for: the largest extent along either axis among the children that do not
 * match the container's own, plus the container's insets. A container whose children all match it asks
 * for its insets alone.
 */
private fun OverlapLayout.combinedSize(
    parent: Container,
    extentOf: (Component) -> Dimension,
): Dimension {
    var width = 0
    var height = 0
    // Read straight off the container: this walk needs no index of its own, so it holds nothing between
    // calls and stays usable while a layout pass is in flight.
    for (index in 0 until parent.componentCount) {
        val child = parent.getComponent(index)
        if (!child.isVisible || declared[child]?.matchesParentSize == true) continue
        val extent = extentOf(child)
        width = maxOf(width, extent.width)
        height = maxOf(height, extent.height)
    }
    val insets = parent.insets
    return Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom)
}

/**
 * The panel behind a [Box]. `JComponent.isOptimizedDrawingEnabled` asks whether a component tiles its
 * children - whether it can promise that they never overlap - and this one cannot, so it answers false
 * the way `JLayeredPane` does. Painting one child then repaints whatever it overlaps, in place of the
 * shortcut the promise would allow.
 *
 * The order its children are declared in, and the component array derived from it, are [stackingOrder]'s.
 */
internal class OverlapPanel(
    private val overlap: OverlapLayout,
) : ScrollablePanel(overlap) {
    /** Where each child stands among its siblings; a child declares that with a zIndex. */
    val stackingOrder: StackingOrder = StackingOrder(this) { overlap.zIndexOf(it) }

    override fun isOptimizedDrawingEnabled(): Boolean = false

    /**
     * Takes [index] as a place in declaration order, records the arriving child there, and stacks the
     * children again.
     *
     * Wherever the container puts the child in the component array, [StackingOrder.restack] gives it its
     * place there, because where in that array a child belongs is the whole stack's business rather than
     * this one child's.
     */
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
}
