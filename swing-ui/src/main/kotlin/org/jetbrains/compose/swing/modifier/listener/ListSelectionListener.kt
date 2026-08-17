@file:JvmMultifileClass
@file:JvmName("ListenerModifierKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.JList
import javax.swing.event.ListSelectionListener

/**
 * Attaches a [ListSelectionListener]
 * (`addListSelectionListener`/`removeListSelectionListener`). Requires a [JList] target.
 *
 * @see javax.swing.JList.addListSelectionListener
 */
public fun SwingModifier.listSelectionListener(listener: ListSelectionListener): SwingModifier =
    listener<JList<*>, ListSelectionListener>(
        listener,
        JList<*>::addListSelectionListener,
        JList<*>::removeListSelectionListener,
    )
