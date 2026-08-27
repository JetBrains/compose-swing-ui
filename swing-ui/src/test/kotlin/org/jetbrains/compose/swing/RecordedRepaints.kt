package org.jetbrains.compose.swing

import javax.swing.JComponent
import javax.swing.RepaintManager
import javax.swing.SwingUtilities

/**
 * A [RepaintManager] that stands in for the real one on the one property a settling test measures: a
 * repaint is asked for while the widget is still moving, and served from a later event, so the value the
 * user is shown is the value the widget holds when that event runs rather than when the change was made.
 *
 * [value] is read as each serving event runs, into [served]. Requests arriving before that event runs
 * are served by it together, the way Swing coalesces the repaints one gesture makes, so [served] holds
 * one entry per paint the user would see rather than one per request. [paint] is what the serving event
 * does besides recording.
 *
 * Install it with [RepaintManager.setCurrentManager], and put the standing manager back afterwards.
 */
internal class RecordedRepaints(
    private val value: () -> Any?,
    private val paint: (JComponent) -> Unit,
) : RepaintManager() {
    /** What [value] answered at each repaint served, in the order the paints ran. */
    val served: MutableList<Any?> = mutableListOf()

    private var queued = false

    override fun addDirtyRegion(
        component: JComponent,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        // A component with no area asks for no repaint at all - the real manager returns on exactly this
        // check - so recording one would count a paint it never serves and the user never sees. A table's
        // header is one such component: a table standing outside a scroll pane carries it unsized, and it
        // asks for a repaint whenever the sort order changes.
        if (component.width <= 0 || component.height <= 0) return
        if (queued) return
        queued = true
        SwingUtilities.invokeLater {
            queued = false
            served += value()
            paint(component)
        }
    }
}
