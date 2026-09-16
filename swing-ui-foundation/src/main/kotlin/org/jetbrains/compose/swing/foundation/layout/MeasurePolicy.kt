/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Adapted from androidx.compose.ui.layout.Layout in AndroidX's ui; see this module's
 * META-INF/NOTICE for the synced version. IntrinsicMeasurableAdapter mirrors
 * DefaultIntrinsicMeasurable and LargeDimension, IntrinsicPlaceable mirrors FixedSizeIntrinsicsPlaceable, and
 * placeRelative mirrors Placeable's.
 */

package org.jetbrains.compose.swing.foundation.layout

import java.awt.Dimension

/**
 * How a container measures and places its children.
 *
 * A policy is handed one [Measurable] per child and the [Constraints] the container was given, measures
 * each child under constraints of its own working out, and answers with the extent it occupies and the
 * placement of what it measured.
 *
 * [Row], [Column] and [Box] are each written as one of these.
 */
public fun interface MeasurePolicy {
    /**
     * Measures [measurables] under [constraints] and answers the extent this container occupies, along
     * with where each measured child goes inside it.
     *
     * Under an unbounded main axis a policy dividing a finite extent among its children has nothing to
     * divide; see the four intrinsic functions, which answer Swing's argument-less size queries.
     */
    public fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult

    /** The smallest width that lets [measurables] paint correctly when they are [height] tall. */
    public fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int = intrinsicMeasure(measurables, IntrinsicSize.Min, IntrinsicWidthHeight.Width, height)

    /** The width beyond which growing [measurables] no longer reduces their height at [height]. */
    public fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int = intrinsicMeasure(measurables, IntrinsicSize.Max, IntrinsicWidthHeight.Width, height)

    /** The smallest height that lets [measurables] paint correctly when they are [width] wide. */
    public fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int = intrinsicMeasure(measurables, IntrinsicSize.Min, IntrinsicWidthHeight.Height, width)

    /** The height beyond which growing [measurables] no longer reduces their width at [width]. */
    public fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int = intrinsicMeasure(measurables, IntrinsicSize.Max, IntrinsicWidthHeight.Height, width)
}

/**
 * CMP's default intrinsic-policy adapter, using Swing's integer constraints rather than packed ones. Where
 * [holdToMaximum], each child answers no more than its [maximum size][IntrinsicMeasurable.maximumSize].
 */
internal fun MeasurePolicy.intrinsicMeasure(
    measurables: List<IntrinsicMeasurable>,
    intrinsicSize: IntrinsicSize,
    widthHeight: IntrinsicWidthHeight,
    crossAxisSize: Int,
    holdToMaximum: Boolean = false,
): Int {
    val constrained =
        List(measurables.size) {
            IntrinsicMeasurableAdapter(measurables[it], intrinsicSize, widthHeight, holdToMaximum)
        }
    return intrinsicExtent(widthHeight, crossAxisSize) { measure(constrained, it) }
}

/**
 * The extent [measure] answers along [widthHeight] under a [crossAxisSize] bound on the other axis and an unbounded
 * one along it, which is how a policy or a layout modifier answers an intrinsic question by default.
 */
internal inline fun intrinsicExtent(
    widthHeight: IntrinsicWidthHeight,
    crossAxisSize: Int,
    measure: MeasureScope.(Constraints) -> MeasureResult,
): Int {
    require(crossAxisSize >= 0) { "An intrinsic cross-axis size must be zero or more, but was $crossAxisSize." }
    val constraints =
        if (widthHeight == IntrinsicWidthHeight.Width) {
            Constraints(maxHeight = crossAxisSize)
        } else {
            Constraints(maxWidth = crossAxisSize)
        }
    val result = PolicyMeasureScope.measure(constraints)
    return if (widthHeight == IntrinsicWidthHeight.Width) result.width else result.height
}

/** Identifies the axis an intrinsic question asks its policy to return. */
internal enum class IntrinsicWidthHeight {
    Width,
    Height,
}

