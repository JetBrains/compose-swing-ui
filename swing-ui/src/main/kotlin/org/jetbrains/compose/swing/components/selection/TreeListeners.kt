package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.compose.swing.core.dispatchToCaller
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.treeExpansionListener
import org.jetbrains.compose.swing.modifier.listener.treeSelectionListener
import org.jetbrains.compose.swing.modifier.listener.treeWillExpandListener
import javax.swing.JTree
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.event.TreeSelectionListener
import javax.swing.event.TreeWillExpandListener
import javax.swing.tree.ExpandVetoException

/**
 * Remembers a [TreeSelectionListener] that reports the tree's selection back through [onSelectionChange]
 * as index paths. A tree selection event's source is the `JTree` itself, so the selection is read back
 * from its model.
 */
@Composable
internal fun rememberSelectionListener(onSelectionChange: (Set<List<Int>>) -> Unit): TreeSelectionListener {
    val callback = rememberUpdatedState(onSelectionChange)
    return remember {
        TreeSelectionListener { event ->
            val tree = event.source as JTree
            callback.value(readSelection(tree, tree.model))
        }
    }
}

/**
 * Remembers a [TreeExpansionListener] that reports every expanded node back through [onExpansionChange]
 * as index paths, after each expand and each collapse. An expansion event's source is the `JTree`
 * itself, so the expansion is read back from it.
 */
@Composable
internal fun rememberExpansionListener(onExpansionChange: (Set<List<Int>>) -> Unit): TreeExpansionListener {
    val callback = rememberUpdatedState(onExpansionChange)
    return remember {
        object : TreeExpansionListener {
            override fun treeExpanded(event: TreeExpansionEvent): Unit = report(event)

            override fun treeCollapsed(event: TreeExpansionEvent): Unit = report(event)

            private fun report(event: TreeExpansionEvent) {
                val tree = event.source as JTree
                callback.value(readExpansion(tree, tree.model))
            }
        }
    }
}

/**
 * Remembers a [TreeWillExpandListener] that asks [onWillExpand] whether the node about to open may, and
 * answers a refusal with the [ExpandVetoException] a `JTree` reads as one. A collapse is announced through
 * the same listener and is never asked about: closing a node loads nothing and reveals nothing.
 *
 * A listener is handed back only while a callback is declared, so a tree without one installs none.
 */
@Composable
internal fun <T> rememberWillExpandListener(
    onWillExpand: ((value: T, path: List<Int>) -> Boolean)?,
): TreeWillExpandListener? {
    val callback = rememberUpdatedState(onWillExpand)
    val listener =
        remember {
            object : TreeWillExpandListener {
                override fun treeWillExpand(event: TreeExpansionEvent) {
                    if (!allowsExpansion(callback.value, event)) throw ExpandVetoException(event)
                }

                override fun treeWillCollapse(event: TreeExpansionEvent): Unit = Unit
            }
        }
    return listener.takeIf { onWillExpand != null }
}

/**
 * Whether [onWillExpand] lets the node [event] announces open, asked with the value that node stands for
 * and its index path.
 *
 * A node opens on the wrapper's own write as well as on the user's click - applying a declared expansion
 * is such a write - so the answer is reached from inside a pass of the composition too, and a callback
 * that fails there would end it. A failure is contained here instead and answers nothing, which lets the
 * expansion through: refusing one is what returning `false` says.
 */
private fun <T> allowsExpansion(
    onWillExpand: ((value: T, path: List<Int>) -> Boolean)?,
    event: TreeExpansionEvent,
): Boolean {
    val callback = onWillExpand ?: return true
    val tree = event.source as JTree
    var allowed = true
    dispatchToCaller { allowed = callback(valueAt(event.path), pathToIndices(tree.model, event.path)) }
    return allowed
}

/**
 * The tree's user-facing listeners as one chain: the selection listener, the expansion listener, and the
 * will-expand listener where one is declared. The first two feed their mirror on every user change
 * regardless of whether the caller declared a raw listener of its own to forward to.
 */
internal fun SwingModifier.treeListeners(
    selectionListener: TreeSelectionListener,
    expansionListener: TreeExpansionListener,
    willExpandListener: TreeWillExpandListener?,
): SwingModifier {
    val listeners = treeSelectionListener(selectionListener).treeExpansionListener(expansionListener)
    return if (willExpandListener == null) listeners else listeners.treeWillExpandListener(willExpandListener)
}
