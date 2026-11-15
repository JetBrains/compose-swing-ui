// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.layout.Spacer
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.enabled
import org.jetbrains.compose.swing.style.foreground
import java.awt.Color
import java.awt.Dimension
import javax.swing.Box

/** The parameters [SpacerFixture] varies: the square size, whether the spacer is enabled and its foreground color. */
public data class SpacerParameters(
    val size: Int,
    val enabled: Boolean,
    val color: Color,
)

/** A [Spacer] against a raw `Box.Filler` of the same square size, enabled state and foreground color. */
public object SpacerFixture : ComposableFixture<SpacerParameters, Box.Filler>(
    initial = SpacerParameters(size = 10, enabled = true, color = Color.BLUE),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<SpacerParameters> =
        listOf(
            initial.copy(size = 30),
            initial.copy(enabled = false),
            initial.copy(color = Color.RED),
        )

    @Composable
    override fun Content(parameters: SpacerParameters): Unit =
        Content(parameters.size, parameters.enabled, parameters.color)

    /** Composes a [Spacer] of [size] on both axes, enabled or not as [enabled] says, drawn in [color]. */
    @Composable
    public fun Content(
        size: Int,
        enabled: Boolean,
        color: Color,
    ) {
        Spacer(
            size = size,
            style =
                SwingStyle {
                    enabled(enabled)
                    foreground(color)
                },
        )
    }

    /** Builds a bare `Box.Filler` with empty sizes before any of the fixture's values are applied. */
    override fun createRawSwing(): Box.Filler = Box.Filler(Dimension(), Dimension(), Dimension())

    /** Applies the square size, enabled state and foreground color of [parameters] to [component]. */
    override fun updateRawSwing(
        component: Box.Filler,
        parameters: SpacerParameters,
    ) {
        val square = Dimension(parameters.size, parameters.size)
        if (component.minimumSize != square || component.preferredSize != square || component.maximumSize != square) {
            component.changeShape(square, square, square)
        }
        component.isEnabled = parameters.enabled
        component.foreground = parameters.color
    }
}
