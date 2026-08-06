@file:JvmMultifileClass
@file:JvmName("ListenerModifierKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Adjustable
import java.awt.Component
import java.awt.event.AdjustmentListener

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
        { c, l -> c.asAdjustable().addAdjustmentListener(l) },
        { c, l -> c.asAdjustable().removeAdjustmentListener(l) },
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
