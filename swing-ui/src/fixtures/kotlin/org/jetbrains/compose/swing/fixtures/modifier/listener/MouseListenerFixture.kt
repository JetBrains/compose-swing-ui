// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.mouseListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JLabel

/** The parameters [MouseListenerFixture] varies: the five per-event mouse callbacks. */
public data class MouseListenerParameters(
    val onMouseClicked: (MouseEvent) -> Unit,
    val onMousePressed: (MouseEvent) -> Unit,
    val onMouseReleased: (MouseEvent) -> Unit,
    val onMouseEntered: (MouseEvent) -> Unit,
    val onMouseExited: (MouseEvent) -> Unit,
)

/**
 * A [Label] carrying `mouseListener` with five per-event callbacks against a raw [JLabel] that forwards the same five
 * events.
 */
public object MouseListenerFixture : ComposableFixture<MouseListenerParameters, JLabel>(
    initial =
        MouseListenerParameters(
            onMouseClicked = {},
            onMousePressed = {},
            onMouseReleased = {},
            onMouseEntered = {},
            onMouseExited = {},
        ),
) {
    private const val HOST_TEXT = "Mouse"

    /** The single-change copies of [initial], one for each callback. */
    override val alternatives: List<MouseListenerParameters> =
        listOf(
            initial.copy(onMouseClicked = {}),
            initial.copy(onMousePressed = {}),
            initial.copy(onMouseReleased = {}),
            initial.copy(onMouseEntered = {}),
            initial.copy(onMouseExited = {}),
        )

    @Composable
    override fun Content(parameters: MouseListenerParameters): Unit =
        Content(
            onMouseClicked = parameters.onMouseClicked,
            onMousePressed = parameters.onMousePressed,
            onMouseReleased = parameters.onMouseReleased,
            onMouseEntered = parameters.onMouseEntered,
            onMouseExited = parameters.onMouseExited,
        )

    /** Composes a [Label] whose mouse events run the five given callbacks through the modifier. */
    @Composable
    public fun Content(
        onMouseClicked: (MouseEvent) -> Unit,
        onMousePressed: (MouseEvent) -> Unit,
        onMouseReleased: (MouseEvent) -> Unit,
        onMouseEntered: (MouseEvent) -> Unit,
        onMouseExited: (MouseEvent) -> Unit,
    ) {
        Label(
            text = HOST_TEXT,
            modifier =
                SwingModifier.mouseListener(
                    onMouseClicked = onMouseClicked,
                    onMousePressed = onMousePressed,
                    onMouseReleased = onMouseReleased,
                    onMouseEntered = onMouseEntered,
                    onMouseExited = onMouseExited,
                ),
        )
    }

    /** Builds the raw [JLabel] with the same text and no mouse listener attached yet. */
    override fun createRawSwing(): JLabel = JLabel(HOST_TEXT)

    /** Adds the mouse listener on the first call; later calls only replace its callbacks with those of [parameters]. */
    override fun updateRawSwing(
        component: JLabel,
        parameters: MouseListenerParameters,
    ) {
        val existing = component.mouseListeners.firstNotNullOfOrNull { it as? MouseListenerForwardingListener }
        if (existing == null) {
            component.addMouseListener(
                MouseListenerForwardingListener(
                    onMouseClicked = parameters.onMouseClicked,
                    onMousePressed = parameters.onMousePressed,
                    onMouseReleased = parameters.onMouseReleased,
                    onMouseEntered = parameters.onMouseEntered,
                    onMouseExited = parameters.onMouseExited,
                ),
            )
        } else {
            existing.onMouseClicked = parameters.onMouseClicked
            existing.onMousePressed = parameters.onMousePressed
            existing.onMouseReleased = parameters.onMouseReleased
            existing.onMouseEntered = parameters.onMouseEntered
            existing.onMouseExited = parameters.onMouseExited
        }
    }
}

/** The mouse listener [MouseListenerFixture] installs on its raw component, so that it finds only its own. */
private class MouseListenerForwardingListener(
    var onMouseClicked: (MouseEvent) -> Unit,
    var onMousePressed: (MouseEvent) -> Unit,
    var onMouseReleased: (MouseEvent) -> Unit,
    var onMouseEntered: (MouseEvent) -> Unit,
    var onMouseExited: (MouseEvent) -> Unit,
) : MouseAdapter() {
    override fun mouseClicked(event: MouseEvent) {
        onMouseClicked(event)
    }

    override fun mousePressed(event: MouseEvent) {
        onMousePressed(event)
    }

    override fun mouseReleased(event: MouseEvent) {
        onMouseReleased(event)
    }

    override fun mouseEntered(event: MouseEvent) {
        onMouseEntered(event)
    }

    override fun mouseExited(event: MouseEvent) {
        onMouseExited(event)
    }
}
