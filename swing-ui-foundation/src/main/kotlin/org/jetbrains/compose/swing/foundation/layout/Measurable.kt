/*
 * Copyright 2019 The Android Open Source Project
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
 * Adapted from androidx.compose.ui.layout.IntrinsicMeasurable in AndroidX's ui; see this
 * module's META-INF/NOTICE for the synced version. The intrinsic-size KDoc is a light rewording
 * of upstream's, and Placeable's width/measuredWidth docs are upstream's.
 */

package org.jetbrains.compose.swing.foundation.layout

import java.awt.Dimension

/** A child as a [MeasurePolicy] asks about its unconstrained, axis-specific dimensions. */
public sealed interface IntrinsicMeasurable {
    /** What the child declared to this container - a weight, an alignment, a scope's own value. */
    public val parentData: Any?

    /** The least width that lets this child paint correctly when it is [height] tall. */
    public fun minIntrinsicWidth(height: Int): Int

    /** The width beyond which growing this child no longer reduces its height at [height]. */
    public fun maxIntrinsicWidth(height: Int): Int

    /** The least height that lets this child paint correctly when it is [width] wide. */
    public fun minIntrinsicHeight(width: Int): Int

    /** The height beyond which growing this child no longer reduces its width at [width]. */
    public fun maxIntrinsicHeight(width: Int): Int

    /**
     * A stand-in for this child at [width] by [height], which a policy answering an intrinsic question reads alignment
     * lines from without measuring the child, or null where it has none. A component answers [FirstBaseline] with its
     * Swing baseline at that size, and each of its layout modifiers moves or names lines through
     * [LayoutModifierNode.intrinsicPlaceable]. Placing it has no effect. Null by default.
     */
    public fun intrinsicPlaceable(
        width: Int,
        height: Int,
    ): Placeable? = null

    /**
     * The maximum size set on this child's component, which a `Row`, `Column` or `Box` holds the whole child to,
     * outside its layout modifiers, or null where none is set. Null by default.
     */
    public fun maximumSize(): Dimension? = null
}

/**
 * One child of a container that can answer both intrinsic and constrained measurement questions.
 *
 * Swing widgets expose argument-less preferred and minimum sizes, rather than a cross-axis-sensitive
 * intrinsic protocol. Foundation therefore answers its intrinsic functions from those stock Swing
 * sizes, while policy containers forward the question through their own policy.
 */
public sealed interface Measurable : IntrinsicMeasurable {
    /**
     * The extent the child takes under [constraints].
     *
     * A [MeasurePolicy] measures each child at most once per pass, and only in its measure block or in
     * its placement block. A size it needs before choosing the constraints comes from the intrinsic
     * functions. A [LayoutModifierNode] may measure its inner measurable again; each answer is then an
     * independent [Placeable].
     *
     * @throws IllegalStateException if the policy already measured this child in the same pass, or
     * measures it outside both blocks
     */
    public fun measure(constraints: Constraints): Placeable
}

/** The receiver of a [MeasurePolicy]'s four intrinsic measurement functions. */
public sealed interface IntrinsicMeasureScope

/**
 * A child measured, and the handle its container places it by.
 *
 * A placeable is one measurement of a child. It retains the component extent and layout modifier offsets
 * computed for that measurement. The alignment lines of a Foundation container follow its latest measure.
 */
public sealed class Placeable {
    /**
     * The width the parent sees: [measuredWidth] coerced inside the constraints passed to
     * [Measurable.measure].
     *
     * A parent normally lays its children out from this apparent extent, so one that measures a
     * child outside its offer still has an extent it can safely account for.
     */
    public abstract val width: Int

    /**
     * The height the parent sees: [measuredHeight] coerced inside the constraints passed to
     * [Measurable.measure].
     */
    public abstract val height: Int

    /** The width the child actually measured itself to, before its parent coerced [width]. */
    public abstract val measuredWidth: Int

    /** The height the child actually measured itself to, before its parent coerced [height]. */
    public abstract val measuredHeight: Int

    /** The child this placeable ends up placing. */
    internal abstract val child: ChildMeasurable

    /**
     * @param x where this placeable's origin lands, in its caller's coordinates.
     * @param y where this placeable's origin lands, in its caller's coordinates.
     * @param zIndex the z-index placed so far, summed through layout modifiers.
     */
    internal abstract fun placeAt(
        x: Long,
        y: Long,
        zIndex: Float,
    )

    /**
     * Where [alignmentLine] falls when this placeable's origin lands at ([x], [y]), or
     * [AlignmentLine.UNSPECIFIED] where the child provides no such line.
     */
    internal abstract fun alignmentLineAt(
        alignmentLine: AlignmentLine,
        x: Long,
        y: Long,
    ): Int

    /**
     * Merges into [scope] each line this placeable puts when its origin lands at ([x], [y]). A stand-in puts none, as
     * placing it has no effect.
     */
    internal open fun mergeAlignmentLines(
        scope: LineMergingPlacementScope,
        x: Long,
        y: Long,
    ): Unit = Unit

    /**
     * Where [alignmentLine] falls from the edge of the box this placeable is placed at, [width] by
     * [height], or [AlignmentLine.UNSPECIFIED] where the child provides no such line.
     */
    public open operator fun get(alignmentLine: AlignmentLine): Int {
        val state = child.owner.layoutState
        if (state == LayoutState.Measuring || child.lineReadDuring == LayoutState.Idle) {
            child.lineReadDuring = state
        }
        return alignmentLineAt(alignmentLine, 0L, 0L)
    }
}
