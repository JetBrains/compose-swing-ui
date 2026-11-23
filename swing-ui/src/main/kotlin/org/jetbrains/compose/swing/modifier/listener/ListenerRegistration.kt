// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.annotations.SwingComponentLibraryApi
import org.jetbrains.compose.swing.node.runReportingUncaught
import java.awt.Component
import java.util.EventListener
import kotlin.reflect.KProperty1

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
 * @property name the event source this registers on, which is what an error about a listener element and
 *     a tool showing the chain a component carries both name it by. Display only: two registrations
 *     sharing a name are still two registrations.
 * @property attach adds a listener to the component.
 * @property detach removes a listener from the component.
 * @param key what identifies this registration among registrations built at the same site; `null` where
 *     the registration is held in a `val` and is therefore identified by being that object.
 */
@SwingComponentLibraryApi
public class ListenerRegistration<in T : Component, L : EventListener>(
    internal val name: String,
    internal val attach: (component: T, listener: L) -> Unit,
    internal val detach: (component: T, listener: L) -> Unit,
    private val key: Any? = null,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (key != null && other is ListenerRegistration<*, *> && key == other.key)

    override fun hashCode(): Int = key?.hashCode() ?: System.identityHashCode(this)
}

/**
 * The callback a modifier declares right now, as a [CallbackRegistration] adapter reaches it. A pass that
 * declares a new callback changes what it yields without rebuilding the listener that holds it.
 */
@SwingComponentLibraryApi
public class DeclaredCallback<C : Any>
    internal constructor(
        private val read: () -> C,
    ) {
        /**
         * The callback declared right now. Read it directly where a throw must reach Swing, such as a veto, or
         * where only a gesture or a posted event reaches the listener; call it through [report] where Swing
         * raises the event inside a write.
         */
        public val current: C get() = read()

        /**
         * Calls [block] with the callback declared right now. A throw out of [block] goes unchanged to the
         * current thread's uncaught-exception handler and this function returns normally.
         *
         * Keep the adapter's own checks and vetoes outside [block], so their failures reach the caller.
         *
         * @param block calls the callback it is given.
         */
        public inline fun report(crossinline block: (callback: C) -> Unit): Unit =
            runReportingUncaught { block(current) }
    }

/**
 * A [registration] together with the [adapter] that builds the listener riding it: what a modifier taking
 * a callback of type [C], rather than a listener of type [L], registers.
 *
 * Declare one per builder and hold it in a `val`, as for a [ListenerRegistration]. The callback is not
 * part of it: a fresh callback on every pass costs one field write.
 *
 * In [adapter], call the callback through [DeclaredCallback.report] where Swing raises the event inside a
 * write - a property change, a child insertion, a model write - so a throw from it is reported and the
 * listener returns normally. Keep adapter checks and vetoes outside the `report` block. A listener that
 * only a gesture or a posted event reaches may call [DeclaredCallback.current] directly, and Swing's
 * event pump handles its throw; so does a veto, whose throw must reach Swing.
 *
 * This constructor compares two callbacks by reference. Where the callback is an immutable value made of
 * callbacks, [byCallbackIdentity] compares each of them instead.
 *
 * @property adapter builds the listener; its `callback` argument reaches the callback declared right now.
 * @param registration where the built listener is registered.
 * @param callbackProperties the properties of the callback value compared by identity, or `null` to compare
 *   the value itself.
 */
@SwingComponentLibraryApi
public class CallbackRegistration<in T : Component, C : Any, L : EventListener> private constructor(
    internal val adapter: (callback: DeclaredCallback<C>) -> L,
    private val registration: ListenerRegistration<T, L>,
    private val callbackProperties: Array<out KProperty1<C, Function<*>?>>?,
) {
    public constructor(
        adapter: (callback: DeclaredCallback<C>) -> L,
        registration: ListenerRegistration<T, L>,
    ) : this(adapter, registration, null)

    /** The event source registered on, taken from [registration]. */
    internal val name: String get() = registration.name

    internal fun attach(
        component: T,
        listener: L,
    ): Unit = registration.attach(component, listener)

    internal fun detach(
        component: T,
        listener: L,
    ): Unit = registration.detach(component, listener)

    /** The callbacks [callback] declares, which this registration compares one by one by identity. */
    internal fun identities(callback: C): List<*> = callbackProperties?.map { it.get(callback) } ?: listOf(callback)

    /** The hash code agreeing with [identities]. */
    internal fun payloadHash(callback: C): Int =
        callbackProperties?.sumOf { System.identityHashCode(it.get(callback)) }
            ?: System.identityHashCode(callback)

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is CallbackRegistration<*, *, *> &&
                    adapter === other.adapter &&
                    registration == other.registration &&
                    callbackProperties.contentEquals(other.callbackProperties)
            )

    override fun hashCode(): Int =
        31 * (31 * System.identityHashCode(adapter) + registration.hashCode()) + callbackProperties.contentHashCode()

    /** Builders of a [CallbackRegistration] that compares its callback by something other than the value's identity. */
    public companion object {
        /**
         * A registration for a callback that is an immutable value made of callbacks, compared by the
         * identity of each callback it holds rather than by the identity of the value.
         *
         * A pass that declares a value holding the same callback instances changes nothing; one holding a
         * different instance in any of [callbackProperties] refreshes the installed listener's callback
         * without registering it again. A property reading `null` is a callback left undeclared. The
         * callbacks' own `equals` is never consulted, which matters because a bound function reference
         * has one that calls two references to different receivers equal.
         *
         * Name every callback [adapter] invokes, and hold the registration in a `val`, as for the
         * constructor. A value carrying anything besides callbacks that changes what the listener does
         * belongs on the constructor, which compares the whole value by reference. Registrations reading
         * different properties are different registrations.
         *
         * @param adapter builds the listener, as for the constructor.
         * @param registration where the built listener is registered.
         * @param callbackProperties the properties of [C] that hold the callbacks, read unbound; at least one.
         * @throws IllegalArgumentException when [callbackProperties] is empty.
         */
        public fun <T : Component, C : Any, L : EventListener> byCallbackIdentity(
            adapter: (DeclaredCallback<C>) -> L,
            registration: ListenerRegistration<T, L>,
            vararg callbackProperties: KProperty1<C, Function<*>?>,
        ): CallbackRegistration<T, C, L> {
            require(callbackProperties.isNotEmpty()) { "byCallbackIdentity needs at least one callback property" }
            return CallbackRegistration(adapter, registration, callbackProperties)
        }
    }
}
