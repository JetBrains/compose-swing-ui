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
     * Puts the child on the row's shared text baseline, which is what a label beside a text field sits
     * on. Every child declaring it is placed so that the baseline its component reports falls on one
     * line, and a row asking for its own height holds the deepest baseline above that line and the
     * deepest remainder below it.
     *
     * This is a form of [align] and stands in the same place on the modifier chain, so of the two the
     * last one declared places the child, and either stands in for the row's `verticalAlignment`.
     *
     * A child whose component reports no baseline - `java.awt.Component.getBaseline` gives `-1` - sits
     * against the row's top edge and takes no part in the shared line, the way `javax.swing.GroupLayout`
     * places one in a baseline group.
     *
     * @return this modifier with the child's placement on the shared baseline declared on it.
     */
    public fun SwingModifier.alignByBaseline(): SwingModifier
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
    ): SwingModifier =
        this then
            WeightElement(
                weightPlacement(weight, fill),
                LinearParentDataProtocol,
            )

    override fun SwingModifier.align(alignment: Alignment.Vertical): SwingModifier =
        this then AlignElement(VerticalAxisAlignment(alignment), LinearParentDataProtocol)

    override fun SwingModifier.alignByBaseline(): SwingModifier =
        this then AlignElement(BaselineAxisAlignment, LinearParentDataProtocol)
}
