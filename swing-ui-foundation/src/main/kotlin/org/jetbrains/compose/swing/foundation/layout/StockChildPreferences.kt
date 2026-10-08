package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.foundation.graphics.NoPaintOutsets
import java.awt.Dimension

/** What the component itself answers under [constraints], with no chain between. */
internal fun ChildMeasurable.measureUnmodified(
    constraints: Constraints,
    maySizeStockChildren: Boolean = false,
): ChildPlaceable {
    if (constrainable != null) {
        val panel = decoratable?.decoration?.childMeasurables?.panel
        if (panel == null) {
            constrainable.measure(constraints)
        } else {
            panel.measureWithStockChildSizing(constraints, maySizeStockChildren)
        }
        return ChildPlaceable(
            this,
            constrainable.constrainedWidth,
            constrainable.constrainedHeight,
            constraints,
            if (decoratable?.decoration?.childMeasurables == null) constrainable.alignmentLines else emptyMap(),
        )
    }
    // Swing has no constrained-measure operation for an ordinary component.  When both axes
    // are exact, retain the established no-query fast path and use the granted extent. For every
    // other offer, its preferred size is the only answer Swing gives us, so constrain it here.
    // Only a Constrainable or layout modifier can deliberately report real overflow.
    val fixed = constraints.hasFixedWidth && constraints.hasFixedHeight
    val width: Int
    val height: Int
    if (fixed) {
        width = constraints.minWidth
        height = constraints.minHeight
    } else {
        var extent =
            if (constraints.hasFixedWidth && constraints.minWidth > 0 && constraints.minWidth != layoutWidth) {
                preferredExtentSizedTo(constraints.minWidth, constraints.maxHeight, maySizeStockChildren)
            } else {
                preferredExtent()
            }
        width = constraints.constrainWidth(extent.width)
        if (!constraints.hasFixedHeight && width != layoutWidth) {
            extent = preferredExtentSizedTo(width, constraints.constrainHeight(extent.height), maySizeStockChildren)
        }
        height = constraints.constrainHeight(extent.height)
    }
    return ChildPlaceable(this, width, height, constraints, emptyMap())
}

/**
 * Sizes this child to the layout extent [width] by [height] and returns what it prefers there, recording that as its
 * preferred extent. Swing has no query for a height at a given width: a component whose height follows its width, such
 * as a wrapping text area, returns `getPreferredSize()` for the width it holds. Where a layout pass of its container
 * follows, it keeps that size; otherwise it is not resized and returns a result at the size it holds.
 */
internal fun ChildMeasurable.preferredExtentSizedTo(
    width: Int,
    height: Int,
    maySizeStockChildren: Boolean = false,
): Dimension =
    if (maySizeStockChildren) {
        sizedTo(width, height) { layoutPreferredSize }.also { preferred = it }
    } else {
        preferredExtent()
    }

/**
 * What [read] returns with this child sized to the layout extent [width] by [height], its markup laid out at that width
 * first. The child keeps that size, which the layout pass that follows overwrites. No resize requests a layout pass.
 */
private inline fun <T> ChildMeasurable.sizedTo(
    width: Int,
    height: Int,
    read: () -> T,
): T {
    val outsets = decoratable?.decoration?.heldPaintOutsets ?: NoPaintOutsets
    val previousWidth = layoutWidth
    return owner.during(RunningCause.ParentPlacement) {
        component.setSize(width.grownBy(outsets.left + outsets.right), height.grownBy(outsets.top + outsets.bottom))
        layOutMarkupIfWidthChanged(previousWidth)
        read()
    }
}

/**
 * The width used to read this stock child's preferences, under an offer from [minWidth] to [maxWidth]: the width it
 * holds where the offer is unbounded, a fixed offer's width, and otherwise the width it prefers, held to the offer, as
 * androidx reads a leaf at the width its own layout takes.
 */
private fun ChildMeasurable.widthForPreferences(
    minWidth: Int,
    maxWidth: Int,
): Int =
    when {
        maxWidth == Constraints.Infinity -> layoutWidth
        minWidth == maxWidth -> maxWidth
        else -> preferredExtent().width.coerceIn(minWidth, maxWidth)
    }

/**
 * This stock child's preferred height, or its minimum height where [mode] is [MeasureMode.Minimum], under an offer of a
 * layout width from [minWidth] to [maxWidth], at its [widthForPreferences]. As `BorderLayout` sizes a north or south
 * child to
 * its width before reading its preferred height, where a layout pass lays its container out next and the child holds
 * another width, which that offer leaves bounded, it is sized to that width at the height it holds, or the height it
 * prefers where it holds none, and read there. At a positive held width, a permitted read of an invalid child asks for
 * the baseline before the preferred height so newly installed markup is laid out. The child is resized only for another
 * width. Where it returns another height, it is sized to that height and read again,
 * so the placement that follows does not resize it. Such a resize drops the preferred size Swing holds for it. A
 * measure that runs before a descendant whose height follows its width reflows on its own, as an animation frame does,
 * would then read the height for the descendant's new width and lay it out a second time. The child keeps that size but
 * is not laid out at it: only that pass lays it out, at a width that may differ. Otherwise it returns a result at the
 * size it
 * holds, as `getPreferredSize()` does.
 */
