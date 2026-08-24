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
    listener<SwingJTree, (TreeSelectionEvent) -> Unit, TreeSelectionListener>(
        callback = onSelectionChange,
        adapter = { current -> TreeSelectionListener { event -> current()(event) } },
        attach = { component, listener -> component.addTreeSelectionListener(listener) },
        detach = { component, listener -> component.removeTreeSelectionListener(listener) },
    )

/**
 * Attaches a [TreeSelectionListener]
 * (`addTreeSelectionListener`/`removeTreeSelectionListener`). Requires a [javax.swing.JTree] target.
 *
 * @see javax.swing.JTree.addTreeSelectionListener
 */
public fun SwingModifier.treeSelectionListener(listener: TreeSelectionListener): SwingModifier =
    listener<SwingJTree, TreeSelectionListener>(
        listener,
        SwingJTree::addTreeSelectionListener,
        SwingJTree::removeTreeSelectionListener,
    )
