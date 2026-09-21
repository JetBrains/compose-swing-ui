// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.annotations.Orientation
import org.jetbrains.compose.swing.components.layout.Glue
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.enabled
import org.jetbrains.compose.swing.style.foreground
import java.awt.Color
import java.awt.Dimension
import javax.swing.Box
import javax.swing.SwingConstants

/** The parameters [GlueFixture] varies: the enabled state, the foreground color and the axis the glue absorbs along. */
public data class GlueParameters(
    val enabled: Boolean,
    val color: Color,
    @param:Orientation val orientation: Int,
)

/** A [Glue] against a raw [Box.Filler] with the same enabled state, foreground color and orientation. */
public object GlueFixture : ComposableFixture<GlueParameters, Box.Filler>(
    initial = GlueParameters(enabled = true, color = Color.BLUE, orientation = SwingConstants.HORIZONTAL),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<GlueParameters> =
        listOf(
            initial.copy(enabled = false),
            initial.copy(color = Color.RED),
            initial.copy(orientation = SwingConstants.VERTICAL),
        )

    @Composable
    override fun Content(parameters: GlueParameters): Unit =
        Content(parameters.enabled, parameters.color, parameters.orientation)

    /** Composes a [Glue] that is enabled or not, drawn in [color], absorbing along [orientation]. */
    @Composable
    public fun Content(
        enabled: Boolean,
        color: Color,
        @Orientation orientation: Int,
    ) {
        Glue(
            style =
                SwingStyle {
                    this.enabled(enabled)
                    this.foreground(color)
                },
            orientation = orientation,
        )
    }

    /** Builds a bare [Box.Filler] before any of the fixture's values are applied. */
    override fun createRawSwing(): Box.Filler = Box.Filler(Dimension(), Dimension(), Dimension())

    /** Applies the enabled state, foreground color and orientation of [parameters] to [component]. */
    override fun updateRawSwing(
        component: Box.Filler,
        parameters: GlueParameters,
    ) {
        val maximum =
            if (parameters.orientation == SwingConstants.HORIZONTAL) {
                Dimension(Short.MAX_VALUE.toInt(), 0)
            } else {
                Dimension(0, Short.MAX_VALUE.toInt())
            }
        val none = Dimension(0, 0)
        if (component.minimumSize != none || component.preferredSize != none || component.maximumSize != maximum) {
            component.changeShape(none, none, maximum)
        }
        component.isEnabled = parameters.enabled
        component.foreground = parameters.color
    }
}
