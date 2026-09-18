package org.jetbrains.compose.swing.components.selection

import javax.swing.JList
import javax.swing.JTree

/**
 * The count a widget asks the viewport scrolling it to make room for, written onto the widget together
 * with the layout pass that acts on the new answer.
 *
 * Neither setter asks for that pass. `JTree.setVisibleRowCount` marks the tree invalid and schedules
 * nothing, and no look and feel listens for the count; `JList.setVisibleRowCount` only fires a property
 * change, which a look and feel acts on where the list wraps its rows - never in the vertical
 * orientation a list carries by default. Without the pass a scroll pane keeps its old room.
 */
internal fun JTree.applyVisibleRowCount(count: Int) {
    visibleRowCount = count
    revalidate()
}

/** Writes the count onto the list and asks for the pass that acts on it; see the tree's own above. */
internal fun JList<*>.applyVisibleRowCount(count: Int) {
    visibleRowCount = count
    revalidate()
}
