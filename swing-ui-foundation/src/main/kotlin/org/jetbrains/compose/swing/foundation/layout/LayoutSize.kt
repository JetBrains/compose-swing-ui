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

/**
 * Lays this child's markup out at the size it holds where its width changed [widthChanged], by asking it its baseline.
 * A label showing HTML lays its markup out at a new width only there or when painted, and a paint that lays it out
 * revalidates it where its height changes, which starts another validation. Its preferred size and its baseline answer
 * from the markup as last laid out: `getBaseline` places the text by the markup's height before it lays the markup out
 * at the width asked. So a question at a new width lays the markup out first. A new height alone never reflows markup.
 */
internal fun ChildMeasurable.layOutMarkup(widthChanged: Boolean) {
    if (widthChanged) layoutBaseline(layoutWidth, layoutHeight)
}
