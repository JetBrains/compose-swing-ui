// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.VirtualSwingNode
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JLabel
import javax.swing.JPanel

/** The parameters [VirtualSwingNodeFixture] varies: the grid column the group declares and the label text it holds. */
public data class VirtualSwingNodeParameters(
    val gridx: Int,
    val text: String,
)

/** A [VirtualSwingNode] declaring a grid column to its container, against a raw [JPanel] holding the same label. */
public object VirtualSwingNodeFixture : ComposableFixture<VirtualSwingNodeParameters, JPanel>(
    initial = VirtualSwingNodeParameters(gridx = 0, text = "Before"),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<VirtualSwingNodeParameters> =
        listOf(
            initial.copy(gridx = 1),
            initial.copy(text = "After"),
        )

    @Composable
    override fun Content(parameters: VirtualSwingNodeParameters): Unit = Content(parameters.gridx, parameters.text)

    /** Composes a [JPanel] whose [VirtualSwingNode] declares grid column [gridx], holding a [Label] showing [text]. */
    @Composable
    public fun Content(
        gridx: Int,
        text: String,
    ) {
        val constraints = remember(gridx) { GridBagConstraints().apply { this.gridx = gridx } }
        SwingNode(factory = { JPanel(GridBagLayout()) }) {
            VirtualSwingNode(modifier = SwingModifier.layoutConstraint(constraints)) {
                Label(text = text)
            }
        }
    }

    /** Builds a [JPanel] with a [GridBagLayout] holding an empty [JLabel], before any values are applied. */
    override fun createRawSwing(): JPanel = JPanel(GridBagLayout()).apply { add(JLabel(), GridBagConstraints()) }

    /** Applies [parameters] to the label of [component]: its text and, when it changed, its grid column. */
    override fun updateRawSwing(
        component: JPanel,
        parameters: VirtualSwingNodeParameters,
    ) {
        val layout = component.layout as GridBagLayout
        for (label in component.components) {
            if (label is JLabel) {
                label.text = parameters.text
                val constraints = layout.getConstraints(label)
                if (constraints.gridx != parameters.gridx) {
                    constraints.gridx = parameters.gridx
                    layout.setConstraints(label, constraints)
                    component.revalidate()
                }
            }
        }
    }
}
