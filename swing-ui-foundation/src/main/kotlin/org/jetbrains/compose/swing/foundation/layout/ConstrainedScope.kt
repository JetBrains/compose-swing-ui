package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.layout.LayoutScopeMarker
import org.jetbrains.compose.swing.modifier.SwingModifier

/**
 * The scope of a container whose child stands between the constraints that container offers it and
 * its own measurement: a fill, a padding, an offset, an aspect ratio or a default minimum size each
 * narrows what reaches the child, states what the child plus its own room occupies, and places the child
 * inside that.
 *
 * [Row], [Column] and [Box] inherit this, so a child of any of them declares these alongside what
 * that container's own scope offers.
 *
 * This scope defines the complete Compose Foundation layout modifier vocabulary scoped to Swing
 * constrained containers so that incompatible standard Swing layout managers fail at compile time
 * rather than at runtime. It is as large as the Foundation modifier vocabulary rather than an interface
 * that grew by accretion, and splitting it into sub-interfaces would fragment the scope hierarchy.
 */
@LayoutScopeMarker
@Suppress("TooManyFunctions")
public interface ConstrainedScope {
    /**
     * Makes the child occupy [fraction] of the greatest bounded width its parent offers it. The result
     * is held between the offered minimum and maximum width. An unbounded width is left unchanged.
     *
     * @param fraction the fraction of the offered maximum width to occupy, from zero through one
     * @throws IllegalArgumentException if [fraction] is outside `0f..1f`
     * @return this modifier with the width fill declared on it.
     */
    public fun SwingModifier.fillMaxWidth(fraction: Float = 1f): SwingModifier =
        this then FillMaxElement.width(fraction)

    /**
     * Makes the child occupy [fraction] of the greatest bounded height its parent offers it. The result
     * is held between the offered minimum and maximum height. An unbounded height is left unchanged.
     *
     * @param fraction the fraction of the offered maximum height to occupy, from zero through one
     * @throws IllegalArgumentException if [fraction] is outside `0f..1f`
     * @return this modifier with the height fill declared on it.
     */
    public fun SwingModifier.fillMaxHeight(fraction: Float = 1f): SwingModifier =
        this then FillMaxElement.height(fraction)

    /**
     * Makes the child occupy [fraction] of the greatest bounded width and height its parent offers it.
     * Either unbounded axis is left unchanged.
     *
     * @param fraction the fraction of each offered maximum extent to occupy, from zero through one
     * @throws IllegalArgumentException if [fraction] is outside `0f..1f`
     * @return this modifier with the width and height fill declared on it.
     */
    public fun SwingModifier.fillMaxSize(fraction: Float = 1f): SwingModifier = this then FillMaxElement.size(fraction)

    /**
     * Prefers an exact [width], while still allowing the constraints the parent offers to override it.
     *
     * @return this modifier with the preferred width declared on it.
     */
    public fun SwingModifier.width(width: Int): SwingModifier =
        this then SizeElement(minWidth = width, maxWidth = width, enforceIncoming = true, name = "width")

    /**
     * Prefers the child's [intrinsicSize] width, while still allowing the constraints the parent offers
     * to override it.
     *
     * @return this modifier with the preferred intrinsic width declared on it.
     */
    public fun SwingModifier.width(intrinsicSize: IntrinsicSize): SwingModifier =
        this then IntrinsicWidthElement(intrinsicSize, enforceIncoming = true, name = "width")

    /**
     * Prefers an exact [height], while still allowing the constraints the parent offers to override it.
     *
     * @return this modifier with the preferred height declared on it.
     */
    public fun SwingModifier.height(height: Int): SwingModifier =
        this then SizeElement(minHeight = height, maxHeight = height, enforceIncoming = true, name = "height")

