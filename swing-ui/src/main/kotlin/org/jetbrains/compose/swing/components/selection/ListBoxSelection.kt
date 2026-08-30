package org.jetbrains.compose.swing.components.selection

import org.jetbrains.compose.swing.node.MirrorState
import javax.swing.JList
import javax.swing.event.ListSelectionListener

/**
 * Adapts an `onSelectionChange` lambda into the raw [ListSelectionListener] the model-agnostic
 * overloads delegate to, reporting one settled selection per change. A JList re-fires its selection
 * event with the list itself as the source, so the settled selection is read back from that list.
 */
internal fun settledSelectionListener(onSelectionChange: (Set<Int>) -> Unit): ListSelectionListener =
    ListSelectionListener { event ->
        if (!event.valueIsAdjusting) onSelectionChange((event.source as JList<*>).selectedIndices.toSet())
    }

/**
 * Gives the list new content through [install], keeping the rows [declared] names selected - or, where the
 * caller declared nothing, the rows the user had - and reporting to [target] the rows the new content is
 * too short to hold. See [installNarrowing].
 */
internal fun JList<*>.installContent(
    mirror: MirrorState<Set<Int>?>,
    declared: Set<Int>?,
    target: ListSelectionListener,
    install: () -> Unit,
): Unit =
    mirror.installNarrowing(
        declared = declared,
        selection = { selectedIndices.toSet() },
        apply = { rows -> applySelection(this, rows) },
        report = { lost -> reportLostRows(target, lost) },
        install = install,
    )

/**
 * Re-applies [indices] as the list's selection, dropping any index the current item count no longer
 * covers. A selection that already matches is left alone, so a recomposition that changed nothing
 * touches the list's selection model not at all, and a `null` declaration leaves it alone entirely. See
 * [selectExactly].
 */
internal fun applySelection(
    list: JList<*>,
    indices: Set<Int>?,
) {
    if (indices == null) return
    val itemCount = list.model.size
    val valid = indices.filterTo(sortedSetOf()) { it in 0 until itemCount }
    val standing = list.selectedIndices
    if (standing.holdsSelection(valid)) return
    list.selectionModel.selectExactly(standing, valid)
}