private fun ChildMeasurable.stockHeightAt(
    minWidth: Int,
    maxWidth: Int,
    mode: MeasureMode,
    maySizeStockChildren: Boolean,
): Int {
    val width = widthForPreferences(minWidth, maxWidth)

    // A text component lays its text out at the width it holds only when its preferred size is read or it is painted,
    // and returns a result
    // its minimum from that layout, so the minimum is read after the preferred size.
    fun sizePreference(preferred: Dimension) = if (mode == MeasureMode.Minimum) layoutMinimumSize else preferred

    val refreshHeldWidth = width > 0 && width == layoutWidth && maySizeStockChildren && !component.isValid
    if (refreshHeldWidth) layoutBaseline(width, layoutHeight)
    if (width == layoutWidth || !maySizeStockChildren) {
        val preferred =
            if (refreshHeldWidth) {
                layoutPreferredSize.also { this.preferred = it }
            } else {
                preferredExtent()
            }
        return sizePreference(preferred).height
    }
    val height = if (layoutHeight > 0) layoutHeight else preferredExtent().height
    val preferred = sizedTo(width, height) { layoutPreferredSize }
    val kept = if (preferred.height == height) preferred else sizedTo(width, preferred.height) { layoutPreferredSize }
    this.preferred = kept
    return sizePreference(kept).height
}

/**
 * Where this child, read at the layout extent [width] by [height], puts its baseline from the top of its layout
 * bounds, or a negative value where it puts none. A [Constrainable] returns a result at that extent. A stock child is
 * read at
 * the [widthForPreferences] of an offer of [width]. Where [stockHeightAt] would size it, it is sized to that extent and
 * its
 * markup laid out there before the preference read. Otherwise it returns a result at the width it holds, where its
 * markup is laid out.
 */
internal fun ChildMeasurable.baselineAt(
    width: Int,
    height: Int,
    maySizeStockChildren: Boolean = false,
): Int {
    if (constrainable != null) return layoutBaseline(width, height)
    val baselineWidth = widthForPreferences(width, width)
    return if (baselineWidth == layoutWidth || !maySizeStockChildren) {
        layoutBaseline(layoutWidth, height)
    } else {
        sizedTo(baselineWidth, height) { layoutBaseline(baselineWidth, height) }
    }
}

internal fun ChildMeasurable.unmodifiedIntrinsicHeight(
    intrinsicSize: IntrinsicSize,
    minWidth: Int,
    maxWidth: Int,
    maySizeStockChildren: Boolean = false,
): Int {
    val permitted = maySizeStockChildren && lastPlacement == ChildPlacement.Placed
    return if (intrinsicSize == IntrinsicSize.Min) {
        unmodifiedMinIntrinsicHeight(minWidth, maxWidth, permitted)
    } else {
        unmodifiedMaxIntrinsicHeight(minWidth, maxWidth, permitted)
    }
}

/**
 * The component's own min intrinsic width at [height], with no layout modifier between: a [Constrainable] returns a
 * result for
 * [height], and any other component with its minimum width. A Foundation container's minimum size is its min intrinsic
 * width at an unbounded height and its min intrinsic height at that width, and its preferred size the same of the max
 * functions, which Swing holds until the container is invalidated: the intrinsic width reads at an unbounded height
 * read
 * them.
 */
internal fun ChildMeasurable.unmodifiedMinIntrinsicWidth(height: Int): Int {
    val constrainable = constrainable
    if (constrainable == null || (height == Constraints.Infinity && constrainedPanel != null)) {
        return layoutMinimumSize.width
    }
    return constrainable.minIntrinsicWidth(height)
}

/** The component's own max intrinsic width at [height]; see [unmodifiedMinIntrinsicWidth]. */
internal fun ChildMeasurable.unmodifiedMaxIntrinsicWidth(height: Int): Int {
    val constrainable = constrainable
    if (constrainable == null || (height == Constraints.Infinity && constrainedPanel != null)) {
        return preferredExtent().width
    }
    return constrainable.maxIntrinsicWidth(height)
}

/**
 * The component's own min intrinsic height under an offer from [minWidth] to [maxWidth]: a Foundation container runs
 * its policy under that offer, any other [Constrainable] returns a result at [maxWidth]; see
 * [unmodifiedMinIntrinsicWidth] and
 * [stockHeightAt].
 */
internal fun ChildMeasurable.unmodifiedMinIntrinsicHeight(
    minWidth: Int,
    maxWidth: Int,
    maySizeStockChildren: Boolean = false,
): Int {
    val constrainable =
        constrainable ?: return stockHeightAt(minWidth, maxWidth, MeasureMode.Minimum, maySizeStockChildren)
    return constrainedPanel?.intrinsicHeightUnder(IntrinsicSize.Min, minWidth, maxWidth, maySizeStockChildren)
        ?: constrainable.minIntrinsicHeight(maxWidth)
}

/**
 * The component's own max intrinsic height under an offer from [minWidth] to [maxWidth]; see
 * [unmodifiedMinIntrinsicHeight].
 */
internal fun ChildMeasurable.unmodifiedMaxIntrinsicHeight(
    minWidth: Int,
    maxWidth: Int,
    maySizeStockChildren: Boolean = false,
): Int {
    val constrainable =
        constrainable ?: return stockHeightAt(minWidth, maxWidth, MeasureMode.Preferred, maySizeStockChildren)
    val panel = constrainedPanel
    return if (panel == null) {
        constrainable.maxIntrinsicHeight(maxWidth)
    } else {
        val held = preferred?.takeIf { minWidth == 0 && component.isValid }
        if (held != null && held.width == maxWidth) {
            held.height
        } else {
            panel.intrinsicHeightUnder(IntrinsicSize.Max, minWidth, maxWidth, maySizeStockChildren)
        }
    }
}

private val ChildMeasurable.constrainedPanel: ConstrainedPanel?
    get() = decoratable?.decoration?.childMeasurables?.panel
