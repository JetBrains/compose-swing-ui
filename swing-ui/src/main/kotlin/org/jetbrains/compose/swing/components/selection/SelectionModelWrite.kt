package org.jetbrains.compose.swing.components.selection

import org.jetbrains.compose.swing.util.forEachInterval
import java.util.SortedSet
import javax.swing.DefaultListSelectionModel
import javax.swing.ListSelectionModel

/**
 * Whether the indices this array holds are exactly [selection].
 *
 * A list and a table both report their selected indices in increasing order, and [selection] is sorted, so
 * the two are walked together rather than gathered into a collection to be compared as one. A declared
 * selection is re-asserted on every pass, and that comparison is the whole of what a pass changing nothing
 * about it comes down to.
 */
internal fun IntArray.holdsSelection(selection: SortedSet<Int>): Boolean {
    if (size != selection.size) return false
    // Both sides are in increasing order and now of equal length, so the nth row of the set belongs against
    // the nth index of this array - which is what the counter names, advanced once per row visited.
    var index = 0
    return selection.all { row -> this[index++] == row }
}

/**
 * Leaves the model selecting exactly [selection], writing only the rows that separate it from [standing] -
 * the rows the model holds now, in increasing order.
 *
 * The rows that leave the selection are removed and the rows that join it are added, both a run of adjacent
 * rows at a time, and a row on both sides is not written at all. What that spares is the repaint: a viewport
 * flushes the pending dirty region of its view before it copies a scrolled band, so the region a selection
 * change leaves behind is the region a scroll has to paint. Emptying the selection and putting it back marks
 * every row of both the old and the new selection dirty; writing the difference marks the rows that actually
 * changed.
 *
 * The whole update is one adjusting run, so the model publishes a single settled selection however many
 * intervals it took.
 */
internal fun ListSelectionModel.selectExactly(
    standing: IntArray,
    selection: SortedSet<Int>,
) {
    valueIsAdjusting = true
    try {
        if (selection.isEmpty()) {
            // Clearing the whole selection in one step leaves the lead and the anchor where they are, which is
            // what a user is left with when their own selection is emptied.
            clearSelection()
        } else {
            val leadBefore = leadSelectionIndex
            val standard = this as? DefaultListSelectionModel
            val announced = standard?.isLeadAnchorNotificationEnabled ?: false
            // Every interval written moves the anchor and the lead onto itself and says so, and a model
            // reports the rows it marked as the one range that spans them - so a single row joining a
            // selection at the far end of it announces every row between the two. The anchor is silenced for
            // the whole update and the lead put back where it was, leaving the move it really makes to be
            // announced once, below.
            standard?.isLeadAnchorNotificationEnabled = false
            standing.forEachInterval({ it !in selection }) { start, end ->
                removeSelectionInterval(start, end)
            }
            selection.forEachInterval({ standing.binarySearch(it) < 0 }) { start, end ->
                addSelectionInterval(start, end)
            }
            val lastIntervalEnd = anchorLastInterval(selection, standard, leadBefore)
            standard?.isLeadAnchorNotificationEnabled = announced
            standard?.moveLeadSelectionIndex(lastIntervalEnd)
        }
    } finally {
        valueIsAdjusting = false
    }
}

/**
 * Puts the anchor at the start of the last run of adjacent rows in [selection], and answers where that run
 * ends - the row the lead belongs on.
 *
 * A shift-click extends the selection from the anchor, so the anchor belongs on the run the caller
 * declared rather than on whichever rows happened to join. Writing the run as an interval is also what
 * settles which of its rows a mode too narrow to hold them all keeps, so it is written whatever the model
 * already holds. The anchor paints nothing, so a [standard] model has its lead put back to [leadBefore]
 * afterwards, leaving the lead's real move to be announced once. A model of another kind cannot separate
 * the two and announces what it announces.
 */
private fun ListSelectionModel.anchorLastInterval(
    selection: SortedSet<Int>,
    standard: DefaultListSelectionModel?,
    leadBefore: Int,
): Int {
    var start = selection.first()
    var end = start
    selection.forEachInterval { runStart, runEnd ->
        start = runStart
        end = runEnd
    }
    addSelectionInterval(start, end)
    // Silenced above, so the lead is wherever the run left it; putting it back leaves its real move to be
    // announced once, after the announcement is restored.
    standard?.moveLeadSelectionIndex(leadBefore)
    return end
}
