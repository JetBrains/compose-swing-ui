// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.mouseMotionListener
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import javax.swing.JLabel

/** The parameters [MouseMotionListenerFixture] varies: the callbacks run on a drag and on a plain move. */
public data class MouseMotionListenerParameters(
    val onMouseDragged: (MouseEvent) -> Unit,
    val onMouseMoved: (MouseEvent) -> Unit,
)

/**
 * A [SwingNode] holding a [JLabel] with [SwingModifier.mouseMotionListener] callbacks, against a raw [JLabel]
 * with the same callbacks attached by hand.
 */
public object MouseMotionListenerFixture : ComposableFixture<MouseMotionListenerParameters, JLabel>(
    initial = MouseMotionListenerParameters(onMouseDragged = {}, onMouseMoved = {}),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<MouseMotionListenerParameters> =
        listOf(
            initial.copy(onMouseDragged = {}),
            initial.copy(onMouseMoved = {}),
        )

    @Composable
    override fun Content(parameters: MouseMotionListenerParameters): Unit =
        Content(parameters.onMouseDragged, parameters.onMouseMoved)

    /** Composes a label host that runs [onMouseDragged] with a button held and [onMouseMoved] without one. */
    @Composable
    public fun Content(
        onMouseDragged: (MouseEvent) -> Unit,
        onMouseMoved: (MouseEvent) -> Unit,
    ) {
        Label(
            text = "",
            modifier =
                SwingModifier.mouseMotionListener(
                    onMouseDragged = onMouseDragged,
                    onMouseMoved = onMouseMoved,
                ),
        )
    }

    /** Builds a bare [JLabel] before any of the fixture's values are applied. */
    override fun createRawSwing(): JLabel = JLabel()

    /** Adds the listener on the first call; later calls only replace its callbacks with those of [parameters]. */
    override fun updateRawSwing(
        component: JLabel,
        parameters: MouseMotionListenerParameters,
    ) {
        val existing = component.mouseMotionListeners.firstNotNullOfOrNull { it as? MotionForwarder }
        if (existing == null) {
            component.addMouseMotionListener(MotionForwarder(parameters.onMouseDragged, parameters.onMouseMoved))
        } else {
            existing.onMouseDragged = parameters.onMouseDragged
            existing.onMouseMoved = parameters.onMouseMoved
        }
    }
}

/** The listener [MouseMotionListenerFixture] adds to its raw component, so that it finds only its own. */
private class MotionForwarder(
    var onMouseDragged: (MouseEvent) -> Unit,
    var onMouseMoved: (MouseEvent) -> Unit,
) : MouseMotionAdapter() {
    override fun mouseDragged(event: MouseEvent) {
        onMouseDragged(event)
    }

    override fun mouseMoved(event: MouseEvent) {
        onMouseMoved(event)
    }
}
