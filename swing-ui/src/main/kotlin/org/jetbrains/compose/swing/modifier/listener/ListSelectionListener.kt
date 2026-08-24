@file:JvmMultifileClass
@file:JvmName("ListenerModifierKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.JList
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener

/**
 * Runs [onSelectionChange] whenever the list's selection changes. Requires a [JList] target.
 *
 * [onSelectionChange] is read when the event fires, so writing a fresh lambda on every recomposition
 * registers nothing again.
 *
 * @see javax.swing.JList.addListSelectionListener
 */
public fun SwingModifier.listSelectionListener(onSelectionChange: (ListSelectionEvent) -> Unit): SwingModifier =
    listener(onSelectionChange, LIST_SELECTION_CALLBACKS)

/**
 * Attaches a [ListSelectionListener]
 * (`addListSelectionListener`/`removeListSelectionListener`). Requires a [JList] target.
 *
 * @see javax.swing.JList.addListSelectionListener
 */
public fun SwingModifier.listSelectionListener(listener: ListSelectionListener): SwingModifier =
    listener(listener, LIST_SELECTION)

private val LIST_SELECTION =
    ListenerRegistration<JList<*>, ListSelectionListener>(
        JList<*>::addListSelectionListener,
        JList<*>::removeListSelectionListener,
    )

private val LIST_SELECTION_CALLBACKS =
    CallbackRegistration<JList<*>, (ListSelectionEvent) -> Unit, ListSelectionListener>(
        adapter = { current -> ListSelectionListener { event -> current()(event) } },
        registration = LIST_SELECTION,
    )
