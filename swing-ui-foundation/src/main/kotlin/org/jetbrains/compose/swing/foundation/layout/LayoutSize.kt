package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.foundation.graphics.NoPaintOutsets
import java.awt.Dimension
import java.awt.Insets

/**
 * The preferred size less the paint outsets, or a preferred size set on the component as set: what a policy of this
 * library measures this child at.
 */
internal val ChildMeasurable.layoutPreferredSize: Dimension
    get() = layoutSize(component.preferredSize, component.isPreferredSizeSet)

/**
 * The minimum size less the paint outsets, or a minimum set as set: what a policy of this library reads as its
 * minimum.
 */
internal val ChildMeasurable.layoutMinimumSize: Dimension
    get() = layoutSize(component.minimumSize, component.isMinimumSizeSet)

/**
 * What this child prefers sized to the layout extent [width] by [height]. Swing has no query for a height at a given
 * width: a component whose height follows its width, such as a wrapping text area, answers `getPreferredSize()` for
 * the width it holds, so it is sized for the reading, as `JViewport` sizes a view that tracks its width. In its
 * container's own layout pass it keeps that size, which the placement following sets or hides it from; otherwise it is
 * given its own size back, since only a placement sets a child's bounds. No resize asks for a layout pass. Its baseline
 * is asked at that size first: a label showing HTML lays its markup out at the width `getBaseline` names.
 */
internal fun ChildMeasurable.preferredExtentSizedTo(
    width: Int,
    height: Int,
): Dimension {
    val outsets = decoratable?.decoration?.heldPaintOutsets ?: NoPaintOutsets
    val heldWidth = component.width
    val heldHeight = component.height
    var extent = Dimension()
    owner.during(RunningCause.SettledResultKept) {
        component.setSize(width.grownBy(outsets.left + outsets.right), height.grownBy(outsets.top + outsets.bottom))
        layoutBaseline(width, height)
        extent = layoutPreferredSize
        if (!owner.isValidating) component.setSize(heldWidth, heldHeight)
    }
    preferred = extent
    return extent
}

/**
 * [size], less the paint outsets [ChildMeasurable.decoratable] holds unless the size is [set] on the component: the
 * size Foundation measures and places. A side left below the outsets reads as zero. An axis at [Int.MAX_VALUE] stays
 * there.
 */
private fun ChildMeasurable.layoutSize(
    size: Dimension,
    set: Boolean,
): Dimension {
    val outsets = decoratable?.decoration?.heldPaintOutsets
    if (set || outsets == null || outsets == NoPaintOutsets) return size
    return Dimension(
        lessPaintOutsets(size.width, outsets.left + outsets.right),
        lessPaintOutsets(size.height, outsets.top + outsets.bottom),
    )
}

/**
 * This panel's border insets: the part of `getInsets()` that takes space in Foundation's layout; the paint outsets
 * do not.
 */
internal fun ConstrainedPanel.borderInsets(): Insets = border?.getBorderInsets(this) ?: NoPaintOutsets

private fun lessPaintOutsets(
    extent: Int,
    outsets: Int,
): Int = if (extent == Int.MAX_VALUE) extent else (extent - outsets).coerceAtLeast(0)

/**
 * Where this child, measured at [width] by [height], puts its baseline from the top of its layout bounds, or a
 * negative value where it puts none. `getBaseline` takes the component's Swing size and measures from the top of
 * its bounds, as `FlowLayout`, `GridBagLayout` and `GroupLayout` call it, so a decorated child is asked with its
 * paint outsets around the layout size.
 */
internal fun ChildMeasurable.layoutBaseline(
    width: Int,
    height: Int,
): Int {
    val outsets = decoratable?.decoration?.heldPaintOutsets
    if (outsets == null || outsets == NoPaintOutsets) return component.getBaseline(width, height)
    val baseline =
        component.getBaseline(width.grownBy(outsets.left + outsets.right), height.grownBy(outsets.top + outsets.bottom))
    return if (baseline < 0) baseline else baseline - outsets.top
}
