package org.jetbrains.compose.swing.swingmark.harness

import javax.swing.RepaintManager

/**
 * Counts the paints the tests cause, for the figure SwingMark prints beside each time.
 *
 * SwingMark counts paints by subclassing the widget under test. A composed test builds no widget of its
 * own, so this counts at the repaint manager instead: one per flush of the dirty regions, which is one
 * per painted frame rather than one per widget painted.
 *
 * The two figures agree where one widget repaints itself, and not where a viewport scrolls, because a
 * viewport paints itself as it scrolls rather than marking a region dirty. `RepaintManager.paint` is
 * package-private, so that path cannot be counted from here.
 */
internal object PaintCounter : RepaintManager() {
    @Volatile
    private var count: Int = 0

    val paints: Int get() = count

    override fun paintDirtyRegions() {
        count++
        super.paintDirtyRegions()
    }

    /** Takes over painting for this application context. Must be called on the event dispatch thread. */
    fun install() {
        setCurrentManager(this)
    }

    fun reset() {
        count = 0
    }
}
