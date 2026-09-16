package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.layout.LayoutScopeMarker
import org.jetbrains.compose.swing.modifier.SwingModifier

/**
 * The receiver of a [Box]'s content, through which a child declares its own placement in that box.
 *
 * Children are written plainly; what a child declares here rides along on its `modifier`:
 *
 * ```
 * Box {
 *     ProgressBar(value = 40, modifier = SwingModifier.matchParentSize())
 *     Label(text = "Badge", modifier = SwingModifier.align(Alignment.CenterEnd))
 * }
 * ```
 */
@LayoutScopeMarker
public sealed interface BoxScope : ConstrainedScope {
    /**
     * Places the child at [alignment] on both axes, in place of the box's own `contentAlignment`.
     *
     * @param alignment where the child sits in the box
     * @return this modifier with the child's alignment declared on it.
     */
    public fun SwingModifier.align(alignment: Alignment): SwingModifier

    /**
     * Gives the child the box's whole extent in place of the extent it prefers, up to an explicit
     * `maximumSize` where it declares one.
     *
     * The box is sized to the children that do not match it, so a child declaring this takes whatever
     * size the others settle, and a box whose children all match it asks for its insets alone. Where a
     * `maximumSize` holds the child below the box, its own [align] or the box's alignment places it in
     * what that leaves free.
     *
     * @return this modifier with the child's match of the box's extent declared on it.
     */
    public fun SwingModifier.matchParentSize(): SwingModifier
}

/**
 * The [BoxScope] one [Box] hands its content. What a child declares to it goes onto that child's own
 * modifier, so the scope holds nothing itself and every box shares this one.
 */
@PublishedApi
internal object BoxScopeInstance : BoxScope {
    override fun SwingModifier.align(alignment: Alignment): SwingModifier = this then BoxAlignElement(alignment)

    override fun SwingModifier.matchParentSize(): SwingModifier = this then BoxMatchParentSizeElement
}