/**
 * CMP's default intrinsic measurable adapter.
 *
 * A policy may run normal measurement while answering an intrinsic query. Swing constraints can
 * represent an unbounded axis, but a component policy may use that otherwise irrelevant axis in
 * arithmetic, so this follows CMP and substitutes a finite large value. Where [holdToMaximum], the answer is held to
 * [source]'s [maximum size][IntrinsicMeasurable.maximumSize].
 */
internal class IntrinsicMeasurableAdapter(
    private val source: IntrinsicMeasurable,
    private val intrinsicSize: IntrinsicSize,
    private val widthHeight: IntrinsicWidthHeight,
    private val holdToMaximum: Boolean = false,
) : Measurable {
    override val parentData: Any? get() = source.parentData

    override fun measure(constraints: Constraints): Placeable {
        val maximum = if (holdToMaximum) source.maximumSize() else null
        val width =
            if (widthHeight == IntrinsicWidthHeight.Width) {
                val intrinsic = source.intrinsicWidth(intrinsicSize, constraints.maxHeight)
                if (maximum == null) intrinsic else minOf(intrinsic, maximum.width.coerceAtLeast(0))
            } else {
                if (constraints.hasBoundedWidth) constraints.maxWidth else INTRINSIC_LARGE_DIMENSION
            }
        val height =
            if (widthHeight == IntrinsicWidthHeight.Height) {
                val intrinsic = source.intrinsicHeight(intrinsicSize, constraints.maxWidth)
                if (maximum == null) intrinsic else minOf(intrinsic, maximum.height.coerceAtLeast(0))
            } else {
                if (constraints.hasBoundedHeight) constraints.maxHeight else INTRINSIC_LARGE_DIMENSION
            }
        return IntrinsicPlaceable(width, height)
    }

    override fun minIntrinsicWidth(height: Int): Int = source.minIntrinsicWidth(height)

    override fun maxIntrinsicWidth(height: Int): Int = source.maxIntrinsicWidth(height)

    override fun minIntrinsicHeight(width: Int): Int = source.minIntrinsicHeight(width)

    override fun maxIntrinsicHeight(width: Int): Int = source.maxIntrinsicHeight(width)

    override fun intrinsicPlaceable(
        width: Int,
        height: Int,
    ): Placeable? = source.intrinsicPlaceable(width, height)

    override fun maximumSize(): Dimension? = source.maximumSize()
}

/**
 * A fixed measurement used only while an intrinsic question is answered; never placed, so it reports no alignment
 * line rather than answering through a placement it does not have. Its extent is the one it was given, whatever the
 * constraints it was measured under.
 */
internal class IntrinsicPlaceable(
    override val measuredWidth: Int,
    override val measuredHeight: Int,
) : Placeable() {
    override val width: Int = measuredWidth.coerceAtLeast(0)
    override val height: Int = measuredHeight.coerceAtLeast(0)

    override fun alignmentLineAt(
        alignmentLine: AlignmentLine,
        x: Long,
        y: Long,
    ): Int = AlignmentLine.UNSPECIFIED
}

/** CMP's finite replacement for an intrinsic axis a policy was not asked to determine. */
private const val INTRINSIC_LARGE_DIMENSION: Int = (1 shl 15) - 1

/**
 * The receiver a [MeasurePolicy] or a [LayoutModifierNode] measures in, and the one way it answers.
 *
 * @see layout
 */
