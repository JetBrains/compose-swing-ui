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
 * Adapted from androidx.compose.foundation.layout.ColumnScope in AndroidX's foundation-layout;
 * see this module's META-INF/NOTICE for the synced version. The alignBy KDoc is
 * a light rewording of upstream's.
 */

package org.jetbrains.compose.swing.foundation.layout

import androidx.annotation.FloatRange
import org.jetbrains.compose.swing.layout.LayoutScopeMarker
import org.jetbrains.compose.swing.modifier.SwingModifier

/**
 * The receiver of a [Column]'s content, through which a child declares its own placement in that column.
 *
 * Children are written plainly; what a child declares here rides along on its `modifier`:
 *
 * ```
 * Column {
 *     Label(text = "Title")
 *     Panel(PanelLayout.Flow(), modifier = SwingModifier.weight(1f)) { Body() }
 *     Button(text = "Close", onClick = ::close, modifier = SwingModifier.align(Alignment.End))
 * }
 * ```
 */
@LayoutScopeMarker
public sealed interface ColumnScope : ConstrainedScope {
    /**
     * Claims [weight] shares of the height the column has left over once every child that claims none
     * has taken the height it prefers. Two children weighted `1f` and `2f` take a third and two thirds
     * of it. A child with an explicit `maximumSize` takes no more than that maximum allows; what it
     * leaves stays empty. With [fill] the child occupies all the height it is granted; otherwise it
     * occupies as much of that height as it prefers and the column's arrangement places the rest.
     *
     * @param weight the share claimed, greater than zero
     * @param fill whether the child occupies the whole height it is granted; `true` by default
     * @return this modifier with the height share declared on it.
     */
    public fun SwingModifier.weight(
        @FloatRange(from = 0.0, fromInclusive = false) weight: Float,
        fill: Boolean = true,
    ): SwingModifier

    /**
     * Places the child at [alignment] across the column's width, in place of the column's own
     * `horizontalAlignment`.
     *
     * @param alignment where the child sits across the column
     * @return this modifier with the child's horizontal alignment declared on it.
     */
    public fun SwingModifier.align(alignment: Alignment.Horizontal): SwingModifier

    /**
     * Places the child across the column's width so that its [alignmentLine] falls on the line it shares with
     * every sibling also declaring `alignBy`. This is a form of [align] and stands in the same place on the
     * modifier chain, so of the two the last one declared places the child. Within a [Column], every child
     * declaring `alignBy` aligns horizontally using the [VerticalAlignmentLine]s it names or the values the other
     * `alignBy` overload works out, forming a sibling group. At least one child of the group is placed as it would
     * be with [Alignment.Start], and the others are placed so that their alignment lines coincide with its line. A
     * child declaring `alignBy` alone in its [Column] is placed as it would be with [Alignment.Start]; a child that
     * provides no such line sits at the column's left edge.
     *
     * A column asking for its own width holds the furthest line from a child's leading edge before the shared line
     * and the furthest remainder past it. A line a [Layout] child's policy provides places that child on the shared
     * line, but does not enter the width the column asks for.
     *
     * @param alignmentLine the line of the child's own that falls on the shared line
     * @return this modifier with the child's placement on the shared line declared on it.
     */
    public fun SwingModifier.alignBy(alignmentLine: VerticalAlignmentLine): SwingModifier

    /**
     * As `alignBy(alignmentLine)`, with the line [alignmentLineBlock] works out from the measured child.
     *
     * @param alignmentLineBlock where the line falls from the leading edge of the measured child
     * @return this modifier with the child's placement on the shared line declared on it.
     */
    public fun SwingModifier.alignBy(alignmentLineBlock: (Placeable) -> Int): SwingModifier
}

/**
 * The [ColumnScope] one [Column] hands its content. What a child declares to it goes onto that child's
 * own modifier, so the scope holds nothing itself and every column shares this one.
 */
@PublishedApi
internal object ColumnScopeInstance : ColumnScope {
    override fun SwingModifier.weight(
        weight: Float,
        fill: Boolean,
    ): SwingModifier = this then WeightElement(weightPlacement(weight, fill))

    override fun SwingModifier.align(alignment: Alignment.Horizontal): SwingModifier =
        this then AlignElement(HorizontalAxisAlignment(alignment))

    override fun SwingModifier.alignBy(alignmentLine: VerticalAlignmentLine): SwingModifier =
        this then AlignElement(AlignmentLineValue(alignmentLine))

    override fun SwingModifier.alignBy(alignmentLineBlock: (Placeable) -> Int): SwingModifier =
        this then AlignElement(AlignmentLineBlock(alignmentLineBlock))
}
