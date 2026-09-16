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
 * Adapted from androidx.compose.ui.unit.Constraints in AndroidX's ui-unit; see this module's
 * META-INF/NOTICE for the synced version. The KDoc and code of Infinity, constrain, constrainWidth,
 * constrainHeight, isSatisfiedBy, offset and addMaxWithMinimum are upstream's, except:
 * - Dimension replaces IntSize, built from positional arguments because Kotlin takes no named
 *   arguments for a Java constructor, and the standard library's coerceIn and coerceAtLeast
 *   replace ui-util's fastCoerceIn and fastCoerceAtLeast.
 * - This module's explicit API mode adds `public` and states the types of Infinity,
 *   constrain(size), constrainWidth, constrainHeight and offset, which upstream leaves inferred.
 * - Infinity suppresses ktlint's property-naming rule, and addMaxWithMinimum reads
 *   Constraints.Infinity where upstream reads a file-private copy, which that rule rejects.
 * - ktlint's function-signature rule puts the parameters of offset and addMaxWithMinimum one per
 *   line, and its function-expression-body rule turns the block bodies of isSatisfiedBy and
 *   addMaxWithMinimum into expression bodies.
 * - addMaxWithMinimum drops `inline`: it takes no function-typed parameter, so inlining it
 *   triggers the "nothing to inline" warning, which this build treats as an error.
 */
@file:JvmMultifileClass
@file:JvmName("LayoutKt")

package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Stable
import java.awt.Dimension

/**
 * The extents a parent offers a child: the least it must occupy along each axis and the most it may.
 *
 * A container computes these for each of its children and measures the child under them; the child
 * answers with an extent inside them. [Int.MAX_VALUE] as a maximum means the axis is unbounded - the
 * parent imposes no ceiling there, which is what a container asked for the extent it prefers offers,
 * and what `maximumLayoutSize` already reports for a row.
 *
 * The extents are plain `Int`s, the unit every other geometry in this library is written in.
 *
 * @property minWidth the least width the child must occupy
 * @property maxWidth the most width the child may occupy, [Int.MAX_VALUE] for an unbounded axis
 * @property minHeight the least height the child must occupy
 * @property maxHeight the most height the child may occupy, [Int.MAX_VALUE] for an unbounded axis
 * @throws IllegalArgumentException if either minimum is negative or greater than its own maximum
 */
public class Constraints(
    public val minWidth: Int = 0,
    public val maxWidth: Int = Int.MAX_VALUE,
    public val minHeight: Int = 0,
    public val maxHeight: Int = Int.MAX_VALUE,
) {
    init {
        require(minWidth in 0..maxWidth) {
            "A width ranges from a minimum of zero or more up to its maximum, but minWidth is " +
                "$minWidth and maxWidth is $maxWidth."
        }
        require(minHeight in 0..maxHeight) {
            "A height ranges from a minimum of zero or more up to its maximum, but minHeight is " +
                "$minHeight and maxHeight is $maxHeight."
        }
    }

    /** Whether the width has a finite maximum. */
    public val hasBoundedWidth: Boolean get() = maxWidth != Int.MAX_VALUE

    /** Whether the height has a finite maximum. */
    public val hasBoundedHeight: Boolean get() = maxHeight != Int.MAX_VALUE

    /** Whether the width can take exactly one value. */
    public val hasFixedWidth: Boolean get() = minWidth == maxWidth

    /** Whether the height can take exactly one value. */
    public val hasFixedHeight: Boolean get() = minHeight == maxHeight

    /** Whether every size satisfying these constraints has zero area. */
    @Stable
    public val isZero: Boolean get() = maxWidth == 0 || maxHeight == 0

    /**
     * A new constraint with every extent left unchanged unless this call supplies a replacement.
     *
     * @throws IllegalArgumentException if a replacement makes either axis invalid
     */
    public fun copy(
        minWidth: Int = this.minWidth,
        maxWidth: Int = this.maxWidth,
        minHeight: Int = this.minHeight,
        maxHeight: Int = this.maxHeight,
    ): Constraints =
        Constraints(
            minWidth = minWidth,
            maxWidth = maxWidth,
            minHeight = minHeight,
            maxHeight = maxHeight,
        )

    /** A copy with both minimum dimensions reset to zero. */
    @Stable
    public fun copyMaxDimensions(): Constraints = Constraints(maxWidth = maxWidth, maxHeight = maxHeight)

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is Constraints &&
                    minWidth == other.minWidth &&
                    maxWidth == other.maxWidth &&
                    minHeight == other.minHeight &&
                    maxHeight == other.maxHeight
            )

    override fun hashCode(): Int {
        var result = minWidth
        result = 31 * result + maxWidth
        result = 31 * result + minHeight
        result = 31 * result + maxHeight
        return result
    }

    override fun toString(): String =
        "Constraints(minWidth = $minWidth, maxWidth = ${displayMaximum(maxWidth)}, " +
            "minHeight = $minHeight, maxHeight = ${displayMaximum(maxHeight)})"

    /** The standard constraints. */
    public companion object {
        /**
         * A value that [maxWidth] or [maxHeight] will be set to when the constraint should be
         * considered infinite. [hasBoundedWidth] or [hasBoundedHeight] will be `false` when
         * [maxWidth] or [maxHeight] is [Infinity], respectively.
         */
        @Suppress("ktlint:standard:property-naming")
        public const val Infinity: Int = Int.MAX_VALUE

        /** Constraints that impose nothing: a child takes the extent it asks for on either axis. */
        @Stable
        public val Unbounded: Constraints = Constraints()

        /** Creates constraints for a fixed size in both dimensions. */
        @Stable
        public fun fixed(
            width: Int,
            height: Int,
        ): Constraints =
            Constraints(
                minWidth = width,
                maxWidth = width,
                minHeight = height,
                maxHeight = height,
            )

        /** Creates constraints for a fixed width and an unspecified height. */
        @Stable
        public fun fixedWidth(width: Int): Constraints =
            Constraints(
                minWidth = width,
                maxWidth = width,
            )

        /** Creates constraints for a fixed height and an unspecified width. */
        @Stable
        public fun fixedHeight(height: Int): Constraints =
            Constraints(
                minHeight = height,
                maxHeight = height,
            )
    }
}

