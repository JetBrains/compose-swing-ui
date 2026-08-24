@file:JvmMultifileClass
@file:JvmName("ListenerModifierKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.JTree as SwingJTree

/**
 * Runs [onExpansionChange] whenever a node of the tree opens or closes. Requires a
 * [javax.swing.JTree] target.
 *
 * [onExpansionChange] is read when the event fires, so writing a fresh lambda on every recomposition
 * registers nothing again.
 *
 * @see javax.swing.JTree.addTreeExpansionListener
 */
public fun SwingModifier.treeExpansionListener(onExpansionChange: (TreeExpansionEvent) -> Unit): SwingModifier =
    treeExpansionListener(
        onTreeExpanded = onExpansionChange,
        onTreeCollapsed = onExpansionChange,
    )

/**
 * Runs [onTreeExpanded] when a node opens and [onTreeCollapsed] when one closes. Requires a
 * [javax.swing.JTree] target. A direction left undeclared reports nowhere.
 *
 * Each lambda is read when its event fires, so writing a fresh one on every recomposition
 * registers nothing again.
 *
 * Declaring none at all is refused.
 *
 * @see javax.swing.JTree.addTreeExpansionListener
 */
public fun SwingModifier.treeExpansionListener(
    onTreeExpanded: (TreeExpansionEvent) -> Unit = UNDECLARED,
    onTreeCollapsed: (TreeExpansionEvent) -> Unit = UNDECLARED,
): SwingModifier {
    requireAnyDeclared("treeExpansionListener", declared(onTreeExpanded) || declared(onTreeCollapsed))
    return listener<SwingJTree, TreeExpansionCallbacks, TreeExpansionListener>(
        callback = TreeExpansionCallbacks(onTreeExpanded, onTreeCollapsed),
        adapter = { current ->
            object : TreeExpansionListener {
                override fun treeExpanded(event: TreeExpansionEvent): Unit = current().onTreeExpanded(event)

                override fun treeCollapsed(event: TreeExpansionEvent): Unit = current().onTreeCollapsed(event)
            }
        },
        attach = { component, listener -> component.addTreeExpansionListener(listener) },
        detach = { component, listener -> component.removeTreeExpansionListener(listener) },
    )
}

/**
 * Attaches a [TreeExpansionListener]
 * (`addTreeExpansionListener`/`removeTreeExpansionListener`). Requires a [javax.swing.JTree] target.
 *
 * @see javax.swing.JTree.addTreeExpansionListener
 */
public fun SwingModifier.treeExpansionListener(listener: TreeExpansionListener): SwingModifier =
    listener<SwingJTree, TreeExpansionListener>(
        listener,
        SwingJTree::addTreeExpansionListener,
        SwingJTree::removeTreeExpansionListener,
    )

/** The lambdas [treeExpansionListener] was declared with, as one value the built listener reads. */
private class TreeExpansionCallbacks(
    val onTreeExpanded: (TreeExpansionEvent) -> Unit,
    val onTreeCollapsed: (TreeExpansionEvent) -> Unit,
)
