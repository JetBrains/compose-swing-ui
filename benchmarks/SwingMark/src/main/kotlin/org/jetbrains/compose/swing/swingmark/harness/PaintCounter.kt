package org.jetbrains.compose.swing.swingmark.harness

import javax.swing.JComponent
import javax.swing.RepaintManager

/** The name of the span one flush of the dirty regions is recorded under. */
private const val PAINT_SPAN: String = "paint"

/**
 * Counts the paints the tests cause, for the figure SwingMark prints beside each time, and names each of
 * them in a trace.
 *
 * SwingMark counts paints by subclassing the widget under test. A composed test builds no widget of its
 * own, so this counts at the repaint manager instead: one per flush of the dirty regions, which is one
 * per painted frame rather than one per widget painted. The span covers that same flush, which is what
 * lets a reader tell the time a change spends painting from the time it spends anywhere else - painting
 * is the largest stretch of a run that the library's own spans say nothing about, because it is the
 * toolkit's work and not the pipeline's. Both arms paint through this manager, so both are named alike.
 *
 * The two figures agree where one widget repaints itself, and not where a viewport scrolls, because a
 * viewport paints itself as it scrolls rather than marking a region dirty. `RepaintManager.paint` is
 * package-private and a scroll marks nothing on the window either, so no figure here reaches that path.
 *
 * **Nothing here compares two arms on a screen that scrolls.** A viewport copies what it has already
 * painted and paints only the strip the copy uncovered, so a scroll costs real time and leaves every
 * figure below unmoved. Measured on the tree screen, the two arms agree on layouts exactly and on dirty
 * pixels to within a third of a percent while one of them spends twice as long inside
 * `scrollRectToVisible`. Read these figures as what a screen repaints, never as what it costs.
 */
internal object PaintCounter : RepaintManager() {
    @Volatile
    private var count: Int = 0

    @Volatile
    private var area: Long = 0

    @Volatile
    private var invalidations: Int = 0

    val paints: Int get() = count

    /**
     * The pixels the tests have marked dirty, summed over every region.
     *
     * Both arms mark their regions through this manager, and this counts them the same way for each, so
     * unlike [paints] - which each arm raises from its own instrumentation - this figure compares across
     * arms. It is what tells an arm that paints more often from one that paints more at a time.
     *
     * It sees what a screen repaints and not what it scrolls, so on a screen with a scroll pane in it
     * two arms can agree here and differ in what they cost.
     */
    val dirtyArea: Long get() = area

    /**
     * The components the tests have handed this manager to lay out again.
     *
     * A flush lays out what it has been given before it paints anything, so this is the part of a paint
     * that is not painting. Both arms hand them over through this manager, so this compares across arms.
     */
    val layouts: Int get() = invalidations

    override fun addInvalidComponent(component: JComponent) {
        invalidations++
        super.addInvalidComponent(component)
    }

    override fun addDirtyRegion(
        component: JComponent,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
    ) {
        if (w > 0 && h > 0) area += w.toLong() * h
        super.addDirtyRegion(component, x, y, w, h)
    }

    override fun paintDirtyRegions() {
        count++
        traceHarness(PAINT_SPAN) { super.paintDirtyRegions() }
    }

    /** Takes over painting for this application context. Must be called on the event dispatch thread. */
    fun install() {
        setCurrentManager(this)
    }

    fun reset() {
        count = 0
        area = 0
        invalidations = 0
    }
}
