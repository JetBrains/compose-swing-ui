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
    liveCallbackListener<JList<*>, (ListSelectionEvent) -> Unit, ListSelectionListener>(
        callback = onSelectionChange,
        adapter = { current -> ListSelectionListener { event -> current()(event) } },
        attach = { component, listener -> component.addListSelectionListener(listener) },
        detach = { component, listener -> component.removeListSelectionListener(listener) },
    )

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
