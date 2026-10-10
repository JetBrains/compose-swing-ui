package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.foundation.graphics.NoPaintOutsets
import java.awt.Dimension

/**
 * Sizes this child to the layout extent [width] by [height] and returns what it prefers there, recording that as its
 * preferred extent. Swing has no query for a height at a given width: a component whose height follows its width, such
 * as a wrapping text area, answers `getPreferredSize()` for the width it holds. Where a layout pass of its container
 * follows, it keeps that size; otherwise it is not resized and answers at the size it holds.
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
 * What [read] answers with this child sized to the layout extent [width] by [height], its markup laid out at that width
 * first. The child keeps that size, which the layout pass that follows overwrites. No resize asks for a layout pass.
 */
private inline fun <T> ChildMeasurable.sizedTo(
    width: Int,
    height: Int,
    read: () -> T,
): T {
    val outsets = decoratable?.decoration?.heldPaintOutsets ?: NoPaintOutsets
    val widthChanged = width != layoutWidth
    return owner.during(RunningCause.SettledResultKept) {
        component.setSize(width.grownBy(outsets.left + outsets.right), height.grownBy(outsets.top + outsets.bottom))
        layOutMarkup(widthChanged)
        read()
    }
}

/**
 * The width a question is asked of this stock child at, under an offer from [minWidth] to [maxWidth]: the width it
 * holds where the offer is unbounded, a fixed offer's width, and otherwise the width it prefers, held to the offer, as
 * androidx asks a leaf at the width its own layout takes.
 */
