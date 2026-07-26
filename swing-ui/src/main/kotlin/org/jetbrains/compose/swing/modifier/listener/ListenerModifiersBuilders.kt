@file:JvmMultifileClass
@file:JvmName("ListenerModifiersKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component
import java.awt.event.ActionListener
import java.beans.PropertyChangeListener
import javax.swing.AbstractButton
import javax.swing.JComboBox
import javax.swing.JTextField
import javax.swing.event.DocumentListener
import javax.swing.text.JTextComponent

/*
 * Typed instance builders for model- and role-specific listeners - property change, action, and
 * text-document. They share the by-identity add/remove contract of the builders in ListenerModifiers.kt:
 * the instance is added once, the same instance is removed on detach, and supplying a different instance
 * on recomposition detaches the old one and attaches the new.
 */

/**
 * Attaches an unbound [PropertyChangeListener] (`addPropertyChangeListener`), notified of every bound
 * property change. For a single property, prefer the [name][propertyChangeListener] overload.
 */
public fun SwingModifier.propertyChangeListener(listener: PropertyChangeListener): SwingModifier =
    listener<Component, PropertyChangeListener>(
        listener,
        { c, l -> c.addPropertyChangeListener(l) },
        { c, l -> c.removePropertyChangeListener(l) },
    )

/**
 * Attaches a [PropertyChangeListener] bound to the property [name]
 * (`addPropertyChangeListener(name, listener)`), notified only of changes to that property.
 */
public fun SwingModifier.propertyChangeListener(
    name: String,
    listener: PropertyChangeListener,
): SwingModifier =
    listener<Component, PropertyChangeListener>(
        listener,
        { c, l -> c.addPropertyChangeListener(name, l) },
        { c, l -> c.removePropertyChangeListener(name, l) },
    )

/**
 * Attaches an [ActionListener] (`addActionListener`/`removeActionListener`) to a component that fires
 * action events (`AbstractButton` - so `JButton`, `JCheckBox`, ... -, `JTextField`, or `JComboBox`).
 *
 * A text field fires its action event when the user presses Enter in it; the interaction family's
 * `onAccept` is the same channel with a live callback in place of a listener instance.
 */
public fun SwingModifier.actionListener(listener: ActionListener): SwingModifier =
    listener<Component, ActionListener>(
        listener,
        { c, l -> actionListenerRegistrar(c).add(l) },
        { c, l -> actionListenerRegistrar(c).remove(l) },
    )

/**
 * The matched widget's `addActionListener`/`removeActionListener` pair, resolved once so a single
 * narrowing [when][actionListenerRegistrar] backs both attach and detach.
 */
private class ActionListenerRegistrar(
    val add: (ActionListener) -> Unit,
    val remove: (ActionListener) -> Unit,
)

/**
 * The Swing widgets that publish action events through `addActionListener`/`removeActionListener` share
 * no common supertype that declares the pair, so [actionListener] routes through this single narrowing
 * dispatch, which yields the matched widget's add/remove pair for both attach and detach.
 */
private fun actionListenerRegistrar(component: Component): ActionListenerRegistrar =
    when (component) {
        is AbstractButton -> ActionListenerRegistrar(component::addActionListener, component::removeActionListener)
        is JTextField -> ActionListenerRegistrar(component::addActionListener, component::removeActionListener)
        is JComboBox<*> -> ActionListenerRegistrar(component::addActionListener, component::removeActionListener)
        else -> error(actionListenerTargetError(component))
    }

private fun actionListenerTargetError(component: Component): String =
    "actionListener requires a component that fires action events " +
        "(AbstractButton, JTextField, JComboBox), " +
        "but the component is a ${component.javaClass.name}"

/**
 * Attaches a [DocumentListener] to the text component's `document` (`document.addDocumentListener`).
 * Requires a [JTextComponent] target (`JTextField`, `JTextArea`, ...). The listener observes the
 * `document` the component holds at install time.
 */
public fun SwingModifier.documentListener(listener: DocumentListener): SwingModifier =
    listener<JTextComponent, DocumentListener>(
        listener,
        { c, l -> c.document.addDocumentListener(l) },
        { c, l -> c.document.removeDocumentListener(l) },
    )
