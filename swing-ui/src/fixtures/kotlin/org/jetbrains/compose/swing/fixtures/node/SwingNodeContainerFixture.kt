// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.node

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.node.SwingNode
import javax.swing.JLabel
import javax.swing.JPanel

/** The parameters [SwingNodeContainerFixture] varies. */
public data class SwingNodeContainerParameters(
    val labelText: String,
)

/** A container [SwingNode] backed by a [JPanel] holding one label, against a raw [JPanel] holding the same label. */
public object SwingNodeContainerFixture : ComposableFixture<SwingNodeContainerParameters, JPanel>(
    initial = SwingNodeContainerParameters(labelText = "Before"),
) {
    override val alternatives: List<SwingNodeContainerParameters> =
        listOf(
            initial.copy(labelText = "After"),
        )

    @Composable
    override fun Content(parameters: SwingNodeContainerParameters): Unit = Content(parameters.labelText)

    /** Composes a [SwingNode] panel named "Before" holding a [Label] that shows [labelText]. */
    @Composable
    public fun Content(labelText: String) {
        SwingNode(
            factory = { JPanel() },
            modifier = SwingModifier.name("Before"),
        ) {
            Label(text = labelText)
        }
    }

    /** Builds a [JPanel] named "Before" holding an empty [JLabel]. */
    override fun createRawSwing(): JPanel =
        JPanel().apply {
            name = "Before"
            add(JLabel())
        }

    /** Applies [parameters] to [component]: the text of its label. */
    override fun updateRawSwing(
        component: JPanel,
        parameters: SwingNodeContainerParameters,
    ) {
        for (child in component.components) {
            if (child is JLabel) {
                child.text = parameters.labelText
            }
        }
    }
}
