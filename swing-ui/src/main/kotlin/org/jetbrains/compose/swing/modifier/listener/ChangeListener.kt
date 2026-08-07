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
import javax.swing.event.ChangeListener

/**
 * Attaches a [ChangeListener] (`addChangeListener`/`removeChangeListener`) to a component that fires
 * change events (`JSlider`, `JSpinner`, `JTabbedPane`, `JProgressBar`, `AbstractButton`, `JViewport`,
 * `JColorChooser`). A color chooser publishes its change events through the `selectionModel` it holds
 * when the listener is installed.
 *
 * @see javax.swing.event.ChangeListener
 */
public fun SwingModifier.changeListener(listener: ChangeListener): SwingModifier =
    listener<Component, ChangeListener>(
        listener,
        { c, l -> changeListenerRegistrar(c).add(l) },
        { c, l -> changeListenerRegistrar(c).remove(l) },
    )

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
                { l -> component.selectionModel.addChangeListener(l) },
                { l -> component.selectionModel.removeChangeListener(l) },
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
