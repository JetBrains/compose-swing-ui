// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.internalFrameListener
import org.jetbrains.compose.swing.node.SwingNode
import javax.swing.JInternalFrame
import javax.swing.event.InternalFrameAdapter
import javax.swing.event.InternalFrameListener

/** The parameters [InternalFrameListenerListenerFixture] varies: the listener attached to the frame. */
public data class InternalFrameListenerListenerParameters(
    val listener: InternalFrameListener,
)

/**
 * A [SwingNode] holding a [JInternalFrame] with an [internalFrameListener] listener, against a raw [JInternalFrame]
 * with the same listener.
 */
public object InternalFrameListenerListenerFixture :
    ComposableFixture<InternalFrameListenerListenerParameters, JInternalFrame>(
        initial = InternalFrameListenerListenerParameters(listener = object : InternalFrameAdapter() {}),
    ) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<InternalFrameListenerListenerParameters> =
        listOf(
            initial.copy(listener = object : InternalFrameAdapter() {}),
        )

    @Composable
    override fun Content(parameters: InternalFrameListenerListenerParameters): Unit = Content(parameters.listener)

    /** Composes an internal frame host with [listener] attached. */
    @Composable
    public fun Content(listener: InternalFrameListener) {
        SwingNode<JInternalFrame>(
            factory = { JInternalFrame() },
            modifier = SwingModifier.internalFrameListener(listener),
        )
    }

    /** Builds a bare [JInternalFrame] before any of the fixture's values are applied. */
    override fun createRawSwing(): JInternalFrame = JInternalFrame()

    /** Replaces the listener this fixture last added to [component] with the listener of [parameters]. */
    override fun updateRawSwing(
        component: JInternalFrame,
        parameters: InternalFrameListenerListenerParameters,
    ) {
        component.removeInternalFrameListener(initial.listener)
        alternatives.forEach { component.removeInternalFrameListener(it.listener) }
        component.addInternalFrameListener(parameters.listener)
    }
}
