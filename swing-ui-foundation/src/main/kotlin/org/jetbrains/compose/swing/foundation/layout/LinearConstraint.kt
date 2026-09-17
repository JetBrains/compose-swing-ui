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
 * Adapted from androidx.compose.foundation.layout.CrossAxisAlignment in AndroidX's
 * foundation-layout; see this module's META-INF/NOTICE for the synced version.
 * AlignmentLineCrossAxisAlignment's beforeCrossAxisAlignmentLine parameter and its RTL line
 * math are upstream's.
 */

package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.layout.ParentDataModifier
import org.jetbrains.compose.swing.layout.ParentProtocol
import java.awt.ComponentOrientation

/**
 * What a child of a [Row] or a [Column] declares for itself: the share of the leftover space it claims
 * and the cross-axis placement it names in place of its container's.
 *
 * A [RowMeasurePolicy] or [ColumnMeasurePolicy] reads it through [Measurable.parentData].
 *
 * @property weight what the child claims of the leftover space, or `null` when it takes the size it
 *   prefers.
 * @property alignment where the child sits across the axis, or `null` to leave that to its container.
 */
internal data class LinearConstraint(
    val weight: WeightPlacement? = null,
    val alignment: CrossAxisAlignment? = null,
)

/**
 * A child's claim on the space its container has left over: [weight] shares of it, and whether the child
 * occupies all of what it is granted ([fill]) or only as much of it as it prefers.
 */
internal data class WeightPlacement(
    val weight: Float,
    val fill: Boolean,
)

/**
 * Where a [RowMeasurePolicy] or [ColumnMeasurePolicy] places a child across its axis, as androidx's RowColumnImpl
 * `CrossAxisAlignment` does: by an [AxisAlignment], or by an alignment line the child shares with its siblings. Both
 * are declared through the one [LinearConstraint.alignment], so of an `align` and an `alignBy` the last declared
 * places the child. Implementations compare by value, which is what lets an unchanged declaration be recognized as
 * the placement already in force.
 */
internal sealed interface CrossAxisAlignment {
    /** Whether this places the child by an alignment line it shares with every sibling declaring one. */
    val isRelative: Boolean get() = false

    /** Where the child's line falls from the edge [placeable] is placed at, or [AlignmentLine.UNSPECIFIED]. */
    fun linePosition(placeable: Placeable): Int = AlignmentLine.UNSPECIFIED

    /**
     * Where [placeable], [size] across the axis, sits in the container's cross-axis extent [space] under
     * [orientation], the shared line lying [beforeCrossAxisAlignmentLine] from the container's leading edge.
     */
    fun align(
        size: Int,
        space: Int,
        orientation: ComponentOrientation,
        placeable: Placeable,
        beforeCrossAxisAlignmentLine: Int,
    ): Int
}

/**
 * A child placed so that its alignment line falls on the line it shares with every sibling declaring one, as
 * `alignBy` and `alignByBaseline` declare it. A child without the line sits at the top of a row or the left
 * edge of a column.
 */
internal sealed class AlignmentLineCrossAxisAlignment : CrossAxisAlignment {
    override val isRelative: Boolean get() = true

    abstract override fun linePosition(placeable: Placeable): Int

    override fun align(
        size: Int,
        space: Int,
        orientation: ComponentOrientation,
        placeable: Placeable,
        beforeCrossAxisAlignmentLine: Int,
    ): Int {
        val position = linePosition(placeable)
        if (position == AlignmentLine.UNSPECIFIED) return 0
        val line = beforeCrossAxisAlignmentLine - position
        return if (orientation.isLeftToRight) line else space - size - line
    }
}

/** The child's own [line], as `alignBy(alignmentLine)` and `alignByBaseline` declare it. */
internal data class AlignmentLineValue(
    val line: AlignmentLine,
) : AlignmentLineCrossAxisAlignment() {
    override fun linePosition(placeable: Placeable): Int = placeable[line]
}

/** The line [block] works out from the measured child, as `alignBy(alignmentLineBlock)` declares it. */
internal data class AlignmentLineBlock(
    val block: (Placeable) -> Int,
) : AlignmentLineCrossAxisAlignment() {
    override fun linePosition(placeable: Placeable): Int = block(placeable)
}

/**
 * Builds the claim a scope's `weight` extension declares. An infinite weight is taken as the largest
 * finite one, so a child asking for everything gets it rather than an arithmetic answer.
 */
internal fun weightPlacement(
    weight: Float,
    fill: Boolean,
): WeightPlacement {
    require(weight > 0f) { "A weight must be greater than zero, but was $weight." }
    return WeightPlacement(weight.coerceAtMost(Float.MAX_VALUE), fill)
}

/** The share of the leftover space a child claims, as a row's or a column's `weight` declares it. */
internal data class WeightElement(
    val placement: WeightPlacement,
) : ParentDataModifier {
    override val parentProtocol: ParentProtocol get() = LinearParentDataProtocol

    override val key: Any get() = WeightElement::class

    override val name: String get() = "weight"

    override val declaredValues: Map<String, Any?> get() = mapOf("weight" to placement)

    override fun modifyParentData(parentData: Any?): Any =
        (parentData as? LinearConstraint ?: LinearConstraint()).copy(weight = placement)
}

/** Where across the axis a child sits, as a row's or a column's `align` declares it. */
internal data class AlignElement(
    val alignment: CrossAxisAlignment,
) : ParentDataModifier {
    override val parentProtocol: ParentProtocol get() = LinearParentDataProtocol

    override val key: Any get() = AlignElement::class

    override val name: String get() = "align"

    override val declaredValues: Map<String, Any?> get() = mapOf("alignment" to alignment)

    override fun modifyParentData(parentData: Any?): Any =
        (parentData as? LinearConstraint ?: LinearConstraint()).copy(alignment = alignment)
}