public sealed interface MeasureScope : IntrinsicMeasureScope {
    /**
     * The extent this policy or modifier settled on, with [placementBlock] as the block that places what it
     * measured.
     *
     * @param width the width this layout measured itself to. A parent sees this extent
     *   coerced into the constraints it offered, while Swing ultimately receives this raw extent.
     * @param height the height this layout measured itself to. A parent sees this extent
     *   coerced into the constraints it offered, while Swing ultimately receives this raw extent.
     * @param alignmentLines lines this layout provides to its parent, which reads them through
     *   [Placeable.get]. A line named here takes the place of that line of what this layout places; each other
     *   line comes from what it places, where it places it.
     * @param placementBlock places what this layout measured: a policy's children relative to the
     *   container's inner rectangle, a modifier's content relative to the modifier's box
     * @return what this layout settled on.
     * @throws IllegalArgumentException if either extent is negative
     */
    public fun layout(
        width: Int,
        height: Int,
        alignmentLines: Map<AlignmentLine, Int> = emptyMap(),
        placementBlock: PlacementScope.() -> Unit,
    ): MeasureResult
}

/** The [MeasureScope] every policy and layout modifier node of this library is run in; it holds nothing of its own. */
internal object PolicyMeasureScope : MeasureScope {
    override fun layout(
        width: Int,
        height: Int,
        alignmentLines: Map<AlignmentLine, Int>,
        placementBlock: PlacementScope.() -> Unit,
    ): MeasureResult {
        require(width >= 0 && height >= 0) {
            "A container occupies an extent of zero or more, but $width by $height was named."
        }
        return SettledExtent(width, height, alignmentLines, placementBlock)
    }
}

/** What [MeasureScope.layout] hands back: the raw extent named there, and the block that was named with it. */
private class SettledExtent(
    override val width: Int,
    override val height: Int,
    override val alignmentLines: Map<AlignmentLine, Int>,
    private val placementBlock: PlacementScope.() -> Unit,
) : MeasureResult {
    override fun PlacementScope.placeChildren(): Unit = placementBlock()
}

/**
 * What a [MeasurePolicy] settled on: the raw extent the container measured itself to, and where its
 * children go in it.
 *
 * When this result becomes a child's [Placeable], the parent reads a constraint-coerced apparent
 * extent and Swing places the raw extent centered within that apparent space.
 */
public interface MeasureResult {
    /** The width the container actually measured itself to. */
    public val width: Int

    /** The height the container actually measured itself to. */
    public val height: Int

    /** The alignment lines this layout explicitly provides, excluding any child-derived lines. */
    public val alignmentLines: Map<AlignmentLine, Int> get() = emptyMap()

    /**
     * Places every child into a zero-origin, left-to-right rectangle as wide as [width].
     *
     * This is CMP's standalone placement surface. Swing's real layout, nested-modifier replay and
     * baseline probing each have a distinct origin and reading order, so they call the scoped
     * overload below instead.
     */
    public fun placeChildren() {
        val scope = StandalonePlacementScope(width)
        this.run { scope.placeChildren() }
    }

    /** Places every child using the actual Swing pass's origin, width and component orientation. */
    public fun PlacementScope.placeChildren()
}

/** CMP's standalone placement scope, adapted to Swing's zero-origin, left-to-right default. */
private class StandalonePlacementScope(
    override val parentWidth: Int,
) : PlacementScope() {
    override val isLeftToRight: Boolean = true

    override fun Placeable.place(
        x: Int,
        y: Int,
        zIndex: Float,
    ) {
        if (this !is PlacedPlaceable) return
        placeAt(x.toLong(), y.toLong(), zIndex)
    }
}

/** The [PlacementScope.placeWithLayer] and [PlacementScope.placeRelativeWithLayer] default: a layer at its defaults. */
private val DefaultLayerBlock: PlacementLayerScope.() -> Unit = {}

/**
 * The receiver a [MeasureResult] places its children in, from a [MeasurePolicy] or a [LayoutModifierNode].
 *
 * A placement is relative to the container's inner rectangle - inside its insets - so a policy works in
 * the same coordinates it measured in.
 */
public sealed class PlacementScope {
    /** The inner width the policy divided, which is what [placeRelative] mirrors a placement across. */
    public abstract val parentWidth: Int

    /** Whether the container reads left to right; see `java.awt.ComponentOrientation`. */
    public abstract val isLeftToRight: Boolean

