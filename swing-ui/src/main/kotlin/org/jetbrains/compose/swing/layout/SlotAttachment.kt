package org.jetbrains.compose.swing.layout

import java.awt.Component
import java.awt.Container

/** An attachment to a host that holds children through a dedicated setter rather than `Container.add`. */
public fun interface SlotAttachment {
    /**
     * Attaches [component] to [host], returning the action that detaches it again.
     *
     * An attachment for an ordered host must detach by component identity: sibling positions can change.
     */
    public fun install(
        host: Container,
        component: Component,
        index: Int,
    ): () -> Unit
}
