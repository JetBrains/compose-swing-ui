package org.jetbrains.compose.swing.components.layout

import org.jetbrains.compose.swing.modifier.SwingModifier

/**
 * The receiver of a [Row]'s content, through which a child declares its own placement in that row.
 *
 * Children are written plainly; what a child declares here rides along on its `modifier`:
 *
 * ```
 * Row {
 *     Label(text = "Status")
 *     FlowPanel(modifier = SwingModifier.weight(1f)) { Details() }
 *     Button(text = "Close", onClick = ::close, modifier = SwingModifier.align(Alignment.Bottom))
 * }
 * ```
 */
public sealed interface RowScope {
    /**
     * Claims [weight] shares of the width the row has left over once every child that claims none has
     * taken the width it prefers. Two children weighted `1f` and `2f` take a third and two thirds of it.
     * A child with an explicit `maximumSize` takes no more than that maximum allows; what it leaves stays
     * empty. With [fill] the child occupies all the width it is granted; otherwise it occupies as much of
     * that width as it prefers and the row's arrangement places the rest.
     *
     * @param weight the share claimed, greater than zero
     * @param fill whether the child occupies the whole width it is granted; `true` by default
     * @return this chain with the width share declared on it.
     */
    public fun SwingModifier.weight(
        weight: Float,
        fill: Boolean = true,
    ): SwingModifier

    /**
     * Places the child at [alignment] across the row's height, in place of the row's own
     * `verticalAlignment`.
     *
     * @param alignment where the child sits across the row
     * @return this chain with the child's vertical alignment declared on it.
     */
    public fun SwingModifier.align(alignment: Alignment.Vertical): SwingModifier

    /**
     * Gives the child the row's whole height in place of the height it prefers, up to an explicit
     * `maximumSize` where it declares one. A child taking the whole height has nowhere left to sit, so
     * this stands in for both its own [align] and the row's `verticalAlignment`.
     */
    public fun SwingModifier.fillHeight(): SwingModifier
}

/**
 * The [RowScope] one [Row] hands its content. It is remembered alongside the row, so the placements a
 * child declares outlive the pass that declared them.
 */
internal class RowScopeImpl : RowScope {
    val placements: ChildPlacements = ChildPlacements()

    override fun SwingModifier.weight(
        weight: Float,
        fill: Boolean,
    ): SwingModifier = this then WeightElement(placements, weightPlacement(weight, fill))

    override fun SwingModifier.align(alignment: Alignment.Vertical): SwingModifier =
        this then AlignElement(placements, VerticalAxisAlignment(alignment))

    override fun SwingModifier.fillHeight(): SwingModifier = this then FillElement(placements)
}