    /**
     * Places the child at [x] from the left edge, whatever the orientation.
     *
     * @param x where the child's left edge lands, from the container's inner left edge
     * @param y where the child's top edge lands, from the container's inner top edge
     * @param zIndex the child's place in its container's paint and hit-testing order: a child placed
     *   with a larger value paints over, and receives a mouse event before, every sibling placed with a
     *   smaller one. Siblings placed with the same value keep the order they are declared in. A layout
     *   modifier placing its content adds its value to this one.
     */
    public abstract fun Placeable.place(
        x: Int,
        y: Int,
        zIndex: Float = 0f,
    )

    /**
     * Places the child at [x] from the leading edge, mirroring under a right-to-left parent.
     *
     * @param x where the child's leading edge lands, from the container's inner leading edge
     * @param y where the child's top edge lands, from the container's inner top edge
     * @param zIndex the child's place in its container's paint and hit-testing order; see [place].
     */
    public open fun Placeable.placeRelative(
        x: Int,
        y: Int,
        zIndex: Float = 0f,
    ): Unit = place(saturateLayoutCoordinate(relativeX(this, x)), y, zIndex)

    /**
     * Places the child at [x] from the left edge, whatever the orientation, with a layer whose properties
     * [layerBlock] sets, as androidx's `placeWithLayer` does. Placing the child without a layer on a later pass
     * removes the layer.
     *
     * A [MeasurePolicy]'s layer wraps the child's whole decoration, outside every step of its own modifier, and
     * covers the extent the child measured to. A [LayoutModifierNode]'s layer paints in the place of the node's
     * [decorator][LayoutModifierNode.decorator], at the node's position in the modifier: a decoration declared
     * before the node paints outside the layer, and one declared after it paints inside. Placing with a layer
     * fails while the node holds a decorator, and where the component placed is not a
     * [Decoratable][org.jetbrains.compose.swing.foundation.graphics.Decoratable]. A placement that only replays
     * where the children go, such as [MeasureResult.placeChildren] or an alignment-line query, places the child
     * without a layer.
     *
     * A state read in [layerBlock] repaints the component without measuring it again. Placing with a different
     * block instance repaints the component; pass the same instance to avoid that.
     *
     * @param x where the child's left edge lands, from the container's inner left edge
     * @param y where the child's top edge lands, from the container's inner top edge
     * @param zIndex the child's place in its container's paint and hit-testing order; see [place].
     * @param layerBlock sets the layer's properties; a layer at its defaults when omitted
     */
    public open fun Placeable.placeWithLayer(
        x: Int,
        y: Int,
        zIndex: Float = 0f,
        layerBlock: PlacementLayerScope.() -> Unit = DefaultLayerBlock,
    ): Unit = place(x, y, zIndex)

    /**
     * Places the child at [x] from the leading edge, mirroring under a right-to-left parent, with a layer
     * whose properties [layerBlock] sets; see [placeWithLayer].
     *
     * @param x where the child's leading edge lands, from the container's inner leading edge
     * @param y where the child's top edge lands, from the container's inner top edge
     * @param zIndex the child's place in its container's paint and hit-testing order; see [place].
     * @param layerBlock sets the layer's properties; a layer at its defaults when omitted
     */
    public open fun Placeable.placeRelativeWithLayer(
        x: Int,
        y: Int,
        zIndex: Float = 0f,
        layerBlock: PlacementLayerScope.() -> Unit = DefaultLayerBlock,
    ): Unit = placeWithLayer(saturateLayoutCoordinate(relativeX(this, x)), y, zIndex, layerBlock)

    /** Where [x] from the leading edge lands from the left edge of this scope, as wide as [parentWidth]. */
    internal fun relativeX(
        placeable: Placeable,
        x: Int,
    ): Long = if (isLeftToRight) x.toLong() else parentWidth.toLong() - placeable.width.toLong() - x.toLong()
}

/** A signed coordinate held to the range AWT can represent rather than wrapped across the opposite edge. */
internal fun saturateLayoutCoordinate(value: Long): Int =
    value.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