    /**
     * Prefers the child's [intrinsicSize] height, while still allowing the constraints the parent offers
     * to override it.
     *
     * @return this modifier with the preferred intrinsic height declared on it.
     */
    public fun SwingModifier.height(intrinsicSize: IntrinsicSize): SwingModifier =
        this then IntrinsicHeightElement(intrinsicSize, enforceIncoming = true, name = "height")

    /**
     * Prefers an exact square [size], while still allowing the constraints the parent offers to override it.
     *
     * @return this modifier with the preferred size declared on it.
     */
    public fun SwingModifier.size(size: Int): SwingModifier = size(size, size)

    /**
     * Prefers an exact [width] by [height], while still allowing the constraints the parent offers to
     * override either extent.
     *
     * @return this modifier with the preferred size declared on it.
     */
    public fun SwingModifier.size(
        width: Int,
        height: Int,
    ): SwingModifier =
        this then
            SizeElement(
                minWidth = width,
                minHeight = height,
                maxWidth = width,
                maxHeight = height,
                enforceIncoming = true,
                name = "size",
            )

    /**
     * Prefers a width between [min] and [max], with either bound absent when it is `null`.
     * The constraints the parent offers still take precedence.
     *
     * @return this modifier with the preferred width range declared on it.
     */
    public fun SwingModifier.widthIn(
        min: Int? = null,
        max: Int? = null,
    ): SwingModifier = this then SizeElement(minWidth = min, maxWidth = max, enforceIncoming = true, name = "widthIn")

    /**
     * Prefers a height between [min] and [max], with either bound absent when it is `null`.
     * The constraints the parent offers still take precedence.
     *
     * @return this modifier with the preferred height range declared on it.
     */
    public fun SwingModifier.heightIn(
        min: Int? = null,
        max: Int? = null,
    ): SwingModifier =
        this then SizeElement(minHeight = min, maxHeight = max, enforceIncoming = true, name = "heightIn")

    /**
     * Prefers a size inside the bounds named here, with any `null` bound absent. The constraints the
     * parent offers still take precedence.
     *
     * @return this modifier with the preferred size range declared on it.
     */
    public fun SwingModifier.sizeIn(
        minWidth: Int? = null,
        minHeight: Int? = null,
        maxWidth: Int? = null,
        maxHeight: Int? = null,
    ): SwingModifier =
        this then
            SizeElement(
                minWidth = minWidth,
                minHeight = minHeight,
                maxWidth = maxWidth,
                maxHeight = maxHeight,
                enforceIncoming = true,
                name = "sizeIn",
            )

    /**
     * Requires an exact [width], even where it is outside the constraints the parent offers.
     *
     * @return this modifier with the required width declared on it.
     */
    public fun SwingModifier.requiredWidth(width: Int): SwingModifier =
        this then SizeElement(minWidth = width, maxWidth = width, enforceIncoming = false, name = "requiredWidth")

    /**
     * Requires the child's [intrinsicSize] width, even where it is outside the constraints the parent offers.
     *
     * @return this modifier with the required intrinsic width declared on it.
     */
    public fun SwingModifier.requiredWidth(intrinsicSize: IntrinsicSize): SwingModifier =
        this then IntrinsicWidthElement(intrinsicSize, enforceIncoming = false, name = "requiredWidth")

    /**
     * Requires an exact [height], even where it is outside the constraints the parent offers.
     *
     * @return this modifier with the required height declared on it.
     */
    public fun SwingModifier.requiredHeight(height: Int): SwingModifier =
        this then SizeElement(minHeight = height, maxHeight = height, enforceIncoming = false, name = "requiredHeight")

    /**
     * Requires the child's [intrinsicSize] height, even where it is outside the constraints the parent offers.
     *
     * @return this modifier with the required intrinsic height declared on it.
     */
    public fun SwingModifier.requiredHeight(intrinsicSize: IntrinsicSize): SwingModifier =
        this then IntrinsicHeightElement(intrinsicSize, enforceIncoming = false, name = "requiredHeight")

