// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.node

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.node.SwingNode
import javax.swing.JLabel

/** The parameters [SwingNodeFixture] varies. */
public data class SwingNodeParameters(
    val name: String,
    val text: String,
)

/** A leaf [SwingNode] backed by a [JLabel] against a raw [JLabel] with the same name and text. */
public object SwingNodeFixture : ComposableFixture<SwingNodeParameters, JLabel>(
    initial = SwingNodeParameters(name = "Before", text = "Before"),
) {
    override val alternatives: List<SwingNodeParameters> =
        listOf(
            initial.copy(name = "After"),
            initial.copy(text = "After"),
        )

    @Composable
    override fun Content(parameters: SwingNodeParameters): Unit = Content(parameters.name, parameters.text)

    /** Composes a [SwingNode] whose label is named [name] and shows [text]. */
    @Composable
    public fun Content(
        name: String,
        text: String,
    ) {
        SwingNode(
            factory = { JLabel() },
            modifier = SwingModifier.name(name),
            update = { set(text) { this.text = it } },
        )
    }

    /** Builds an empty [JLabel]. */
    override fun createRawSwing(): JLabel = JLabel()

    /** Applies [parameters] to [component]: its name and its text. */
    override fun updateRawSwing(
        component: JLabel,
        parameters: SwingNodeParameters,
    ) {
        component.name = parameters.name
        component.text = parameters.text
    }
}
