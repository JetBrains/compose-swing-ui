// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.layout

import org.jetbrains.compose.swing.annotations.SwingComponentLibraryApi
import java.awt.Component
import java.awt.Container

/**
 * An attachment to a host of type [H] that holds children through a dedicated setter rather than
 * `Container.add`.
 */
@SwingComponentLibraryApi
public fun interface SlotAttachment<in H : Container> {
    /**
     * Attaches [component] to [host], returning the action that detaches it again.
     *
     * An attachment for an ordered host must detach by component identity: sibling positions can change.
     */
    public fun install(
        host: H,
        component: Component,
        index: Int,
    ): () -> Unit
}
