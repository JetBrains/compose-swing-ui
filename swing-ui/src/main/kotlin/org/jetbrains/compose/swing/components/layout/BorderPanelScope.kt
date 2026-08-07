package org.jetbrains.compose.swing.components.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import java.awt.BorderLayout

/**
 * The receiver of a [BorderPanel]'s content, through which a child declares which region of that panel
 * it occupies.
 *
 * Children are written plainly; the region a child names here rides along on its `modifier`:
 *
 * ```
 * BorderPanel {
 *     Toolbar(modifier = SwingModifier.north())
 *     Editor()
 *     StatusBar(modifier = SwingModifier.south())
 * }
 * ```
 *
 * Two families of region are available:
 *  - absolute compass: [north], [south], [east], [west], [center];
 *  - orientation-aware: [pageStart], [pageEnd], [lineStart], [lineEnd], resolved against the panel's
 *    `ComponentOrientation` (leading is the left edge under left-to-right, the right edge under
 *    right-to-left).
 *
 * Prefer one family for a given edge: pairing, e.g., [north] with [pageStart] attaches two children and
 * the orientation-aware one is laid out at the top. [center] is shared by both families.
 *
 * A region holds one child. The last region named in a chain is the one that child occupies. Where two
 * children name the same region, the second to be registered takes it and the panel lays nothing out
 * for the first.
 *
 * @see java.awt.BorderLayout
 */
public sealed interface BorderPanelScope {
    /**
     * Places the child across the top of the panel, at the height it prefers and the panel's full
     * width ([BorderLayout.NORTH]).
     *
     * @see java.awt.BorderLayout.NORTH
     */
    public fun SwingModifier.north(): SwingModifier

    /**
     * Places the child across the bottom of the panel, at the height it prefers and the panel's full
     * width ([BorderLayout.SOUTH]).
     *
     * @see java.awt.BorderLayout.SOUTH
     */
    public fun SwingModifier.south(): SwingModifier

    /**
     * Places the child down the right side of the panel, at the width it prefers and the height the
     * top and bottom regions leave ([BorderLayout.EAST]).
     *
     * @see java.awt.BorderLayout.EAST
     */
    public fun SwingModifier.east(): SwingModifier

    /**
     * Places the child down the left side of the panel, at the width it prefers and the height the top
     * and bottom regions leave ([BorderLayout.WEST]).
     *
     * @see java.awt.BorderLayout.WEST
     */
    public fun SwingModifier.west(): SwingModifier

    /**
     * Places the child in the middle of the panel, filling everything the edge regions leave
     * ([BorderLayout.CENTER]).
     *
     * @see java.awt.BorderLayout.CENTER
     */
    public fun SwingModifier.center(): SwingModifier

    /**
     * Places the child across the top of the panel, orientation-aware; wins the top edge over [north]
     * ([BorderLayout.PAGE_START]).
     *
     * @see java.awt.BorderLayout.PAGE_START
     */
    public fun SwingModifier.pageStart(): SwingModifier

    /**
     * Places the child across the bottom of the panel, orientation-aware; wins the bottom edge over
     * [south] ([BorderLayout.PAGE_END]).
     *
     * @see java.awt.BorderLayout.PAGE_END
     */
    public fun SwingModifier.pageEnd(): SwingModifier

    /**
     * Places the child down the leading side of the panel (left in LTR, right in RTL); wins that side
     * over [west]/[east] ([BorderLayout.LINE_START]).
     *
     * @see java.awt.BorderLayout.LINE_START
     */
    public fun SwingModifier.lineStart(): SwingModifier

    /**
     * Places the child down the trailing side of the panel (right in LTR, left in RTL); wins that side
     * over [east]/[west] ([BorderLayout.LINE_END]).
     *
     * @see java.awt.BorderLayout.LINE_END
     */
    public fun SwingModifier.lineEnd(): SwingModifier
}

/**
 * The [BorderPanelScope] every [BorderPanel] hands its content. A region builder appends the matching
 * [BorderLayout] constraint to the child's own chain and holds nothing of the panel it was called
 * under, so one instance serves them all.
 */
internal object BorderPanelScopeImpl : BorderPanelScope {
    override fun SwingModifier.north(): SwingModifier = this.layoutConstraint(BorderLayout.NORTH)

    override fun SwingModifier.south(): SwingModifier = this.layoutConstraint(BorderLayout.SOUTH)

    override fun SwingModifier.east(): SwingModifier = this.layoutConstraint(BorderLayout.EAST)

    override fun SwingModifier.west(): SwingModifier = this.layoutConstraint(BorderLayout.WEST)

    override fun SwingModifier.center(): SwingModifier = this.layoutConstraint(BorderLayout.CENTER)

    override fun SwingModifier.pageStart(): SwingModifier = this.layoutConstraint(BorderLayout.PAGE_START)

    override fun SwingModifier.pageEnd(): SwingModifier = this.layoutConstraint(BorderLayout.PAGE_END)

    override fun SwingModifier.lineStart(): SwingModifier = this.layoutConstraint(BorderLayout.LINE_START)

    override fun SwingModifier.lineEnd(): SwingModifier = this.layoutConstraint(BorderLayout.LINE_END)
}
