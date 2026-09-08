package org.jetbrains.compose.swing.components.layout

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
public sealed interface ColumnScope {
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
        weight: Float,
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
     * Gives the child the column's whole width in place of the width it prefers, up to an explicit
     * `maximumSize` where it declares one. A child taking the whole width has nowhere left to sit, so
     * this stands in for both its own [align] and the column's `horizontalAlignment`.
     */
    public fun SwingModifier.fillWidth(): SwingModifier
}

/**
 * The [ColumnScope] one [Column] hands its content. What a child declares to it goes onto that child's
 * own modifier, so the scope holds nothing itself and every column shares this one.
 */
internal object ColumnScopeImpl : ColumnScope {
    override fun SwingModifier.weight(
        weight: Float,
        fill: Boolean,
    ): SwingModifier = this then WeightElement(weightPlacement(weight, fill))

    override fun SwingModifier.align(alignment: Alignment.Horizontal): SwingModifier =
        this then AlignElement(HorizontalAxisAlignment(alignment))

    override fun SwingModifier.fillWidth(): SwingModifier = this then FillElement
}
