@file:JvmMultifileClass
@file:JvmName("ListenerModifierKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeWillExpandListener
import javax.swing.tree.ExpandVetoException
import javax.swing.JTree as SwingJTree

/**
 * Asks [onWillChange] before a node of the tree opens or closes whether it may: answering false leaves
 * the node as it was and no expansion event follows. Requires a [javax.swing.JTree] target.
 *
 * [onWillChange] is read when the event fires, so writing a fresh lambda on every recomposition
 * registers nothing again.
 *
 * @see javax.swing.JTree.addTreeWillExpandListener
 */
public fun SwingModifier.treeWillExpandListener(onWillChange: (TreeExpansionEvent) -> Boolean): SwingModifier =
    treeWillExpandListener(
        onWillExpand = onWillChange,
        onWillCollapse = onWillChange,
    )

/**
 * Asks [onWillExpand] before a node opens, and [onWillCollapse] before one closes, whether the change
 * may happen: answering false leaves the node as it was and no expansion event follows. Requires a
 * [javax.swing.JTree] target. A direction left undeclared allows the change.
 *
 * Each lambda is read when its event fires, so writing a fresh one on every recomposition registers
 * nothing again.
 *
 * Declaring none at all is refused.
 *
 * @see javax.swing.JTree.addTreeWillExpandListener
 */
public fun SwingModifier.treeWillExpandListener(
    onWillExpand: (TreeExpansionEvent) -> Boolean = UNDECLARED_ANSWER,
    onWillCollapse: (TreeExpansionEvent) -> Boolean = UNDECLARED_ANSWER,
): SwingModifier {
    requireAnyDeclared("treeWillExpandListener", declared(onWillExpand) || declared(onWillCollapse))
    return liveCallbackListener<SwingJTree, TreeWillExpandCallbacks, TreeWillExpandListener>(
        callback = TreeWillExpandCallbacks(onWillExpand, onWillCollapse),
        adapter = { current ->
            object : TreeWillExpandListener {
                override fun treeWillExpand(event: TreeExpansionEvent) {
                    if (!current().onWillExpand(event)) throw ExpandVetoException(event)
                }

                override fun treeWillCollapse(event: TreeExpansionEvent) {
                    if (!current().onWillCollapse(event)) throw ExpandVetoException(event)
                }
            }
        },
        attach = { component, listener -> component.addTreeWillExpandListener(listener) },
        detach = { component, listener -> component.removeTreeWillExpandListener(listener) },
    )
}

/**
 * Attaches a [TreeWillExpandListener]
 * (`addTreeWillExpandListener`/`removeTreeWillExpandListener`), notified before a node opens or closes.
 * Requires a [javax.swing.JTree] target.
 *
 * The listener answers a change it refuses with a [javax.swing.tree.ExpandVetoException], which is what
 * a `JTree` reads as a veto: the node is left as it was and no expansion event follows.
 *
 * @see javax.swing.JTree.addTreeWillExpandListener
 */
public fun SwingModifier.treeWillExpandListener(listener: TreeWillExpandListener): SwingModifier =
    listener<SwingJTree, TreeWillExpandListener>(
        listener,
        SwingJTree::addTreeWillExpandListener,
        SwingJTree::removeTreeWillExpandListener,
    )

/** The lambdas [treeWillExpandListener] was declared with, as one value the built listener reads. */
private class TreeWillExpandCallbacks(
    val onWillExpand: (TreeExpansionEvent) -> Boolean,
    val onWillCollapse: (TreeExpansionEvent) -> Boolean,
)
