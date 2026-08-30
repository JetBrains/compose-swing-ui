@file:JvmMultifileClass
@file:JvmName("ListenerModifierKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component
import javax.swing.AbstractButton
import javax.swing.JColorChooser
import javax.swing.JProgressBar
import javax.swing.JSlider
import javax.swing.JSpinner
import javax.swing.JTabbedPane
import javax.swing.JViewport
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import kotlin.reflect.KClass

/**
 * Runs [onChange] on the change event of a component that fires one - the widgets [changeListener]
 * lists.
 *
 * [onChange] is read when the event fires, so writing a fresh lambda on every recomposition registers
 * nothing again.
 *
 * @see javax.swing.event.ChangeListener
 */
public fun SwingModifier.changeListener(onChange: (ChangeEvent) -> Unit): SwingModifier =
    listener(onChange, CHANGE_CALLBACKS)

/**
 * Runs [onChange] on the change event of a component of type [T] that fires one, with that component as
 * `this`.
 *
 * @see javax.swing.event.ChangeListener
 */
public inline fun <reified T : Component> SwingModifier.changeListener(
    noinline onChange: T.(ChangeEvent) -> Unit,
): SwingModifier = changeListener(T::class, onChange)

/**
 * Runs [onChange] on the change event of a component of type [targetType] that fires one, with that
 * component as `this`. An event sourced anywhere else is refused; see [listener].
 *
 * A `JColorChooser` publishes through its selection model, so its change events name that model rather
 * than the chooser and are not ones this overload can scope.
 *
 * @see javax.swing.event.ChangeListener
 */
public fun <T : Component> SwingModifier.changeListener(
    targetType: KClass<T>,
    onChange: T.(ChangeEvent) -> Unit,
): SwingModifier = listener(targetType, CHANGE_CALLBACKS, onChange)

/**
 * Attaches a [ChangeListener] (`addChangeListener`/`removeChangeListener`) to a component that fires
 * change events (`JSlider`, `JSpinner`, `JTabbedPane`, `JProgressBar`, `AbstractButton`, `JViewport`,
 * `JColorChooser`). A color chooser publishes its change events through its `selectionModel`, and the
 * registration follows that model when the chooser is given another one.
 *
 * @see javax.swing.event.ChangeListener
 */
public fun SwingModifier.changeListener(listener: ChangeListener): SwingModifier = listener(listener, CHANGE)

/**
 * The matched widget's `addChangeListener`/`removeChangeListener` pair, resolved once so a single
 * narrowing [when][changeListenerRegistrar] backs both attach and detach.
 */
private class ChangeListenerRegistrar(
    val add: (ChangeListener) -> Unit,
    val remove: (ChangeListener) -> Unit,
)

/**
 * The Swing widgets that fire change events expose no common supertype declaring an
 * `addChangeListener`/`removeChangeListener` pair, so [changeListener] routes through this single
 * narrowing dispatch, which yields the matched widget's add/remove pair for both attach and detach.
 */
private fun changeListenerRegistrar(component: Component): ChangeListenerRegistrar =
    when (component) {
        is AbstractButton -> {
            ChangeListenerRegistrar(component::addChangeListener, component::removeChangeListener)
        }

        is JSlider -> {
            ChangeListenerRegistrar(component::addChangeListener, component::removeChangeListener)
        }

        is JSpinner -> {
            ChangeListenerRegistrar(component::addChangeListener, component::removeChangeListener)
        }

        is JTabbedPane -> {
            ChangeListenerRegistrar(component::addChangeListener, component::removeChangeListener)
        }

        is JProgressBar -> {
            ChangeListenerRegistrar(component::addChangeListener, component::removeChangeListener)
        }

        is JViewport -> {
            ChangeListenerRegistrar(component::addChangeListener, component::removeChangeListener)
        }

        is JColorChooser -> {
            ChangeListenerRegistrar(
                component::attachSwappableChangeListener,
                component::detachSwappableChangeListener,
            )
        }

        else -> {
            error(changeListenerTargetError(component))
        }
    }

private fun changeListenerTargetError(component: Component): String =
    "changeListener requires a component that fires change events " +
        "(JSlider, JSpinner, JTabbedPane, JProgressBar, AbstractButton, JViewport, JColorChooser), " +
        "but the component is a ${component.javaClass.name}"

private val CHANGE =
    ListenerRegistration<Component, ChangeListener>(
        { component, listener -> changeListenerRegistrar(component).add(listener) },
        { component, listener -> changeListenerRegistrar(component).remove(listener) },
    )

private val CHANGE_CALLBACKS =
    CallbackRegistration<Component, (ChangeEvent) -> Unit, ChangeListener>(
        adapter = { current -> ChangeListener { event -> current()(event) } },
        registration = CHANGE,
    )
