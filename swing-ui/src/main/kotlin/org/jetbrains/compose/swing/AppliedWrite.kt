package org.jetbrains.compose.swing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import javax.swing.event.TableColumnModelEvent
import javax.swing.event.TableColumnModelListener
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.event.TreeSelectionListener

/**
 * A reentrancy guard marking the wrapper's own writes to its widget, so a listener can tell them from
 * the user's.
 *
 * Runs on the event dispatch thread.
 */
internal class AppliedWrite {
    /**
     * How many nested [write]s are in flight. A widget raises its event from inside the write that
     * provokes it, before the write returns, so an event arriving while this is above zero is the
     * wrapper's own doing. Counted rather than flagged, so a write nested inside another is one stretch
     * rather than two.
     */
    private var writeDepth: Int = 0

    /**
     * Whether a [write] of this wrapper's own is currently in flight - the events it raises are the
     * wrapper's doing, not the user's. A listener narrowed to the user's own changes rather than compared
     * against a mirrored value reads this directly instead.
     */
    val isWriting: Boolean get() = writeDepth > 0

    /**
     * Runs [block] as the wrapper's own write to its widget, so the events it raises are recognizable as
     * such rather than as something the user did.
     *
     * A write that cannot leave the widget holding the declaration - one narrower than it, or a structural
     * change with a side effect on the property a declaration governs - still belongs here: [isWriting]
     * marks it as the wrapper's regardless of what the widget is left holding. A write that throws still
     * lowers the count, so the wrapper's next write is not mistaken for a nested one.
     */
    fun write(block: () -> Unit) {
        writeDepth++
        try {
            block()
        } finally {
            writeDepth--
        }
    }
}

/** Remembers the [AppliedWrite] a component marks its own writes to its widget through. */
@Composable
internal fun rememberAppliedWrite(): AppliedWrite = remember { AppliedWrite() }

/** [listener], narrowed to the selection changes the user made. */
internal fun AppliedWrite.userOnly(listener: ListSelectionListener): ListSelectionListener =
    ListSelectionListener { event -> if (!isWriting) listener.valueChanged(event) }

/** [listener], narrowed to the selection changes the user made. */
internal fun AppliedWrite.userOnly(listener: TreeSelectionListener): TreeSelectionListener =
    TreeSelectionListener { event -> if (!isWriting) listener.valueChanged(event) }

/** [listener], narrowed to the expansions and collapses the user made. */
internal fun AppliedWrite.userOnly(listener: TreeExpansionListener): TreeExpansionListener =
    object : TreeExpansionListener {
        override fun treeExpanded(event: TreeExpansionEvent) {
            if (!isWriting) listener.treeExpanded(event)
        }

        override fun treeCollapsed(event: TreeExpansionEvent) {
            if (!isWriting) listener.treeCollapsed(event)
        }
    }

/** [listener], narrowed to the column reorders and resizes the user made. */
internal fun AppliedWrite.userOnly(listener: TableColumnModelListener): TableColumnModelListener =
    object : TableColumnModelListener {
        override fun columnAdded(event: TableColumnModelEvent) {
            if (!isWriting) listener.columnAdded(event)
        }

        override fun columnRemoved(event: TableColumnModelEvent) {
            if (!isWriting) listener.columnRemoved(event)
        }

        override fun columnMoved(event: TableColumnModelEvent) {
            if (!isWriting) listener.columnMoved(event)
        }

        override fun columnMarginChanged(event: ChangeEvent) {
            if (!isWriting) listener.columnMarginChanged(event)
        }

        override fun columnSelectionChanged(event: ListSelectionEvent) {
            if (!isWriting) listener.columnSelectionChanged(event)
        }
    }

/** [listener], narrowed to the changes the user made. */
internal fun AppliedWrite.userOnly(listener: ChangeListener): ChangeListener =
    ChangeListener { event -> if (!isWriting) listener.stateChanged(event) }
