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
 * @param onAdjustment receives the event, whose `value` is the scrollbar's current value and whose
 *   `valueIsAdjusting` is `true` while the user is still dragging the knob.
 * @return this chain with the adjustment callback declared on it.
 * @see java.awt.Adjustable.addAdjustmentListener
 */
public fun SwingModifier.adjustmentListener(onAdjustment: (AdjustmentEvent) -> Unit): SwingModifier =
    listener(onAdjustment, ADJUSTMENT_CALLBACKS)

/**
 * Attaches an [AdjustmentListener] (`addAdjustmentListener`/`removeAdjustmentListener`) to a scrollbar
 * (`javax.swing.JScrollBar`, `java.awt.Scrollbar`).
 *
 * A scroll pane's own position is hoistable state
 * ([ScrollState][org.jetbrains.compose.swing.components.layout.ScrollState]); this builder is for a
 * scrollbar a custom component drives itself.
 *
 * @param listener attached by identity, so a remembered instance stays put while a fresh one on every
 *   pass is detached and re-added.
 * @return this chain with the adjustment listener declared on it.
 * @see java.awt.Adjustable.addAdjustmentListener
 */
public fun SwingModifier.adjustmentListener(listener: AdjustmentListener): SwingModifier =
    listener(listener, ADJUSTMENT)

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

private val ADJUSTMENT =
    ListenerRegistration<Component, AdjustmentListener>(
        name = "adjustmentListener",
        { component, listener -> component.asAdjustable().addAdjustmentListener(listener) },
        { component, listener -> component.asAdjustable().removeAdjustmentListener(listener) },
    )

private val ADJUSTMENT_CALLBACKS =
    CallbackRegistration<Component, (AdjustmentEvent) -> Unit, AdjustmentListener>(
        adapter = { current -> AdjustmentListener { event -> current()(event) } },
        registration = ADJUSTMENT,
    )
