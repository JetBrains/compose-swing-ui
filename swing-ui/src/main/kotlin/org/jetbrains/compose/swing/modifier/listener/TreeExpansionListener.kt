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
 * @param onExpansionChange receives the event, whose `path` is the node that opened or closed; it is
 *   read when the event fires.
 * @return this chain with the expansion callback declared on it.
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
 * @param onTreeExpanded runs once the node's children are showing.
 * @param onTreeCollapsed runs once they are hidden; the subtree keeps the expansion it had, and shows
 *   it again when the node reopens.
 * @return this chain with the expansion callbacks declared on it.
 * @see javax.swing.JTree.addTreeExpansionListener
 */
public fun SwingModifier.treeExpansionListener(
    onTreeExpanded: (TreeExpansionEvent) -> Unit = UNDECLARED,
    onTreeCollapsed: (TreeExpansionEvent) -> Unit = UNDECLARED,
): SwingModifier {
    requireAnyDeclared("treeExpansionListener", declared(onTreeExpanded) || declared(onTreeCollapsed))
    return listener(TreeExpansionCallbacks(onTreeExpanded, onTreeCollapsed), TREE_EXPANSION_CALLBACKS)
}

/**
 * Attaches a [TreeExpansionListener]
 * (`addTreeExpansionListener`/`removeTreeExpansionListener`). Requires a [javax.swing.JTree] target.
 *
 * @param listener attached by identity, so a remembered instance stays put while a fresh one on every
 *   pass is detached and re-added.
 * @return this chain with the expansion listener declared on it.
 * @see javax.swing.JTree.addTreeExpansionListener
 */
public fun SwingModifier.treeExpansionListener(listener: TreeExpansionListener): SwingModifier =
    listener(listener, TREE_EXPANSION)

/** The lambdas [treeExpansionListener] was declared with, as one value the built listener reads. */
private class TreeExpansionCallbacks(
    val onTreeExpanded: (TreeExpansionEvent) -> Unit,
    val onTreeCollapsed: (TreeExpansionEvent) -> Unit,
)

private val TREE_EXPANSION =
    ListenerRegistration<SwingJTree, TreeExpansionListener>(
        SwingJTree::addTreeExpansionListener,
        SwingJTree::removeTreeExpansionListener,
    )

private val TREE_EXPANSION_CALLBACKS =
    CallbackRegistration<SwingJTree, TreeExpansionCallbacks, TreeExpansionListener>(
        adapter = { current ->
            object : TreeExpansionListener {
                override fun treeExpanded(event: TreeExpansionEvent): Unit = current().onTreeExpanded(event)

                override fun treeCollapsed(event: TreeExpansionEvent): Unit = current().onTreeCollapsed(event)
            }
        },
        registration = TREE_EXPANSION,
    )
