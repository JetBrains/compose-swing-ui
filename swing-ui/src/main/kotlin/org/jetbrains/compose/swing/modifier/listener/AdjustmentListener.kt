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
 */
public fun SwingModifier.adjustmentListener(listener: AdjustmentListener): SwingModifier =
    listener<Component, AdjustmentListener>(
        listener,
        { c, l -> c.asAdjustable().addAdjustmentListener(l) },
        { c, l -> c.asAdjustable().removeAdjustmentListener(l) },
    )

/**
 * The target as the [Adjustable] that declares the adjustment-listener pair, which is the scrollbars -
 * `javax.swing.JScrollBar` and `java.awt.Scrollbar`. A component that is not one is rejected at apply,
 * where the chain is built, rather than silently observing nothing.
 */
private fun Component.asAdjustable(): Adjustable =
    this as? Adjustable
        ?: error(
            "adjustmentListener requires a scrollbar component (JScrollBar, java.awt.Scrollbar), " +
                "but the component is a ${javaClass.name}",
        )
