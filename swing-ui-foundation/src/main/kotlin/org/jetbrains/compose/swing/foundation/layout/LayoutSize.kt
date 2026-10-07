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
 * the width it holds, so it is sized for the reading, as `JViewport` sizes a view that tracks its width. While its
 * container validates it keeps that size, which the placement following sets or hides it from; otherwise it is given
 * its own size back, since only a placement sets a child's bounds.
 */
internal fun ChildMeasurable.preferredExtentSizedTo(
    width: Int,
    height: Int,
): Dimension = sizedTo(width, height, keeps = owner.isValidating) { layoutPreferredSize }.also { preferred = it }

/**
 * What [read] answers with this child sized to the layout extent [width] by [height], its baseline asked at that size
 * first: a label showing HTML lays its markup out at the width `getBaseline` names. The child keeps that size where
 * [keeps] holds, and is given its own size back otherwise. No resize asks for a layout pass.
 */
private inline fun ChildMeasurable.sizedTo(
    width: Int,
    height: Int,
    keeps: Boolean,
    read: () -> Dimension,
): Dimension {
    val outsets = decoratable?.decoration?.heldPaintOutsets ?: NoPaintOutsets
    val heldWidth = component.width
    val heldHeight = component.height
    var extent = Dimension()
    owner.during(RunningCause.SettledResultKept) {
        component.setSize(width.grownBy(outsets.left + outsets.right), height.grownBy(outsets.top + outsets.bottom))
        layoutBaseline(width, height)
        extent = read()
        if (!keeps) component.setSize(heldWidth, heldHeight)
    }
    return extent
}

/**
 * This stock child's preferred height, or its minimum height where [minimum] holds, under an offer of a layout width
 * from [minWidth] to [maxWidth], at the width its measure takes there: the width it prefers, held to the offer, as
 * androidx asks a leaf at the width its own layout takes. A fixed width, as a filling modifier or a filling weight
 * offers, is that width. Swing's own layouts size a component before asking it: the child answers as it is where it
 * holds that width or the offer is unbounded, and is otherwise sized to that width at the height it holds, or the
 * height it prefers where it holds none, and asked there.
 *
 * Where a layout pass lays its container out next, the child keeps that size, laid out at it, and that pass sets or
 * hides it from there. Otherwise it is given its size back and asked again there, so it ends laid out as it was, and
 * every component stays as valid as it was.
 */
private fun ChildMeasurable.stockHeightAt(
    minWidth: Int,
    maxWidth: Int,
    minimum: Boolean,
): Int {
    val width =
        when {
            maxWidth == Constraints.Infinity -> layoutWidth
            minWidth == maxWidth -> maxWidth
            else -> preferredExtent().width.coerceIn(minWidth, maxWidth)
        }

    // A text component lays its text out at the width it holds only when asked what it prefers or painted, and answers
    // its minimum from that layout, so the minimum is read after the preferred size.
    fun answer(preferred: Dimension) = if (minimum) layoutMinimumSize else preferred

    if (width == layoutWidth) return answer(preferredExtent()).height
    val height = if (layoutHeight > 0) layoutHeight else preferredExtent().height
    var extent = Dimension()
    if (owner.isLaidOutNext) {
        extent =
            sizedTo(width, height, keeps = true) {
                answer(layoutPreferredSize.also { preferred = it }).also { component.validate() }
            }
    } else {
        val heldWidth = layoutWidth
        val heldHeight = layoutHeight
        val holdsNoSize = component.width == 0 && component.height == 0
        val valid = component.isValid
        owner.during(RunningCause.SizingForQuestion) {
            extent = sizedTo(width, height, keeps = false) { answer(layoutPreferredSize) }
            if (!holdsNoSize) layoutBaseline(heldWidth, heldHeight)
            layoutPreferredSize
            if (valid) component.validate()
        }
    }
    return extent.height
}

/**
 * Whether a layout pass lays this container out after the question being answered: a layout pass of it, or of a
 * Foundation container holding it, is running. A container left unplaced, or inside one left unplaced, lays nothing
 * out.
 */
private val ChildMeasurables.isLaidOutNext: Boolean
    get() {
        var measurables = this
        while (true) {
            val parent = measurables.panel.decoration.parentMeasurables
            val record = parent?.find(measurables.panel)
            if (record != null && record.lastPlacement != ChildPlacement.Placed) return false
            if (measurables.layoutState != LayoutState.Idle) return true
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
    val constrainable = constrainable ?: return stockHeightAt(minWidth, maxWidth, minimum = true)
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
    val constrainable = constrainable ?: return stockHeightAt(minWidth, maxWidth, minimum = false)
    val panel = foundationPanel
    val held = preferred?.takeIf { minWidth == 0 && component.isValid }
    return when {
        panel == null -> constrainable.maxIntrinsicHeight(maxWidth)
        held?.width == maxWidth -> held.height
        else -> panel.intrinsicHeightUnder(IntrinsicSize.Max, minWidth, maxWidth)
    }
}

/** The component, where it is a Foundation container. */
private val ChildMeasurable.foundationPanel: ConstrainedPanel?
    get() = decoratable?.decoration?.childMeasurables?.panel

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
