@file:JvmMultifileClass
@file:JvmName("ListenerModifierKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import javax.swing.AbstractButton
import javax.swing.JComboBox
import javax.swing.JFileChooser
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.JTextComponent
import java.awt.Button as AwtButton
import java.awt.List as AwtList
import java.awt.TextField as AwtTextField

/**
 * Runs [onPropertyChange] on every bound property change of the component. For a single property,
 * prefer the [name][propertyChangeListener] overload.
 *
 * [onPropertyChange] is read when the event fires, so writing a fresh lambda on every recomposition
 * registers nothing again.
 *
 * @see java.awt.Component.addPropertyChangeListener
 */
public fun SwingModifier.propertyChangeListener(onPropertyChange: (PropertyChangeEvent) -> Unit): SwingModifier =
    listener<Component, (PropertyChangeEvent) -> Unit, PropertyChangeListener>(
        callback = onPropertyChange,
        adapter = { current -> PropertyChangeListener { event -> current()(event) } },
        attach = { component, listener -> component.addPropertyChangeListener(listener) },
        detach = { component, listener -> component.removePropertyChangeListener(listener) },
    )

/**
 * Runs [onPropertyChange] on changes to the property [name] only.
 *
 * [onPropertyChange] is read when the event fires, so writing a fresh lambda on every recomposition
 * registers nothing again. Declaring a different [name] moves the registration to that property.
 *
 * @see java.awt.Component.addPropertyChangeListener
 */
public fun SwingModifier.propertyChangeListener(
    name: String,
    onPropertyChange: (PropertyChangeEvent) -> Unit,
): SwingModifier =
    listener<Component, (PropertyChangeEvent) -> Unit, PropertyChangeListener>(
        callback = onPropertyChange,
        adapter = { current -> PropertyChangeListener { event -> current()(event) } },
        attach = { component, listener -> component.addPropertyChangeListener(name, listener) },
        detach = { component, listener -> component.removePropertyChangeListener(name, listener) },
        registrationKey = name,
    )

/*
 * Typed instance builders for model- and role-specific listeners - property change, action, and
 * text-document - built on the by-identity add/remove contract of `listener`'s instance overload.
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
 * Runs [onAction] on the action event of a component that fires one - the same components
 * [actionListener] lists.
 *
 * [onAction] is read when the event fires, so writing a fresh lambda on every recomposition registers
 * nothing again.
 *
 * @see java.awt.event.ActionListener
 */
public fun SwingModifier.actionListener(onAction: (ActionEvent) -> Unit): SwingModifier =
    listener<Component, (ActionEvent) -> Unit, ActionListener>(
        callback = onAction,
        adapter = { current -> ActionListener { event -> current()(event) } },
        attach = { component, listener -> actionListenerRegistrar(component).add(listener) },
        detach = { component, listener -> actionListenerRegistrar(component).remove(listener) },
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
 * Runs [onDocumentChange] for every change to the text component's `document` - an insertion, a removal
 * and a change of attributes alike. Requires a [JTextComponent] target, and observes the `document` the
 * component holds at install time.
 *
 * [onDocumentChange] is read when the event fires, so writing a fresh lambda on every recomposition
 * registers nothing again. To tell the three changes apart, declare them one by one instead.
 *
 * @see javax.swing.text.Document.addDocumentListener
 */
public fun SwingModifier.documentListener(onDocumentChange: (DocumentEvent) -> Unit): SwingModifier =
    documentListener(
        onInsert = onDocumentChange,
        onRemove = onDocumentChange,
        onChange = onDocumentChange,
    )

/**
 * Runs [onInsert] when text enters the text component's `document`, [onRemove] when text leaves it, and
 * [onChange] when its attributes change. Requires a [JTextComponent] target, and observes the `document`
 * the component holds at install time.
 *
 * Each lambda is read when its event fires, so writing a fresh one on every recomposition registers
 * nothing again. A change left undeclared reports nowhere.
 *
 * Declaring none at all is refused.
 *
 * @see javax.swing.text.Document.addDocumentListener
 */
public fun SwingModifier.documentListener(
    onInsert: (DocumentEvent) -> Unit = UNDECLARED,
    onRemove: (DocumentEvent) -> Unit = UNDECLARED,
    onChange: (DocumentEvent) -> Unit = UNDECLARED,
): SwingModifier {
    requireAnyDeclared("documentListener", declared(onInsert) || declared(onRemove) || declared(onChange))
    return listener<JTextComponent, DocumentCallbacks, DocumentListener>(
        callback = DocumentCallbacks(onInsert, onRemove, onChange),
        adapter = { current ->
            object : DocumentListener {
                override fun insertUpdate(event: DocumentEvent): Unit = current().onInsert(event)

                override fun removeUpdate(event: DocumentEvent): Unit = current().onRemove(event)

                override fun changedUpdate(event: DocumentEvent): Unit = current().onChange(event)
            }
        },
        attach = { component, listener -> component.document.addDocumentListener(listener) },
        detach = { component, listener -> component.document.removeDocumentListener(listener) },
    )
}

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

/** The lambdas [documentListener] was declared with, as one value the built listener reads. */
private class DocumentCallbacks(
    val onInsert: (DocumentEvent) -> Unit,
    val onRemove: (DocumentEvent) -> Unit,
    val onChange: (DocumentEvent) -> Unit,
)
