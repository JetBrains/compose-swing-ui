@file:JvmMultifileClass
@file:JvmName("ListenerModifierKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.JList
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import kotlin.reflect.KClass

/**
 * Runs [onSelectionChange] whenever the list's selection changes. Requires a [JList] target.
 *
 * [onSelectionChange] is read when the event fires, so writing a fresh lambda on every recomposition
 * registers nothing again.
 *
 * @param onSelectionChange receives the event, whose `firstIndex` and `lastIndex` are the ends of the
 *   row range to re-read, and whose `valueIsAdjusting` is `true` while the user is still dragging the
 *   selection out - a handler that wants only the settled result skips those.
 * @return this chain with the selection callback declared on it.
 * @see javax.swing.JList.addListSelectionListener
 */
public fun SwingModifier.listSelectionListener(onSelectionChange: (ListSelectionEvent) -> Unit): SwingModifier =
    listener(onSelectionChange, LIST_SELECTION_CALLBACKS)

/**
 * Runs [onSelectionChange] on the selection event of a list of type [T], with that list as `this`.
 *
 * @param onSelectionChange read when the event fires. A parameter of the enclosing composable shadows
 *   the receiver, so reach it through `this`.
 *   Read the component's state through the receiver; writing a property the composition declares
 *   through it makes the second manager [listener] describes.
 * @return this chain with the selection callback declared on it.
 * @see javax.swing.JList.addListSelectionListener
 */
public inline fun <reified T : JList<*>> SwingModifier.listSelectionListener(
    noinline onSelectionChange: T.(ListSelectionEvent) -> Unit,
): SwingModifier = listSelectionListener(T::class, onSelectionChange)

/**
 * Runs [onSelectionChange] on the selection event of a list of type [targetType], with that list as
 * `this`. An event sourced anywhere else is refused; see [listener].
 *
 * @param targetType the list type the node and every event's source are both checked against.
 * @param onSelectionChange read when the event fires.
 * @return this chain with the selection callback declared on it.
 * @see javax.swing.JList.addListSelectionListener
 */
public fun <T : JList<*>> SwingModifier.listSelectionListener(
    targetType: KClass<T>,
    onSelectionChange: T.(ListSelectionEvent) -> Unit,
): SwingModifier = listener(targetType, LIST_SELECTION_CALLBACKS, onSelectionChange)

/**
 * Attaches a [ListSelectionListener]
 * (`addListSelectionListener`/`removeListSelectionListener`). Requires a [JList] target.
 *
 * @param listener attached by identity, so a remembered instance stays put while a fresh one on every
 *   pass is detached and re-added.
 * @return this chain with the selection listener declared on it.
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
