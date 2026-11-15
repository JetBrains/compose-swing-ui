// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.annotations.Orientation
import org.jetbrains.compose.swing.components.Separator
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.style.foreground
import java.awt.Color
import javax.swing.JSeparator
import javax.swing.SwingConstants

/** The parameters [SeparatorFixture] varies: the modifier name, the foreground color and the orientation. */
public data class SeparatorParameters(
    val name: String,
    val color: Color,
    @param:Orientation val orientation: Int,
)

/** A [Separator] against a raw [JSeparator] with the same name, foreground color and orientation. */
public object SeparatorFixture : ComposableFixture<SeparatorParameters, JSeparator>(
    initial = SeparatorParameters(name = "Before", color = Color.BLUE, orientation = SwingConstants.HORIZONTAL),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<SeparatorParameters> =
        listOf(
            initial.copy(name = "After"),
            initial.copy(color = Color.RED),
            initial.copy(orientation = SwingConstants.VERTICAL),
        )

    @Composable
    override fun Content(parameters: SeparatorParameters): Unit =
        Content(parameters.name, parameters.color, parameters.orientation)

    /** Composes a [Separator] named [name], drawn in [color], with the given [orientation]. */
    @Composable
    public fun Content(
        name: String,
        color: Color,
        @Orientation orientation: Int,
    ) {
        Separator(
            modifier = SwingModifier.name(name),
            style = { foreground(color) },
            orientation = orientation,
        )
    }

    /** Builds a bare [JSeparator] before any of the fixture's values are applied. */
    override fun createRawSwing(): JSeparator = JSeparator()

    /** Applies the name, foreground color and orientation of [parameters] to [component]. */
    override fun updateRawSwing(
        component: JSeparator,
        parameters: SeparatorParameters,
    ) {
        component.name = parameters.name
        component.foreground = parameters.color
        component.orientation = parameters.orientation
    }
}