private fun ChildMeasurable.questionWidth(
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
 * layout width from [minWidth] to [maxWidth], at its [questionWidth]. As `BorderLayout` sizes a north or south child to
 * its width before asking its height, where a layout pass lays its container out next and the child holds another
 * width, which that offer leaves bounded, it is sized to that width at the height it holds, or the height it prefers
 * where it holds none, and asked there. A question at the width it holds costs nothing beyond the ask: it is resized
 * and asked again only for another width. Where it answers another height, it is sized to that height and asked again,
 * so the placement that follows does not resize it. Such a resize drops the preferred size Swing holds for it. A
 * measure that runs before a descendant whose height follows its width reflows on its own, as an animation frame does,
 * would then read the height for the descendant's new width and lay it out a second time. The child keeps that size but
 * is not laid out at it: only that pass lays it out, at a width that may differ. Otherwise it answers at the size it
 * holds, as `getPreferredSize()` does.
 */
private fun ChildMeasurable.stockHeightAt(
    minWidth: Int,
    maxWidth: Int,
    mode: MeasureMode,
): Int {
    val width = questionWidth(minWidth, maxWidth)

    // A text component lays its text out at the width it holds only when asked what it prefers or painted, and answers
    // its minimum from that layout, so the minimum is read after the preferred size.
    fun answer(preferred: Dimension) = if (mode == MeasureMode.Minimum) layoutMinimumSize else preferred

    if (width == layoutWidth || !owner.isLaidOutNext) return answer(preferredExtent()).height
    val height = if (layoutHeight > 0) layoutHeight else preferredExtent().height
    val asked = sizedTo(width, height) { layoutPreferredSize }
    val kept = if (asked.height == height) asked else sizedTo(width, asked.height) { layoutPreferredSize }
    preferred = kept
    return answer(kept).height
}

/**
 * Where this child, asked at the layout extent [width] by [height], puts its baseline from the top of its layout
 * bounds, or a negative value where it puts none. A [Constrainable] answers at that extent. A stock child is asked at
 * the [questionWidth] of an offer of [width]. Where [stockHeightAt] would size it, it is sized to that extent and its
 * markup laid out there before the question. Otherwise it answers at the width it holds, where its markup is laid out.
 */
internal fun ChildMeasurable.baselineAt(
    width: Int,
    height: Int,
): Int {
    if (constrainable != null) return layoutBaseline(width, height)
    val askedAt = questionWidth(width, width)
    return if (askedAt == layoutWidth || !owner.isLaidOutNext) {
        layoutBaseline(layoutWidth, height)
    } else {
        sizedTo(askedAt, height) { layoutBaseline(askedAt, height) }
    }
}

/**
 * Whether a layout pass lays this container out after the question being answered: a layout pass of it, or of a
 * Foundation container holding it, is running, or it answers a stock parent that lays it out next. A container left
 * unplaced, or inside one left unplaced, lays nothing out. Neither does a size query from Swing under a Foundation
 * parent, such as that parent reading the container's preferred width: the height is answered at the preferred width,
 * which the parent does not necessarily grant. Only where a layout follows is a stock child resized to answer a
 * question. A stock child resized away and back is invalidated, so the next validation lays its children out again.
 */
private val ChildMeasurables.isLaidOutNext: Boolean
    get() {
        var measurables = this
        while (true) {
            val parent = measurables.panel.decoration.parentMeasurables
            val record = parent?.find(measurables.panel)
            if (record != null && record.lastPlacement != ChildPlacement.Placed) return false
            if (measurables.isAnsweringFoundationParent) return false
            if (measurables.layoutState != LayoutState.Idle) return true
            if (measurables.isAnsweringBeforeLayout) return true
            measurables = parent ?: return false
        }
    }

/**
 * The component's own min intrinsic width at [height], with no layout modifier between: a [Constrainable] answers for
 * [height], and any other component with its minimum width. A Foundation container's minimum size is its min intrinsic
 * width at an unbounded height and its min intrinsic height at that width, and its preferred size the same of the max
 * functions, which Swing holds until the container is invalidated: the width questions at an unbounded height read
 * them, and the max height question under an offer from no minimum width reads the preferred size its container already
 * holds where its width is the one asked at.
 */
internal fun ChildMeasurable.unmodifiedMinIntrinsicWidth(height: Int): Int {
    val constrainable = constrainable
    if (constrainable == null || (height == Constraints.Infinity && foundationPanel != null)) {
        return layoutMinimumSize.width
    }
    return constrainable.minIntrinsicWidth(height)
}

/** The component's own max intrinsic width at [height]; see [unmodifiedMinIntrinsicWidth]. */
internal fun ChildMeasurable.unmodifiedMaxIntrinsicWidth(height: Int): Int {
    val constrainable = constrainable
    if (constrainable == null || (height == Constraints.Infinity && foundationPanel != null)) {
        return preferredExtent().width
    }
    return constrainable.maxIntrinsicWidth(height)
}

/**
 * The component's own min intrinsic height under an offer from [minWidth] to [maxWidth]: a Foundation container runs
 * its policy under that offer, any other [Constrainable] answers at [maxWidth]; see [unmodifiedMinIntrinsicWidth] and
 * [stockHeightAt].
 */
internal fun ChildMeasurable.unmodifiedMinIntrinsicHeight(
    minWidth: Int,
    maxWidth: Int,
): Int {
    val constrainable = constrainable ?: return stockHeightAt(minWidth, maxWidth, MeasureMode.Minimum)
    return foundationPanel?.intrinsicHeightUnder(IntrinsicSize.Min, minWidth, maxWidth)
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
    val constrainable = constrainable ?: return stockHeightAt(minWidth, maxWidth, MeasureMode.Preferred)
    val panel = foundationPanel
    val held = if (minWidth == 0 && component.isValid) layoutPreferredSize else null
    return when {
        panel == null -> constrainable.maxIntrinsicHeight(maxWidth)
        held?.width == maxWidth -> held.height
        else -> panel.intrinsicHeightUnder(IntrinsicSize.Max, minWidth, maxWidth)
    }
}

/** The component, where it is a Foundation container. */
private val ChildMeasurable.foundationPanel: ConstrainedPanel?
    get() = decoratable?.decoration?.childMeasurables?.panel
