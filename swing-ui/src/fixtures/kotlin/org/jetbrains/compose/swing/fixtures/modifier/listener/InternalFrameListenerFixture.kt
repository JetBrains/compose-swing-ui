// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.internalFrameListener
import org.jetbrains.compose.swing.node.SwingNode
import javax.swing.JInternalFrame
import javax.swing.event.InternalFrameAdapter
import javax.swing.event.InternalFrameEvent

/** The parameters [InternalFrameListenerFixture] varies: the callback run for each internal frame change. */
public data class InternalFrameListenerParameters(
    val onFrameOpened: (InternalFrameEvent) -> Unit,
    val onFrameClosing: (InternalFrameEvent) -> Unit,
    val onFrameClosed: (InternalFrameEvent) -> Unit,
    val onFrameIconified: (InternalFrameEvent) -> Unit,
    val onFrameDeiconified: (InternalFrameEvent) -> Unit,
    val onFrameActivated: (InternalFrameEvent) -> Unit,
    val onFrameDeactivated: (InternalFrameEvent) -> Unit,
)

/**
 * A [SwingNode] holding a [JInternalFrame] with one [internalFrameListener] callback per change, against a raw
 * [JInternalFrame] with the same callbacks.
 */
public object InternalFrameListenerFixture : ComposableFixture<InternalFrameListenerParameters, JInternalFrame>(
    initial =
        InternalFrameListenerParameters(
            onFrameOpened = {},
            onFrameClosing = {},
            onFrameClosed = {},
            onFrameIconified = {},
            onFrameDeiconified = {},
            onFrameActivated = {},
            onFrameDeactivated = {},
        ),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<InternalFrameListenerParameters> =
        listOf(
            initial.copy(onFrameOpened = {}),
            initial.copy(onFrameClosing = {}),
            initial.copy(onFrameClosed = {}),
            initial.copy(onFrameIconified = {}),
            initial.copy(onFrameDeiconified = {}),
            initial.copy(onFrameActivated = {}),
            initial.copy(onFrameDeactivated = {}),
        )

    @Composable
    override fun Content(parameters: InternalFrameListenerParameters): Unit =
        Content(
            parameters.onFrameOpened,
            parameters.onFrameClosing,
            parameters.onFrameClosed,
            parameters.onFrameIconified,
            parameters.onFrameDeiconified,
            parameters.onFrameActivated,
            parameters.onFrameDeactivated,
        )

    /** Composes an internal frame host that runs each callback on the change it is declared for. */
    @Composable
    public fun Content(
        onFrameOpened: (InternalFrameEvent) -> Unit,
        onFrameClosing: (InternalFrameEvent) -> Unit,
        onFrameClosed: (InternalFrameEvent) -> Unit,
        onFrameIconified: (InternalFrameEvent) -> Unit,
        onFrameDeiconified: (InternalFrameEvent) -> Unit,
        onFrameActivated: (InternalFrameEvent) -> Unit,
        onFrameDeactivated: (InternalFrameEvent) -> Unit,
    ) {
        SwingNode<JInternalFrame>(
            factory = { JInternalFrame() },
            modifier =
                SwingModifier.internalFrameListener(
                    onFrameOpened = onFrameOpened,
                    onFrameClosing = onFrameClosing,
                    onFrameClosed = onFrameClosed,
                    onFrameIconified = onFrameIconified,
                    onFrameDeiconified = onFrameDeiconified,
                    onFrameActivated = onFrameActivated,
                    onFrameDeactivated = onFrameDeactivated,
                ),
        )
    }

    /** Builds a bare [JInternalFrame] before any of the fixture's values are applied. */
    override fun createRawSwing(): JInternalFrame = JInternalFrame()

    /** Adds the listener on the first call; later calls only replace its parameters with those given. */
    override fun updateRawSwing(
        component: JInternalFrame,
        parameters: InternalFrameListenerParameters,
    ) {
        val existing = component.internalFrameListeners.firstNotNullOfOrNull { it as? FixtureListener }
        if (existing == null) {
            component.addInternalFrameListener(FixtureListener(parameters))
        } else {
            existing.parameters = parameters
        }
    }

    /** The listener the raw side attaches, running the callback of its parameters for each change. */
    private class FixtureListener(
        var parameters: InternalFrameListenerParameters,
    ) : InternalFrameAdapter() {
        override fun internalFrameOpened(event: InternalFrameEvent) = parameters.onFrameOpened(event)

        override fun internalFrameClosing(event: InternalFrameEvent) = parameters.onFrameClosing(event)

        override fun internalFrameClosed(event: InternalFrameEvent) = parameters.onFrameClosed(event)

        override fun internalFrameIconified(event: InternalFrameEvent) = parameters.onFrameIconified(event)

        override fun internalFrameDeiconified(event: InternalFrameEvent) = parameters.onFrameDeiconified(event)

        override fun internalFrameActivated(event: InternalFrameEvent) = parameters.onFrameActivated(event)

        override fun internalFrameDeactivated(event: InternalFrameEvent) = parameters.onFrameDeactivated(event)
    }
}
