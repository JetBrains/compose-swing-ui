// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.appearance

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import javax.swing.JLabel

/** The parameters [NameFixture] varies. */
public data class NameParameters(
    val name: String?,
)

/** A [Label] carrying [SwingModifier.name] against a raw [JLabel] with the same component name. */
public object NameFixture : ComposableFixture<NameParameters, JLabel>(
    initial = NameParameters(name = "before"),
) {
    private const val HOST_TEXT = "Modifier"

    override val alternatives: List<NameParameters> =
        listOf(
            initial.copy(name = "after"),
        )

    @Composable
    override fun Content(parameters: NameParameters): Unit = Content(parameters.name)

    /** Composes a [Label] showing the host text, named [name] through [SwingModifier.name]. */
    @Composable
    public fun Content(name: String?) {
        Label(text = HOST_TEXT, modifier = SwingModifier.name(name))
    }

    /** Builds a raw [JLabel] showing the host text, before [updateRawSwing] applies the component name. */
    override fun createRawSwing(): JLabel = JLabel(HOST_TEXT)

    /** Sets the component name of [component] to the name [parameters] carries. */
    override fun updateRawSwing(
        component: JLabel,
        parameters: NameParameters,
    ) {
        component.name = parameters.name
    }
}
