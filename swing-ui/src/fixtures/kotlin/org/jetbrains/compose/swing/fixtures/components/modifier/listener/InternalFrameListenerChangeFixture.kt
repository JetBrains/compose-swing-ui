// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.modifier.listener.internalFrameListener
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import javax.swing.JInternalFrame
import javax.swing.event.InternalFrameAdapter
import javax.swing.event.InternalFrameEvent

/** The parameters [InternalFrameListenerChangeFixture] varies: the one callback run on every frame change. */
public data class InternalFrameListenerChangeParameters(
    val onFrameChange: (InternalFrameEvent) -> Unit,
)

/**
 * A [SwingNode] holding a [JInternalFrame] with one change callback declared through
 * `internalFrameListener(onFrameChange)`, against a raw [JInternalFrame] with the same callback.
 */
public object InternalFrameListenerChangeFixture :
    ComposableFixture<InternalFrameListenerChangeParameters, JInternalFrame>(
        initial = InternalFrameListenerChangeParameters(onFrameChange = {}),
    ) {
    /** The single-change copy of [initial], with a different callback. */
    override val alternatives: List<InternalFrameListenerChangeParameters> =
        listOf(
            initial.copy(onFrameChange = {}),
        )

    @Composable
    override fun Content(parameters: InternalFrameListenerChangeParameters): Unit = Content(parameters.onFrameChange)

    /** Composes an internal frame host that runs [onFrameChange] on every frame change. */
    @Composable
    public fun Content(onFrameChange: (InternalFrameEvent) -> Unit) {
        SwingNode<JInternalFrame>(
            factory = { JInternalFrame() },
            modifier = SwingModifier.internalFrameListener(onFrameChange),
        )
    }

    /** Builds a bare [JInternalFrame] before any of the fixture's values are applied. */
    override fun createRawSwing(): JInternalFrame = JInternalFrame()

    /** Adds the listener on the first call; later calls only replace its callback with the one of [parameters]. */
    override fun updateRawSwing(
        component: JInternalFrame,
        parameters: InternalFrameListenerChangeParameters,
    ) {
        val existing = component.internalFrameListeners.firstNotNullOfOrNull { it as? FixtureListener }
        if (existing == null) {
            component.addInternalFrameListener(FixtureListener(parameters.onFrameChange))
        } else {
            existing.onFrameChange = parameters.onFrameChange
        }
    }

    /** The listener the raw side attaches, running one callback for every change. */
    private class FixtureListener(
        var onFrameChange: (InternalFrameEvent) -> Unit,
    ) : InternalFrameAdapter() {
        override fun internalFrameOpened(event: InternalFrameEvent) = onFrameChange(event)

        override fun internalFrameClosing(event: InternalFrameEvent) = onFrameChange(event)

        override fun internalFrameClosed(event: InternalFrameEvent) = onFrameChange(event)

        override fun internalFrameIconified(event: InternalFrameEvent) = onFrameChange(event)

        override fun internalFrameDeiconified(event: InternalFrameEvent) = onFrameChange(event)

        override fun internalFrameActivated(event: InternalFrameEvent) = onFrameChange(event)

        override fun internalFrameDeactivated(event: InternalFrameEvent) = onFrameChange(event)
    }
}
