@file:JvmMultifileClass
@file:JvmName("ListenerModifierKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component
import java.awt.event.ActionListener
import java.beans.PropertyChangeListener
import javax.swing.AbstractButton
import javax.swing.JComboBox
import javax.swing.JFileChooser
import javax.swing.JTextField
import javax.swing.event.DocumentListener
import javax.swing.text.JTextComponent
import java.awt.Button as AwtButton
import java.awt.List as AwtList
import java.awt.TextField as AwtTextField

/*
 * Typed instance builders for model- and role-specific listeners - property change, action, and
 * text-document - built on [listener]'s by-identity add/remove contract.
 */

/**
 * Attaches an unbound [PropertyChangeListener] (`addPropertyChangeListener`), notified of every bound
 * property change. For a single property, prefer the [name][propertyChangeListener] overload.
 *
 * @see java.awt.Component.addPropertyChangeListener
 */
public fun SwingModifier.propertyChangeListener(listener: PropertyChangeListener): SwingModifier =
    listener<Component, PropertyChangeListener>(
        listener,
        Component::addPropertyChangeListener,
        Component::removePropertyChangeListener,
    )

/**
 * Attaches a [PropertyChangeListener] bound to the property [name]
 * (`addPropertyChangeListener(name, listener)`), notified only of changes to that property.
 *
 * @see java.awt.Component.addPropertyChangeListener
 */
public fun SwingModifier.propertyChangeListener(
    name: String,
    listener: PropertyChangeListener,
): SwingModifier =
    listener<Component, PropertyChangeListener>(
        listener,
        { component, instance -> component.addPropertyChangeListener(name, instance) },
        { component, instance -> component.removePropertyChangeListener(name, instance) },
    )

/**
 * Attaches an [ActionListener] (`addActionListener`/`removeActionListener`) to a component that fires
 * action events (`AbstractButton` - so `JButton`, `JCheckBox`, ... -, `JTextField`, `JComboBox`,
 * `JFileChooser`, and the AWT `Button`, `TextField`, and `List`).
 *
 * A text field fires its action event when the user presses Enter in it; the interaction family's
 * `onAccept` is the same channel with a live callback in place of a listener instance.
 *
 * @see java.awt.event.ActionListener
 */
public fun SwingModifier.actionListener(listener: ActionListener): SwingModifier =
    listener<Component, ActionListener>(
        listener,
        { component, instance -> actionListenerRegistrar(component).add(instance) },
        { component, instance -> actionListenerRegistrar(component).remove(instance) },
    )

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

/**
 * Attaches a [DocumentListener] to the text component's `document` (`document.addDocumentListener`).
 * Requires a [JTextComponent] target (`JTextField`, `JTextArea`, ...). The listener observes the
 * `document` the component holds at install time.
 *
 * @see javax.swing.text.Document.addDocumentListener
 */
public fun SwingModifier.documentListener(listener: DocumentListener): SwingModifier =
    listener<JTextComponent, DocumentListener>(
        listener,
        { component, instance -> component.document.addDocumentListener(instance) },
        { component, instance -> component.document.removeDocumentListener(instance) },
    )
