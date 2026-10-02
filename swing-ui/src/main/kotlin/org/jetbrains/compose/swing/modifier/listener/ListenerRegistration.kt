package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.node.runReportingUncaught
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
 * @property name the event source this registers on, which is what an error about a listener element and
 *     a tool showing the chain a component carries both name it by. Display only: two registrations
 *     sharing a name are still two registrations.
 * @property attach adds a listener to the component.
 * @property detach removes a listener from the component.
 * @param key what identifies this registration among registrations built at the same site; `null` where
 *     the registration is held in a `val` and is therefore identified by being that object.
 */
public class ListenerRegistration<in T : Component, L : Any>(
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
 * @property adapter builds the listener; its `callback` argument reaches the callback declared right now.
 * @param registration where the built listener is registered.
 */
public class CallbackRegistration<in T : Component, C : Any, L : Any>(
    internal val adapter: (callback: DeclaredCallback<C>) -> L,
    private val registration: ListenerRegistration<T, L>,
) {
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

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is CallbackRegistration<*, *, *> && adapter === other.adapter && registration == other.registration)

    override fun hashCode(): Int = 31 * System.identityHashCode(adapter) + registration.hashCode()
}