    /**
     * Requires an exact square [size], even where it is outside the constraints the parent offers.
     *
     * @return this modifier with the required size declared on it.
     */
    public fun SwingModifier.requiredSize(size: Int): SwingModifier = requiredSize(size, size)

    /**
     * Requires an exact [width] by [height], even where either is outside the constraints the parent offers.
     *
     * @return this modifier with the required size declared on it.
     */
    public fun SwingModifier.requiredSize(
        width: Int,
        height: Int,
    ): SwingModifier =
        this then
            SizeElement(
                minWidth = width,
                minHeight = height,
                maxWidth = width,
                maxHeight = height,
                enforceIncoming = false,
                name = "requiredSize",
            )

    /**
     * Requires a width inside the bounds named here, with either bound absent when it is `null`.
     *
     * @return this modifier with the required width range declared on it.
     */
    public fun SwingModifier.requiredWidthIn(
        min: Int? = null,
        max: Int? = null,
    ): SwingModifier =
        this then SizeElement(minWidth = min, maxWidth = max, enforceIncoming = false, name = "requiredWidthIn")

    /**
     * Requires a height inside the bounds named here, with either bound absent when it is `null`.
     *
     * @return this modifier with the required height range declared on it.
     */
    public fun SwingModifier.requiredHeightIn(
        min: Int? = null,
        max: Int? = null,
    ): SwingModifier =
        this then SizeElement(minHeight = min, maxHeight = max, enforceIncoming = false, name = "requiredHeightIn")

    /**
     * Requires a size inside the bounds named here, with any `null` bound absent.
     *
     * @return this modifier with the required size range declared on it.
     */
    public fun SwingModifier.requiredSizeIn(
        minWidth: Int? = null,
        minHeight: Int? = null,
        maxWidth: Int? = null,
        maxHeight: Int? = null,
    ): SwingModifier =
        this then
            SizeElement(
                minWidth = minWidth,
                minHeight = minHeight,
                maxWidth = maxWidth,
                maxHeight = maxHeight,
                enforceIncoming = false,
                name = "requiredSizeIn",
            )

    /**
     * Lets the child choose its width without the offered minimum and, where [unbounded], maximum;
     * it is placed within the resulting wrapper by [align].
     *
     * @return this modifier with the wrapped width declared on it.
     */
    public fun SwingModifier.wrapContentWidth(
        align: Alignment.Horizontal = Alignment.CenterHorizontally,
        unbounded: Boolean = false,
    ): SwingModifier = this then WrapContentElement.width(align, unbounded)

    /**
     * Lets the child choose its height without the offered minimum and, where [unbounded], maximum;
     * it is placed within the resulting wrapper by [align].
     *
     * @return this modifier with the wrapped height declared on it.
     */
    public fun SwingModifier.wrapContentHeight(
        align: Alignment.Vertical = Alignment.CenterVertically,
        unbounded: Boolean = false,
    ): SwingModifier = this then WrapContentElement.height(align, unbounded)

    /**
     * Lets the child choose both extents without the offered minima and, where [unbounded], maxima;
     * it is placed within the resulting wrapper by [align].
     *
     * @return this modifier with the wrapped size declared on it.
     */
    public fun SwingModifier.wrapContentSize(
        align: Alignment = Alignment.Center,
        unbounded: Boolean = false,
    ): SwingModifier = this then WrapContentElement.size(align, unbounded)

    /**
     * Sizes the child to [ratio] width per unit height, taking the size from the greatest width its
     * incoming constraints allow, then the greatest height, then the least width and the least
     * height, and stopping at the first of those that satisfies both the constraints and the ratio.
     * Where none of them does, the constraints are not respected: the child takes the size the first
     * of those extents that names a size at all implies at the ratio, and only where none of them
     * names one is the child measured under the incoming constraints unchanged.
     *
     * @param ratio the desired width to height ratio, finite and greater than zero
     * @param matchHeightConstraintsFirst takes the size from the greatest height, then the greatest
     *   width, then the least height and the least width, for a child whose height is the extent
     *   that should decide the other; `false` by default
     * @return this modifier with the aspect ratio declared on it.
     */
    public fun SwingModifier.aspectRatio(
        ratio: Float,
        matchHeightConstraintsFirst: Boolean = false,
    ): SwingModifier = this then AspectRatioElement(ratio, matchHeightConstraintsFirst)

