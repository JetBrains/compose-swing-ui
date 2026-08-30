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
 * @see java.awt.Component.addPropertyChangeListener
 */
public fun SwingModifier.propertyChangeListener(listener: PropertyChangeListener): SwingModifier =
    listener(listener, PROPERTY_CHANGES)

/**
 * Attaches a [PropertyChangeListener] bound to the property [name]
 * (`addPropertyChangeListener(name, listener)`), notified only of changes to that property.
 *
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
