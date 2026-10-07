package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.foundation.graphics.NoPaintOutsets
import java.awt.Dimension

/**
 * Sizes this child to the layout extent [width] by [height] and returns what it prefers there, recording that as its
 * preferred extent. Swing has no query for a height at a given width: a component whose height follows its width, such
 * as a wrapping text area, returns `getPreferredSize()` for the width it holds. Where a layout pass of its container
 * follows, it keeps that size; otherwise it is not resized and returns a result at the size it holds.
 */
internal fun ChildMeasurable.preferredExtentSizedTo(
    width: Int,
    height: Int,
): Dimension =
    if (owner.isLaidOutNext) {
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
 * another
 * width, which that offer leaves bounded, it is sized to that width at the height it holds, or the height it prefers
 * where it holds none, and read there. A preference read at the width it holds costs nothing beyond the preference
 * read: it is resized
 * and read again only for another width. Where it returns another height, it is sized to that height and read again,
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
): Int {
    val width = widthForPreferences(minWidth, maxWidth)

    // A text component lays its text out at the width it holds only when its preferred size is read or it is painted,
    // and returns a result
    // its minimum from that layout, so the minimum is read after the preferred size.
    fun sizePreference(preferred: Dimension) = if (mode == MeasureMode.Minimum) layoutMinimumSize else preferred

    if (width == layoutWidth || !owner.isLaidOutNext) return sizePreference(preferredExtent()).height
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
): Int {
    if (constrainable != null) return layoutBaseline(width, height)
    val baselineWidth = widthForPreferences(width, width)
    return if (baselineWidth == layoutWidth || !owner.isLaidOutNext) {
        layoutBaseline(layoutWidth, height)
    } else {
        sizedTo(baselineWidth, height) { layoutBaseline(baselineWidth, height) }
    }
}

/**
 * Whether a layout pass lays this container out after the size read being performed: a layout pass of it, or of a
 * Foundation container holding it, is running, or it reports size preferences to a stock parent that lays it out next.
 * A container left
 * unplaced, or inside one left unplaced, lays nothing out. Neither does a size query from Swing under a Foundation
 * parent, such as that parent reading the container's preferred width: the height is computed at the preferred width,
 * which the parent does not necessarily grant. Only where a layout follows is a stock child resized for a preference
 * read of a
 * preference read. A stock child resized away and back is invalidated, so the next validation lays its children out
 * again.
 */
private val ChildMeasurables.isLaidOutNext: Boolean
    get() {
        var measurables = this
        while (true) {
            val parent = measurables.panel.decoration.parentMeasurables
            val record = parent?.find(measurables.panel)
            if (record != null && record.lastPlacement != ChildPlacement.Placed) return false
            if (measurables.isReadingSizePreferencesForFoundationParent) return false
            if (measurables.layoutState != LayoutState.Idle) return true
            if (measurables.isReadingSizePreferencesBeforeLayout) return true
            measurables = parent ?: return false
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
): Int {
    val constrainable = constrainable ?: return stockHeightAt(minWidth, maxWidth, MeasureMode.Minimum)
    return constrainedPanel?.intrinsicHeightUnder(IntrinsicSize.Min, minWidth, maxWidth)
        ?: constrainable.minIntrinsicHeight(maxWidth)
}

/**
 * The component's own max intrinsic height under an offer from [minWidth] to [maxWidth]; see
 * [unmodifiedMinIntrinsicHeight].
 */
internal fun ChildMeasurable.unmodifiedMaxIntrinsicHeight(
    minWidth: Int,
    maxWidth: Int,
): Int {
    val constrainable =
        constrainable ?: return stockHeightAt(minWidth, maxWidth, MeasureMode.Preferred)
    val panel = constrainedPanel
    return if (panel == null) {
        constrainable.maxIntrinsicHeight(maxWidth)
    } else {
        val held = preferred?.takeIf { minWidth == 0 && component.isValid }
        if (held != null && held.width == maxWidth) {
            held.height
        } else {
            panel.intrinsicHeightUnder(IntrinsicSize.Max, minWidth, maxWidth)
        }
    }
}

private val ChildMeasurable.constrainedPanel: ConstrainedPanel?
    get() = decoratable?.decoration?.childMeasurables?.panel