/**
 * Takes [otherConstraints] and returns the result of coercing them in the current constraints. Note
 * this means that any size satisfying the resulting constraints will satisfy the current
 * constraints, but they might not satisfy the [otherConstraints] when the two set of constraints
 * are disjoint. Examples (showing only width, height works the same): (minWidth=2,
 * maxWidth=10).constrain(minWidth=7, maxWidth=12) -> (minWidth = 7, maxWidth = 10) (minWidth=2,
 * maxWidth=10).constrain(minWidth=11, maxWidth=12) -> (minWidth=10, maxWidth=10) (minWidth=2,
 * maxWidth=10).constrain(minWidth=5, maxWidth=7) -> (minWidth=5, maxWidth=7)
 */
public fun Constraints.constrain(otherConstraints: Constraints): Constraints {
    val minWidth = minWidth
    val maxWidth = maxWidth
    val minHeight = minHeight
    val maxHeight = maxHeight
    return Constraints(
        minWidth = otherConstraints.minWidth.coerceIn(minWidth, maxWidth),
        maxWidth = otherConstraints.maxWidth.coerceIn(minWidth, maxWidth),
        minHeight = otherConstraints.minHeight.coerceIn(minHeight, maxHeight),
        maxHeight = otherConstraints.maxHeight.coerceIn(minHeight, maxHeight),
    )
}

/** Takes a size and returns the closest size to it that satisfies the constraints. */
@Stable
public fun Constraints.constrain(size: Dimension): Dimension =
    Dimension(
        size.width.coerceIn(minWidth, maxWidth),
        size.height.coerceIn(minHeight, maxHeight),
    )

/** Takes a width and returns the closest size to it that satisfies the constraints. */
@Stable public fun Constraints.constrainWidth(width: Int): Int = width.coerceIn(minWidth, maxWidth)

/** Takes a height and returns the closest size to it that satisfies the constraints. */
@Stable public fun Constraints.constrainHeight(height: Int): Int = height.coerceIn(minHeight, maxHeight)

/** Takes a size and returns whether it satisfies the current constraints. */
@Stable
public fun Constraints.isSatisfiedBy(size: Dimension): Boolean =
    size.width in minWidth..maxWidth && size.height in minHeight..maxHeight

/** Returns the Constraints obtained by offsetting the current instance with the given values. */
@Stable
public fun Constraints.offset(
    horizontal: Int = 0,
    vertical: Int = 0,
): Constraints =
    Constraints(
        (minWidth + horizontal).coerceAtLeast(0),
        addMaxWithMinimum(maxWidth, horizontal),
        (minHeight + vertical).coerceAtLeast(0),
        addMaxWithMinimum(maxHeight, vertical),
    )

private fun addMaxWithMinimum(
    max: Int,
    value: Int,
): Int =
    if (max == Constraints.Infinity) {
        max
    } else {
        (max + value).coerceAtLeast(0)
    }

/** Prints an unconstrained maximum with androidx's public spelling. */
private fun displayMaximum(max: Int): String = if (max == Constraints.Infinity) "Infinity" else max.toString()
