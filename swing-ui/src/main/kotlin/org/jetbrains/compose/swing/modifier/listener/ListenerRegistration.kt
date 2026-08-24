package org.jetbrains.compose.swing.modifier.listener

import java.awt.Component

/**
 * Where a listener is registered: the add/remove pair of one event source on components of type [T], for
 * listeners of type [L].
 *
 * Declare one per event source and hold it in a `val` - a registration with no [key] is the same
 * registration only when it is the same object, so one built afresh at a call site is a new registration
 * on every pass and re-registers the listener each time. Declaring a different registration moves the
 * listener to it.
 *
 * Where the pair closes over something that varies between call sites - the name of a bound property,
 * say - a registration cannot be held in a `val`, and [key] is what says which registration it is
 * instead: two registrations carrying equal keys are the same registration. Give the key a type of the
 * site's own, since it is compared against the keys of every other registration.
 *
 * @property attach adds a listener to the component.
 * @property detach removes a listener from the component.
 * @param key what identifies this registration among registrations built at the same site; `null` where
 *     the registration is held in a `val` and is therefore identified by being that object.
 */
public class ListenerRegistration<T : Component, L : Any>(
    internal val attach: (component: T, listener: L) -> Unit,
    internal val detach: (component: T, listener: L) -> Unit,
    private val key: Any? = null,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (key != null && other is ListenerRegistration<*, *> && key == other.key)

    override fun hashCode(): Int = key?.hashCode() ?: System.identityHashCode(this)
}

/**
 * A [registration] together with the [adapter] that builds the listener riding it: what a modifier taking
 * a callback of type [C], rather than a listener of type [L], registers.
 *
 * Declare one per builder and hold it in a `val`, as for a [ListenerRegistration]. The callback is not
 * part of it: a fresh callback on every pass costs one field write.
 *
 * @property adapter builds the listener; its `current` argument yields the callback declared right now.
 * @param registration where the built listener is registered.
 */
public class CallbackRegistration<T : Component, C : Any, L : Any>(
    internal val adapter: (current: () -> C) -> L,
    private val registration: ListenerRegistration<T, L>,
) {
    internal fun attach(
        component: T,
        listener: L,
    ): Unit = registration.attach(component, listener)

    internal fun detach(
        component: T,
        listener: L,
    ): Unit = registration.detach(component, listener)

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is CallbackRegistration<*, *, *> && adapter === other.adapter && registration == other.registration)

    override fun hashCode(): Int = 31 * System.identityHashCode(adapter) + registration.hashCode()
}
