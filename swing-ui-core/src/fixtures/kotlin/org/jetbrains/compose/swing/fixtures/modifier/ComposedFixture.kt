// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.modifier.composed
import javax.swing.JLabel

/** The parameters [ComposedFixture] varies: the name the composed modifier declares on its label. */
public data class ComposedParameters(
    val name: String,
)

/** A [Label] whose modifier is built by [composed], against a raw [JLabel] carrying the same name. */
public object ComposedFixture : ComposableFixture<ComposedParameters, JLabel>(
    initial = ComposedParameters(name = "before"),
) {
    override val alternatives: List<ComposedParameters> =
        listOf(
            initial.copy(name = "after"),
        )

    @Composable
    override fun Content(parameters: ComposedParameters): Unit = Content(parameters.name)

    /** Composes a [Label] whose [composed] modifier declares [name]. */
    @Composable
    public fun Content(name: String) {
        Label(
            "Composed",
            modifier = SwingModifier.composed { this.name(name) },
        )
    }

    override fun createRawSwing(): JLabel = JLabel("Composed")

    override fun updateRawSwing(
        component: JLabel,
        parameters: ComposedParameters,
    ) {
        component.name = parameters.name
    }
}
