@file:JvmMultifileClass
@file:JvmName("ListenerModifiersKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.event.TreeExpansionListener
import javax.swing.JTree as SwingJTree

/**
 * Attaches a [TreeExpansionListener]
 * (`addTreeExpansionListener`/`removeTreeExpansionListener`). Requires a [SwingJTree] target.
 */
public fun SwingModifier.treeExpansionListener(listener: TreeExpansionListener): SwingModifier =
    listener<SwingJTree, TreeExpansionListener>(
        listener,
        { c, l -> c.addTreeExpansionListener(l) },
        { c, l -> c.removeTreeExpansionListener(l) },
    )
