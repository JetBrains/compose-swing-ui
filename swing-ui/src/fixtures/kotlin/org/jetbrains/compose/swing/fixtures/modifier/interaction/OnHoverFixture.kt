// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.interaction

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.interaction.onHover
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JLabel

/** The parameters [OnHoverFixture] varies: the callbacks run when the pointer enters and leaves. */
public data class OnHoverParameters(
    val onEnter: () -> Unit,
    val onExit: () -> Unit,
)

/** A [Label] carrying `onHover` against a raw [JLabel] whose mouse listener runs the same callbacks. */
public object OnHoverFixture : ComposableFixture<OnHoverParameters, JLabel>(
    initial = OnHoverParameters(onEnter = {}, onExit = {}),
) {
    private const val TEXT = "Hover"

    /** The single-change copies of [initial], each swapping one callback for a fresh one. */
    override val alternatives: List<OnHoverParameters> =
        listOf(
            initial.copy(onEnter = {}),
            initial.copy(onExit = {}),
        )

    @Composable
    override fun Content(parameters: OnHoverParameters): Unit = Content(parameters.onEnter, parameters.onExit)

    /** Composes a [Label] whose hover is reported to [onEnter] and [onExit] through the modifier. */
    @Composable
    public fun Content(
        onEnter: () -> Unit,
        onExit: () -> Unit,
    ) {
        Label(text = TEXT, modifier = SwingModifier.onHover(onEnter = onEnter, onExit = onExit))
    }

    /** Builds the raw [JLabel] with the same text and no hover listener attached yet. */
    override fun createRawSwing(): JLabel = JLabel(TEXT)

    /** Adds the hover listener on the first call; later calls only replace its callbacks with those of [parameters]. */
    override fun updateRawSwing(
        component: JLabel,
        parameters: OnHoverParameters,
    ) {
        val existing = component.mouseListeners.firstNotNullOfOrNull { it as? HoverListener }
        if (existing == null) {
            component.addMouseListener(HoverListener(parameters.onEnter, parameters.onExit))
        } else {
            existing.onEnter = parameters.onEnter
            existing.onExit = parameters.onExit
        }
    }

    /** The raw hover listener the fixture installs, told apart from listeners it did not add. */
    private class HoverListener(
        var onEnter: () -> Unit,
        var onExit: () -> Unit,
    ) : MouseAdapter() {
        override fun mouseEntered(event: MouseEvent) {
            onEnter()
        }

        override fun mouseExited(event: MouseEvent) {
            onExit()
        }
    }
}
