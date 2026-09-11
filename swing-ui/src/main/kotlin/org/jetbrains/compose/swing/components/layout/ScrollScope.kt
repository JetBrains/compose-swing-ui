package org.jetbrains.compose.swing.components.layout

/**
 * The receiver of a [ScrollState.scroll] block, through which the block moves the pane.
 *
 * A block owns the position for as long as it runs, and [scrollTo] is the only move that is the block's
 * own: every other move of the position ends the block instead, as [ScrollState.scroll] describes.
 */
public sealed interface ScrollScope {
    /**
     * Moves the pane to [x], [y] in view coordinates, coerced to what the content reaches.
     *
     * The coercion is against the metrics of this moment, so a block running before the content has been
     * laid out writes `0` and follows the content as it grows. While no pane renders the state there is
     * no content to coerce against, and the position is taken verbatim.
     *
     * @param x the view coordinate to show at the viewport's left edge, coerced into `0..maxX`.
     * @param y the view coordinate to show at the viewport's top edge, coerced into `0..maxY`.
     * @see javax.swing.JViewport.setViewPosition
     */
    public fun scrollTo(
        x: Int,
        y: Int,
    )
}

/**
 * The [ScrollScope] one [ScrollState] hands every block it runs. It holds nothing of its own: a move made
 * through it is a move of that state's position.
 */
internal class ScrollScopeImpl(
    private val state: ScrollState,
) : ScrollScope {
    override fun scrollTo(
        x: Int,
        y: Int,
    ): Unit = state.scrollTo(x, y)
}
