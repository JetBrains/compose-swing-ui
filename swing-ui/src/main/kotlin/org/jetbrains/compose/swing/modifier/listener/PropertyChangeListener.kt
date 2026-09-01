@file:JvmMultifileClass
@file:JvmName("ListenerModifierKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import kotlin.reflect.KClass

/**
 * Runs [onPropertyChange] on every bound property change of the component. For a single property,
 * prefer the [name][propertyChangeListener] overload.
 *
 * [onPropertyChange] is read when the event fires, so writing a fresh lambda on every recomposition
 * registers nothing again.
 *
 * @param onPropertyChange receives the event, whose `propertyName`, `oldValue` and `newValue` describe
 *   the change; a component fires nothing where the two values are both non-null and equal.
 * @return this chain with the property change callback declared on it.
 * @see java.awt.Component.addPropertyChangeListener
 */
public fun SwingModifier.propertyChangeListener(onPropertyChange: (PropertyChangeEvent) -> Unit): SwingModifier =
    listener(onPropertyChange, PROPERTY_CHANGE_CALLBACKS)

/**
 * Runs [onPropertyChange] on changes to the property [name] only.
 *
 * [onPropertyChange] is read when the event fires, so writing a fresh lambda on every recomposition
 * registers nothing again. Declaring a different [name] moves the registration to that property.
 *
 * @param name the bound property, spelled as the component fires it; a name nothing fires reports
 *   nothing and is not an error.
 * @param onPropertyChange receives that property's events, with `oldValue` and `newValue` as the
 *   component reported them.
 * @return this chain with the property change callback declared on it.
 * @see java.awt.Component.addPropertyChangeListener
 */
public fun SwingModifier.propertyChangeListener(
    name: String,
    onPropertyChange: (PropertyChangeEvent) -> Unit,
): SwingModifier = listener(onPropertyChange, boundPropertyCallbackRegistration(name))

/**
 * Runs [onPropertyChange] on changes to the property [name] of a component of type [T], with that
 * component as `this`.
 *
 * @param name the bound property, spelled as the component fires it.
 * @param onPropertyChange read when the event fires. A parameter of the enclosing composable shadows the
 *   receiver, so reach it through `this`.
 *   Read the component's state through the receiver; writing a property the composition declares
 *   through it makes the second manager [listener] describes.
 * @return this chain with the property change callback declared on it.
 * @see java.awt.Component.addPropertyChangeListener
 */
public inline fun <reified T : Component> SwingModifier.propertyChangeListener(
    name: String,
    noinline onPropertyChange: T.(PropertyChangeEvent) -> Unit,
): SwingModifier = propertyChangeListener(name, T::class, onPropertyChange)

/**
 * Runs [onPropertyChange] on changes to the property [name] of a component of type [targetType], with
 * that component as `this`. An event sourced anywhere else is refused; see [listener].
 *
 * @param name the bound property, spelled as the component fires it.
 * @param targetType the component type the node and every event's source are both checked against.
 * @param onPropertyChange read when the event fires.
 * @return this chain with the property change callback declared on it.
 * @see java.awt.Component.addPropertyChangeListener
 */
public fun <T : Component> SwingModifier.propertyChangeListener(
    name: String,
    targetType: KClass<T>,
    onPropertyChange: T.(PropertyChangeEvent) -> Unit,
): SwingModifier = listener(targetType, boundPropertyCallbackRegistration(name), onPropertyChange)

/**
 * Attaches an unbound [PropertyChangeListener] (`addPropertyChangeListener`), notified of every bound
 * property change. For a single property, prefer the [name][propertyChangeListener] overload.
 *
 * @param listener attached by identity, so a remembered instance stays put while a fresh one on every
 *   pass is detached and re-added.
 * @return this chain with the property change listener declared on it.
 * @see java.awt.Component.addPropertyChangeListener
 */
public fun SwingModifier.propertyChangeListener(listener: PropertyChangeListener): SwingModifier =
    listener(listener, PROPERTY_CHANGES)

/**
 * Attaches a [PropertyChangeListener] bound to the property [name]
 * (`addPropertyChangeListener(name, listener)`), notified only of changes to that property.
 *
 * @param name the bound property, spelled as the component fires it.
 * @param listener attached by identity, so a remembered instance stays put while a fresh one on every
 *   pass is detached and re-added.
 * @return this chain with the bound property listener declared on it.
 * @see java.awt.Component.addPropertyChangeListener
 */
public fun SwingModifier.propertyChangeListener(
    name: String,
    listener: PropertyChangeListener,
): SwingModifier = listener(listener, boundPropertyRegistration(name))

private val PROPERTY_CHANGES =
    ListenerRegistration<Component, PropertyChangeListener>(
        Component::addPropertyChangeListener,
        Component::removePropertyChangeListener,
    )

private val propertyChangeAdapter: (() -> (PropertyChangeEvent) -> Unit) -> PropertyChangeListener =
    { current -> PropertyChangeListener { event -> current()(event) } }

private val PROPERTY_CHANGE_CALLBACKS =
    CallbackRegistration(propertyChangeAdapter, PROPERTY_CHANGES)

/*
 * The property name is what names a bound property's registration. It cannot travel inside the listener,
 * and a registration closing over it cannot be held in a `val`, so the name travels as the
 * registration's key: one built afresh on every pass is the same registration as the one before it, and
 * nothing is retained between passes.
 */
private fun boundPropertyRegistration(name: String): ListenerRegistration<Component, PropertyChangeListener> =
    ListenerRegistration(
        { component, listener -> component.addPropertyChangeListener(name, listener) },
        { component, listener -> component.removePropertyChangeListener(name, listener) },
        key = BoundProperty(name),
    )

private fun boundPropertyCallbackRegistration(
    name: String,
): CallbackRegistration<Component, (PropertyChangeEvent) -> Unit, PropertyChangeListener> =
    CallbackRegistration(propertyChangeAdapter, boundPropertyRegistration(name))

/** Names the registration a listener bound to one property sits on, told apart from every other key. */
private data class BoundProperty(
    val name: String,
)
