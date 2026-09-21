// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.annotations.Orientation
import org.jetbrains.compose.swing.components.layout.Strut
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.enabled
import org.jetbrains.compose.swing.style.foreground
import java.awt.Color
import java.awt.Dimension
import javax.swing.Box
import javax.swing.SwingConstants

/** The parameters [StrutFixture] varies: the orientation, the size, the enabled state and the foreground color. */
public data class StrutParameters(
    @param:Orientation val orientation: Int,
    val size: Int,
    val enabled: Boolean,
    val color: Color,
)

/** A [Strut] against a raw [Box.Filler] with the same orientation, size, enabled state and foreground color. */
public object StrutFixture : ComposableFixture<StrutParameters, Box.Filler>(
    initial =
        StrutParameters(
            orientation = SwingConstants.HORIZONTAL,
            size = 10,
            enabled = true,
            color = Color.BLUE,
        ),
) {
    /** The parameter sets that change one value of [initial] each. */
    override val alternatives: List<StrutParameters> =
        listOf(
            initial.copy(orientation = SwingConstants.VERTICAL),
            initial.copy(size = 30),
            initial.copy(enabled = false),
            initial.copy(color = Color.RED),
        )

    @Composable
    override fun Content(parameters: StrutParameters): Unit =
        Content(
            orientation = parameters.orientation,
            size = parameters.size,
            enabled = parameters.enabled,
            color = parameters.color,
        )

    /** Composes a [Strut] along [orientation] of [size], enabled as [enabled], with a foreground of [color]. */
    @Composable
    public fun Content(
        @Orientation orientation: Int,
        size: Int,
        enabled: Boolean,
        color: Color,
    ) {
        Strut(
            orientation = orientation,
            size = size,
            style =
                SwingStyle {
                    enabled(enabled)
                    foreground(color)
                },
        )
    }

    /** Builds an empty filler whose sizes are still unset, before [updateRawSwing] applies the parameters. */
    override fun createRawSwing(): Box.Filler = Box.Filler(Dimension(), Dimension(), Dimension())

    /** Sets the filler's extent along the orientation, its enabled state and its foreground color to [parameters]. */
    override fun updateRawSwing(
        component: Box.Filler,
        parameters: StrutParameters,
    ) {
        val horizontal = parameters.orientation == SwingConstants.HORIZONTAL
        val fixed = if (horizontal) Dimension(parameters.size, 0) else Dimension(0, parameters.size)
        val maximum =
            if (horizontal) {
                Dimension(parameters.size, Short.MAX_VALUE.toInt())
            } else {
                Dimension(Short.MAX_VALUE.toInt(), parameters.size)
            }
        if (component.minimumSize != fixed || component.preferredSize != fixed || component.maximumSize != maximum) {
            component.changeShape(fixed, fixed, maximum)
        }
        component.isEnabled = parameters.enabled
        component.foreground = parameters.color
    }
}
