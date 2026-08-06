package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import java.awt.BorderLayout

/**
 * Declarative regions of a [BorderPanel].
 *
 * Each region hosts a single composable. Declaring a region more than once replaces the previous
 * declaration - the last call wins.
 *
 * Two families of region are available:
 *  - absolute compass: [north], [south], [east], [west], [center];
 *  - orientation-aware: [pageStart], [pageEnd], [lineStart], [lineEnd], resolved against the panel's
 *    `ComponentOrientation` (leading is the left edge under left-to-right, the right edge under
 *    right-to-left).
 *
 * Prefer one family for a given edge: pairing, e.g., [north] with [pageStart] attaches two children
 * and the orientation-aware one is laid out at the top. [center] is shared by both families.
 *
 * @see java.awt.BorderLayout
 */
public sealed interface BorderPanelScope {
    /**
     * Top region ([BorderLayout.NORTH]).
     *
     * @see java.awt.BorderLayout.NORTH
     */
    public fun north(block: @Composable () -> Unit)

    /**
     * Bottom region ([BorderLayout.SOUTH]).
     *
     * @see java.awt.BorderLayout.SOUTH
     */
    public fun south(block: @Composable () -> Unit)

    /**
     * Right region ([BorderLayout.EAST]).
     *
     * @see java.awt.BorderLayout.EAST
     */
    public fun east(block: @Composable () -> Unit)

    /**
     * Left region ([BorderLayout.WEST]).
     *
     * @see java.awt.BorderLayout.WEST
     */
    public fun west(block: @Composable () -> Unit)

    /**
     * Center region, filling the space left by the edge regions ([BorderLayout.CENTER]).
     *
     * @see java.awt.BorderLayout.CENTER
     */
    public fun center(block: @Composable () -> Unit)

    /**
     * Orientation-aware top region; wins the top edge over [north] ([BorderLayout.PAGE_START]).
     *
     * @see java.awt.BorderLayout.PAGE_START
     */
    public fun pageStart(block: @Composable () -> Unit)

    /**
     * Orientation-aware bottom region; wins the bottom edge over [south] ([BorderLayout.PAGE_END]).
     *
     * @see java.awt.BorderLayout.PAGE_END
     */
    public fun pageEnd(block: @Composable () -> Unit)

    /**
     * Leading side region (left in LTR, right in RTL); wins over [west]/[east] ([BorderLayout.LINE_START]).
     *
     * @see java.awt.BorderLayout.LINE_START
     */
    public fun lineStart(block: @Composable () -> Unit)

    /**
     * Trailing side region (right in LTR, left in RTL); wins over [east]/[west] ([BorderLayout.LINE_END]).
     *
     * @see java.awt.BorderLayout.LINE_END
     */
    public fun lineEnd(block: @Composable () -> Unit)
}
