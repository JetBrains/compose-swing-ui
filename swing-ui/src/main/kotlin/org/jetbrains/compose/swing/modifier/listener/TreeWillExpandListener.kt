@file:JvmMultifileClass
@file:JvmName("ListenerModifierKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.event.TreeWillExpandListener
import javax.swing.JTree as SwingJTree

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
