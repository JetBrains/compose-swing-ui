package org.jetbrains.compose.swing.foundation.layout

import java.awt.Component
import java.awt.Dimension
import java.util.IdentityHashMap

/**
 * The extents measured since a container was last invalidated, so a pass with nothing to re-measure asks
 * a child nothing.
 *
 * Keyed by the child itself. A stale entry is then at worst that child's own previous extent, and an
 * unknown child is measured; keyed by position, a stale entry would be another child's extent.
 *
 * One of these belongs to one layout manager, and so to the one container that manager lays out.
 */
internal class PreferredSizeCache {
    private val measured = IdentityHashMap<Component, Dimension>()

    /**
     * The extent [child] prefers, measured once and reused until the next invalidation.
     *
     * The measurement waits for the first read that needs it: a child placed at an extent of its
     * container's is placed without its preferred size being read at all.
     *
     * A child with no peer is measured afresh every time. A child that resizes reaches its manager by
     * invalidating its container, and AWT carries that up only to a container `isValid` reports true for,
     * which requires a peer. Without one no invalidation ever arrives, and every entry here would be
     * stale for good.
     */
    fun preferredSizeOf(child: Component): Dimension =
        if (child.isDisplayable) measured.getOrPut(child) { child.preferredSize } else child.preferredSize

    /** Gives up everything measured, for a container whose layout has been invalidated. */
    fun invalidate() {
        measured.clear()
    }

    /** Gives up the extent measured for [child], for a child leaving the container. */
    fun forget(child: Component) {
        measured.remove(child)
    }
}
