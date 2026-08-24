@file:JvmMultifileClass
@file:JvmName("ListenerModifierKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.JTree as SwingJTree

/**
 * Runs [onSelectionChange] whenever the tree's selection changes. Requires a [javax.swing.JTree] target.
 *
 * [onSelectionChange] is read when the event fires, so writing a fresh lambda on every recomposition
 * registers nothing again.
 *
 * @see javax.swing.JTree.addTreeSelectionListener
 */
public fun SwingModifier.treeSelectionListener(onSelectionChange: (TreeSelectionEvent) -> Unit): SwingModifier =
    listener(onSelectionChange, TREE_SELECTION_CALLBACKS)

/**
 * Attaches a [TreeSelectionListener]
 * (`addTreeSelectionListener`/`removeTreeSelectionListener`). Requires a [javax.swing.JTree] target.
 *
 * @see javax.swing.JTree.addTreeSelectionListener
 */
public fun SwingModifier.treeSelectionListener(listener: TreeSelectionListener): SwingModifier =
    listener(listener, TREE_SELECTION)

private val TREE_SELECTION =
    ListenerRegistration<SwingJTree, TreeSelectionListener>(
        SwingJTree::addTreeSelectionListener,
        SwingJTree::removeTreeSelectionListener,
    )

private val TREE_SELECTION_CALLBACKS =
    CallbackRegistration<SwingJTree, (TreeSelectionEvent) -> Unit, TreeSelectionListener>(
        adapter = { current -> TreeSelectionListener { event -> current()(event) } },
        registration = TREE_SELECTION,
    )
