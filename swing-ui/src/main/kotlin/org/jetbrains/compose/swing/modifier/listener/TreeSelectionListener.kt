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
 * @param onSelectionChange receives the event, whose `paths` were added to or removed from the
 *   selection and whose `isAddedPath` says which of the two happened to a given path.
 * @return this chain with the selection callback declared on it.
 * @see javax.swing.JTree.addTreeSelectionListener
 */
public fun SwingModifier.treeSelectionListener(onSelectionChange: (TreeSelectionEvent) -> Unit): SwingModifier =
    listener(onSelectionChange, TREE_SELECTION_CALLBACKS)

/**
 * Attaches a [TreeSelectionListener]
 * (`addTreeSelectionListener`/`removeTreeSelectionListener`). Requires a [javax.swing.JTree] target.
 *
 * @param listener attached by identity, so a remembered instance stays put while a fresh one on every
 *   pass is detached and re-added.
 * @return this chain with the selection listener declared on it.
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
