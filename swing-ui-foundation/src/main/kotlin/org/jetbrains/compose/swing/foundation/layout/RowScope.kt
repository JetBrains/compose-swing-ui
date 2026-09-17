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
 * Adapted from androidx.compose.foundation.layout.RowScope in AndroidX's foundation-layout; see
 * this module's META-INF/NOTICE for the synced version. The alignBy/alignByBaseline KDoc is a
 * light rewording of upstream's.
 */

package org.jetbrains.compose.swing.foundation.layout

import androidx.annotation.FloatRange
import org.jetbrains.compose.swing.layout.LayoutScopeMarker
import org.jetbrains.compose.swing.modifier.SwingModifier

/**
 * The receiver of a [Row]'s content, through which a child declares its own placement in that row.
 *
 * Children are written plainly; what a child declares here rides along on its `modifier`:
 *
 * ```
 * Row {
 *     Label(text = "Status")
 *     Panel(PanelLayout.Flow(), modifier = SwingModifier.weight(1f)) { Details() }
 *     Button(text = "Close", onClick = ::close, modifier = SwingModifier.align(Alignment.Bottom))
 * }
 * ```
 */
@LayoutScopeMarker
public sealed interface RowScope : ConstrainedScope {
    /**
     * Claims [weight] shares of the width the row has left over once every child that claims none has
     * taken the width it prefers. Two children weighted `1f` and `2f` take a third and two thirds of it.
     * A child with an explicit `maximumSize` takes no more than that maximum allows; what it leaves stays
     * empty. With [fill] the child occupies all the width it is granted; otherwise it occupies as much of
     * that width as it prefers and the row's arrangement places the rest.
     *
     * @param weight the share claimed, greater than zero
     * @param fill whether the child occupies the whole width it is granted; `true` by default
     * @return this modifier with the width share declared on it.
     */
    public fun SwingModifier.weight(
        @FloatRange(from = 0.0, fromInclusive = false) weight: Float,
        fill: Boolean = true,
    ): SwingModifier

    /**
     * Places the child at [alignment] across the row's height, in place of the row's own
     * `verticalAlignment`.
     *
     * @param alignment where the child sits across the row
     * @return this modifier with the child's vertical alignment declared on it.
     */
    public fun SwingModifier.align(alignment: Alignment.Vertical): SwingModifier

    /**
     * Places the child across the row's height so that its [alignmentLine] falls on the line it shares with every
     * sibling also declaring `alignBy`. This is a form of [align] and stands in the same place on the modifier
     * chain, so of the two the last one declared places the child. Within a [Row], every child declaring
     * `alignBy` aligns vertically using the [HorizontalAlignmentLine]s it names or the values the other `alignBy`
     * overload works out, forming a sibling group. At least one child of the group is placed as it would be with
     * [Alignment.Top], and the others are placed so that their alignment lines coincide with its line. A child
     * declaring `alignBy` alone in its [Row] is placed as it would be with [Alignment.Top]; so is a child that
     * provides no such line.
     *
     * A row asking for its own height holds the deepest line above the shared line and the deepest remainder
     * below it. A line a [Layout] child's policy provides places that child on the shared line, but does not
     * enter the height the row asks for.
     *
     * @param alignmentLine the line of the child's own that falls on the shared line
     * @return this modifier with the child's placement on the shared line declared on it.
     * @see alignByBaseline
     */
    public fun SwingModifier.alignBy(alignmentLine: HorizontalAlignmentLine): SwingModifier

    /**
     * Puts the child on the row's shared text baseline, which is what a label beside a text field sits
     * on. Every child declaring it is placed so that its [FirstBaseline] falls on one line. A row asking
     * for its own height holds the deepest baseline above that line and the deepest remainder below it. A
     * line a [Layout] child's policy provides places that child on the shared line, but does not enter the
     * height the row asks for.
     *
     * This is a particular case of `alignBy`, `alignBy(FirstBaseline)`, and a form of [align] that stands in the
     * same place on the modifier chain, so of the two the last one declared places the child, and either stands
     * in for the row's `verticalAlignment`.
     *
     * A child without a [FirstBaseline] sits against the row's top edge and takes no part in the shared
     * line, the way `javax.swing.GroupLayout` places a component whose `getBaseline` gives `-1` in a
     * baseline group.
     *
     * @return this modifier with the child's placement on the shared baseline declared on it.
     * @see alignBy
     */
    public fun SwingModifier.alignByBaseline(): SwingModifier

    /**
     * As `alignBy(alignmentLine)`, with the line [alignmentLineBlock] works out from the measured child.
     *
     * @param alignmentLineBlock where the line falls from the top of the measured child
     * @return this modifier with the child's placement on the shared line declared on it.
     */
    public fun SwingModifier.alignBy(alignmentLineBlock: (Placeable) -> Int): SwingModifier
}

/**
 * The [RowScope] one [Row] hands its content. What a child declares to it goes onto that child's own
 * modifier, so the scope holds nothing itself and every row shares this one.
 */
@PublishedApi
internal object RowScopeInstance : RowScope {
    override fun SwingModifier.weight(
        weight: Float,
        fill: Boolean,
    ): SwingModifier = this then WeightElement(weightPlacement(weight, fill))

    override fun SwingModifier.align(alignment: Alignment.Vertical): SwingModifier =
        this then AlignElement(VerticalAxisAlignment(alignment))

    override fun SwingModifier.alignBy(alignmentLine: HorizontalAlignmentLine): SwingModifier =
        this then AlignElement(AlignmentLineValue(alignmentLine))

    override fun SwingModifier.alignByBaseline(): SwingModifier = alignBy(FirstBaseline)

    override fun SwingModifier.alignBy(alignmentLineBlock: (Placeable) -> Int): SwingModifier =
        this then AlignElement(AlignmentLineBlock(alignmentLineBlock))
}
