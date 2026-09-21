// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.interaction

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.interaction.onPointerEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JLabel

/** The parameters [OnPointerEventFixture] varies: the press, release and click callbacks, each optional. */
public data class OnPointerEventParameters(
    val onPress: ((MouseEvent) -> Unit)?,
    val onRelease: ((MouseEvent) -> Unit)?,
    val onClick: ((MouseEvent) -> Unit)?,
)

/**
 * A [Label] carrying `onPointerEvent` against a raw [JLabel] that forwards the same pointer events to the same
 * callbacks.
 */
public object OnPointerEventFixture : ComposableFixture<OnPointerEventParameters, JLabel>(
    initial =
        OnPointerEventParameters(
            onPress = {},
            onRelease = {},
            onClick = {},
        ),
) {
    private const val HOST_TEXT = "Pointer"

    /** The single-change copies of [initial], each leaving one pointer callback undeclared. */
    override val alternatives: List<OnPointerEventParameters> =
        listOf(
            initial.copy(onPress = null),
            initial.copy(onRelease = null),
            initial.copy(onClick = null),
        )

    @Composable
    override fun Content(parameters: OnPointerEventParameters): Unit =
        Content(
            onPress = parameters.onPress,
            onRelease = parameters.onRelease,
            onClick = parameters.onClick,
        )

    /** Composes a [Label] whose press, release and click events run the given callbacks through the modifier. */
    @Composable
    public fun Content(
        onPress: ((MouseEvent) -> Unit)?,
        onRelease: ((MouseEvent) -> Unit)?,
        onClick: ((MouseEvent) -> Unit)?,
    ) {
        Label(
            text = HOST_TEXT,
            modifier =
                SwingModifier.onPointerEvent(
                    onPress = onPress,
                    onRelease = onRelease,
                    onClick = onClick,
                ),
        )
    }

    /** Builds the raw [JLabel] with the same text and no mouse listener attached yet. */
    override fun createRawSwing(): JLabel = JLabel(HOST_TEXT)

    /**
     * Adds the mouse listener on the first call; later calls only replace its callbacks with those of
     * [parameters]. Unrelated mouse listeners remain.
     */
    override fun updateRawSwing(
        component: JLabel,
        parameters: OnPointerEventParameters,
    ) {
        val existing = component.mouseListeners.firstNotNullOfOrNull { it as? ForwardingMouseListener }
        if (existing == null) {
            component.addMouseListener(
                ForwardingMouseListener(parameters.onPress, parameters.onRelease, parameters.onClick),
            )
        } else {
            existing.onPress = parameters.onPress
            existing.onRelease = parameters.onRelease
            existing.onClick = parameters.onClick
        }
    }
}

/** The mouse listener [OnPointerEventFixture] installs on its raw component, so that it finds only its own. */
private class ForwardingMouseListener(
    var onPress: ((MouseEvent) -> Unit)?,
    var onRelease: ((MouseEvent) -> Unit)?,
    var onClick: ((MouseEvent) -> Unit)?,
) : MouseAdapter() {
    override fun mousePressed(event: MouseEvent) {
        onPress?.invoke(event)
    }

    override fun mouseReleased(event: MouseEvent) {
        onRelease?.invoke(event)
    }

    override fun mouseClicked(event: MouseEvent) {
        onClick?.invoke(event)
    }
}
