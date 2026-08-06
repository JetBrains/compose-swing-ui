@file:JvmMultifileClass
@file:JvmName("ListenerModifierKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.event.TreeSelectionListener
import javax.swing.JTree as SwingJTree

/**
 * Attaches a [TreeSelectionListener]
 * (`addTreeSelectionListener`/`removeTreeSelectionListener`). Requires a [javax.swing.JTree] target.
 *
 * @see javax.swing.JTree.addTreeSelectionListener
 */
public fun SwingModifier.treeSelectionListener(listener: TreeSelectionListener): SwingModifier =
    listener<SwingJTree, TreeSelectionListener>(
        listener,
        { c, l -> c.addTreeSelectionListener(l) },
        { c, l -> c.removeTreeSelectionListener(l) },
    )
