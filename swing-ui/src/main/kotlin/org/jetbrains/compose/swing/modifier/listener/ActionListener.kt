@file:JvmMultifileClass
@file:JvmName("ListenerModifierKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.AbstractButton
import javax.swing.JComboBox
import javax.swing.JFileChooser
import javax.swing.JTextField
import kotlin.reflect.KClass
import java.awt.Button as AwtButton
import java.awt.List as AwtList
import java.awt.TextField as AwtTextField

/**
 * Runs [onAction] on the action event of a component that fires one - the same components
 * [actionListener] lists.
 *
 * [onAction] is read when the event fires, so writing a fresh lambda on every recomposition registers
 * nothing again.
 *
 * @see java.awt.event.ActionListener
 */
public fun SwingModifier.actionListener(onAction: (ActionEvent) -> Unit): SwingModifier =
    listener(onAction, ACTION_CALLBACKS)

/**
 * Runs [onAction] on the action event of a component of type [T] that fires one, with that component as
 * `this`.
 *
 * @see java.awt.event.ActionListener
 */
public inline fun <reified T : Component> SwingModifier.actionListener(
    noinline onAction: T.(ActionEvent) -> Unit,
): SwingModifier = actionListener(T::class, onAction)

/**
 * Runs [onAction] on the action event of a component of type [targetType] that fires one, with that
 * component as `this`. An event sourced anywhere else is refused; see [listener].
 *
 * @see java.awt.event.ActionListener
 */
public fun <T : Component> SwingModifier.actionListener(
    targetType: KClass<T>,
    onAction: T.(ActionEvent) -> Unit,
): SwingModifier = listener(targetType, ACTION_CALLBACKS, onAction)

/**
 * Attaches an [ActionListener] (`addActionListener`/`removeActionListener`) to a component that fires
 * action events (`AbstractButton` - so `JButton`, `JCheckBox`, ... -, `JTextField`, `JComboBox`,
 * `JFileChooser`, and the AWT `Button`, `TextField`, and `List`).
 *
 * A text field fires its action event when the user presses Enter in it, which is what the
 * interaction family's `onAccept` reports. That has a registration of its own, narrowed to
 * `JTextField`, so declaring both attaches two listeners rather than moving one.
 *
 * @see java.awt.event.ActionListener
 */
public fun SwingModifier.actionListener(listener: ActionListener): SwingModifier = listener(listener, ACTION)

/** The matched widget's `addActionListener`/`removeActionListener` pair. */
private class ActionListenerRegistrar(
    val add: (ActionListener) -> Unit,
    val remove: (ActionListener) -> Unit,
)

/**
 * The components that publish action events through `addActionListener`/`removeActionListener` share
 * no common supertype that declares the pair, so [actionListener] routes through this single narrowing
 * dispatch, which yields the matched component's add/remove pair for both attach and detach.
 */
private fun actionListenerRegistrar(component: Component): ActionListenerRegistrar =
    when (component) {
        is AbstractButton -> ActionListenerRegistrar(component::addActionListener, component::removeActionListener)
        is JTextField -> ActionListenerRegistrar(component::addActionListener, component::removeActionListener)
        is JComboBox<*> -> ActionListenerRegistrar(component::addActionListener, component::removeActionListener)
        is JFileChooser -> ActionListenerRegistrar(component::addActionListener, component::removeActionListener)
        is AwtButton -> ActionListenerRegistrar(component::addActionListener, component::removeActionListener)
        is AwtTextField -> ActionListenerRegistrar(component::addActionListener, component::removeActionListener)
        is AwtList -> ActionListenerRegistrar(component::addActionListener, component::removeActionListener)
        else -> error(actionListenerTargetError(component))
    }

private fun actionListenerTargetError(component: Component): String =
    "actionListener requires a component that fires action events " +
        "(AbstractButton, JTextField, JComboBox, JFileChooser, " +
        "java.awt.Button, java.awt.TextField, java.awt.List), " +
        "but the component is a ${component.javaClass.name}"

private val ACTION =
    ListenerRegistration<Component, ActionListener>(
        { component, listener -> actionListenerRegistrar(component).add(listener) },
        { component, listener -> actionListenerRegistrar(component).remove(listener) },
    )

private val ACTION_CALLBACKS =
    CallbackRegistration<Component, (ActionEvent) -> Unit, ActionListener>(
        adapter = { current -> ActionListener { event -> current()(event) } },
        registration = ACTION,
    )