    /**
     * Reserves [all] along every edge of the child.
     *
     * @return this modifier with the padding declared on it.
     */
    public fun SwingModifier.padding(all: Int): SwingModifier = this then PaddingElement(all, all, all, all)

    /**
     * Reserves [start] before the child and [end] after it along the reading order, and [top] and
     * [bottom] above and below it, each edge left unreserved by default. [start] and [end] swap edges
     * under a right-to-left reading order; see [absolutePadding] for a padding that never does.
     *
     * A padding reserves room, so none of the four is ever below zero; declare [offset] to move a
     * child outward from where its container places it.
     *
     * @return this modifier with the padding declared on it.
     */
    public fun SwingModifier.padding(
        start: Int = 0,
        top: Int = 0,
        end: Int = 0,
        bottom: Int = 0,
    ): SwingModifier = this then PaddingElement(start, top, end, bottom)

    /**
     * Reserves [horizontal] before and after the child along the reading order, and [vertical] above
     * and below it, either pair left unreserved by default.
     *
     * @return this modifier with the padding declared on it.
     */
    public fun SwingModifier.padding(
        horizontal: Int = 0,
        vertical: Int = 0,
    ): SwingModifier = padding(horizontal, vertical, horizontal, vertical)

    /**
     * Reserves [left], [top], [right] and [bottom] along the child's edges, each left unreserved by
     * default, the same under a right-to-left reading order as under a left-to-right one; see
     * [padding] for a padding that follows the reading order instead.
     *
     * None of the four is ever below zero, the same as for [padding].
     *
     * @return this modifier with the padding declared on it.
     */
    public fun SwingModifier.absolutePadding(
        left: Int = 0,
        top: Int = 0,
        right: Int = 0,
        bottom: Int = 0,
    ): SwingModifier = this then AbsolutePaddingElement(left, top, right, bottom)

    /**
     * Moves the child by ([x], [y]) from where it would otherwise sit, neither axis moved by default,
     * without changing the room it measures into. A positive [x] moves the child toward the trailing
     * edge: right under a left-to-right reading order and left under a right-to-left one. See
     * [absoluteOffset] for an offset that always moves it toward the right.
     *
     * @return this modifier with the offset declared on it.
     */
    public fun SwingModifier.offset(
        x: Int = 0,
        y: Int = 0,
    ): SwingModifier = this then OffsetElement(x, y)

    /**
     * Moves the child by ([x], [y]) from where it would otherwise sit, neither axis moved by default,
     * the same under a right-to-left reading order as under a left-to-right one; see [offset] for an
     * offset that follows the reading order instead.
     *
     * @return this modifier with the offset declared on it.
     */
    public fun SwingModifier.absoluteOffset(
        x: Int = 0,
        y: Int = 0,
    ): SwingModifier = this then AbsoluteOffsetElement(x, y)

    /**
     * Raises the child's minimum size to [minWidth] by [minHeight] along whichever axis its incoming
     * constraints leave a minimum of zero on. An axis already claiming a minimum is left as it is,
     * and the minimum raised to is held between nothing and the room the child was offered, so a
     * child in a container smaller than the minimum takes the container.
     *
     * @return this modifier with the default minimum size declared on it.
     */
    public fun SwingModifier.defaultMinSize(
        minWidth: Int? = null,
        minHeight: Int? = null,
    ): SwingModifier = this then DefaultMinSizeElement(minWidth, minHeight)
}
