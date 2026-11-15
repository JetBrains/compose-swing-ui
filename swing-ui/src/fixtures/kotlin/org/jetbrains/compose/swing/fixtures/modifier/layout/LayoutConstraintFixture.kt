// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel

/** The parameters [LayoutConstraintFixture] varies: the placement constraint of the child in its host. */
public data class LayoutConstraintParameters(
    val constraint: String,
)

/** A [SwingModifier.layoutConstraint] on a label in a host panel against a raw [JPanel] holding the same label. */
public object LayoutConstraintFixture : ComposableFixture<LayoutConstraintParameters, JPanel>(
    initial = LayoutConstraintParameters(constraint = BorderLayout.NORTH),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<LayoutConstraintParameters> =
        listOf(
            initial.copy(constraint = BorderLayout.SOUTH),
        )

    /** The text of the placed label, shared by the composed and the raw side so that they agree. */
    private const val PLACED_TEXT = "Placed"

    @Composable
    override fun Content(parameters: LayoutConstraintParameters): Unit = Content(parameters.constraint)

    /** Composes a border-layout host panel holding a [Label] placed under [constraint]. */
    @Composable
    public fun Content(constraint: String) {
        SwingNode(factory = { JPanel(BorderLayout()) }) {
            Label(
                PLACED_TEXT,
                modifier = SwingModifier.layoutConstraint(constraint),
            )
        }
    }

    /** Builds a bare [JPanel] with a border layout before any of the fixture's values are applied. */
    override fun createRawSwing(): JPanel = JPanel(BorderLayout())

    /** Places the label of [component] under the constraint of [parameters], and asks the host to lay out again. */
    override fun updateRawSwing(
        component: JPanel,
        parameters: LayoutConstraintParameters,
    ) {
        val label = component.components.firstOrNull() ?: JLabel(PLACED_TEXT)
        component.add(label, parameters.constraint)
        component.revalidate()
    }
}
