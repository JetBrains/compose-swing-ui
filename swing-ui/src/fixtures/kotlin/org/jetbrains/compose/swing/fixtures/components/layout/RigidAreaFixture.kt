// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.layout.RigidArea
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.enabled
import org.jetbrains.compose.swing.style.foreground
import java.awt.Color
import java.awt.Dimension
import javax.swing.Box

/** The parameters [RigidAreaFixture] varies: the fixed size, the enabled state and the foreground color. */
public data class RigidAreaParameters(
    val width: Int,
    val height: Int,
    val enabled: Boolean,
    val color: Color,
)

/** A [RigidArea] against a raw [Box.Filler] of the same fixed size, enabled state and foreground color. */
public object RigidAreaFixture : ComposableFixture<RigidAreaParameters, Box.Filler>(
    initial = RigidAreaParameters(width = 10, height = 15, enabled = true, color = Color.BLUE),
) {
    /** The parameter sets that change one value of [initial] each. */
    override val alternatives: List<RigidAreaParameters> =
        listOf(
            initial.copy(width = 30),
            initial.copy(height = 25),
            initial.copy(enabled = false),
            initial.copy(color = Color.RED),
        )

    @Composable
    override fun Content(parameters: RigidAreaParameters): Unit =
        Content(
            width = parameters.width,
            height = parameters.height,
            enabled = parameters.enabled,
            color = parameters.color,
        )

    /** Composes a [RigidArea] of [width] by [height], enabled as [enabled], with a foreground of [color]. */
    @Composable
    public fun Content(
        width: Int,
        height: Int,
        enabled: Boolean,
        color: Color,
    ) {
        RigidArea(
            width = width,
            height = height,
            style =
                SwingStyle {
                    enabled(enabled)
                    foreground(color)
                },
        )
    }

    /** Builds an empty filler whose sizes are still unset, before [updateRawSwing] applies the parameters. */
    override fun createRawSwing(): Box.Filler = Box.Filler(Dimension(), Dimension(), Dimension())

    /** Sets the filler's fixed size, enabled state and foreground color to [parameters]. */
    override fun updateRawSwing(
        component: Box.Filler,
        parameters: RigidAreaParameters,
    ) {
        val size = Dimension(parameters.width, parameters.height)
        if (component.minimumSize != size || component.preferredSize != size || component.maximumSize != size) {
            component.changeShape(size, size, size)
        }
        component.isEnabled = parameters.enabled
        component.foreground = parameters.color
    }
}
