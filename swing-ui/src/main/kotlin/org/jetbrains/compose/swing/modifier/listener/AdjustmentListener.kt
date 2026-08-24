@file:JvmMultifileClass
@file:JvmName("ListenerModifierKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Adjustable
import java.awt.Component
import java.awt.event.AdjustmentEvent
import java.awt.event.AdjustmentListener

/**
 * Runs [onAdjustment] whenever a scrollbar's value changes - the scrollbars [adjustmentListener] lists.
 *
 * [onAdjustment] is read when the event fires, so writing a fresh lambda on every recomposition
 * registers nothing again.
 *
 * @see java.awt.Adjustable.addAdjustmentListener
 */
public fun SwingModifier.adjustmentListener(onAdjustment: (AdjustmentEvent) -> Unit): SwingModifier =
    listener<Component, (AdjustmentEvent) -> Unit, AdjustmentListener>(
        callback = onAdjustment,
        adapter = { current -> AdjustmentListener { event -> current()(event) } },
        attach = { component, listener -> component.asAdjustable().addAdjustmentListener(listener) },
        detach = { component, listener -> component.asAdjustable().removeAdjustmentListener(listener) },
    )

/**
 * Attaches an [AdjustmentListener] (`addAdjustmentListener`/`removeAdjustmentListener`) to a scrollbar
 * (`javax.swing.JScrollBar`, `java.awt.Scrollbar`).
 *
 * A scroll pane's own position is hoistable state
 * ([ScrollState][org.jetbrains.compose.swing.components.layout.ScrollState]); this builder is for a
 * scrollbar a custom component drives itself.
 *
 * @see java.awt.Adjustable.addAdjustmentListener
 */
public fun SwingModifier.adjustmentListener(listener: AdjustmentListener): SwingModifier =
    listener<Component, AdjustmentListener>(
        listener,
        { component, instance -> component.asAdjustable().addAdjustmentListener(instance) },
        { component, instance -> component.asAdjustable().removeAdjustmentListener(instance) },
    )

/**
 * Casts to [Adjustable], the interface the scrollbar classes implement. A component that isn't one
 * fails loudly when the chain is built, instead of silently attaching nothing.
 */
private fun Component.asAdjustable(): Adjustable =
    this as? Adjustable
        ?: error(
            "adjustmentListener requires a scrollbar component (JScrollBar, java.awt.Scrollbar), " +
                "but the component is a ${javaClass.name}",
        )
