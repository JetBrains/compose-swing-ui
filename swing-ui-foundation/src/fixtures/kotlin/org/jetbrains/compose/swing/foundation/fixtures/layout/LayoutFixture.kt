// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.fixtures.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Layout
import org.jetbrains.compose.swing.foundation.layout.MeasurePolicy
import org.jetbrains.compose.swing.foundation.layout.constrainHeight
import org.jetbrains.compose.swing.foundation.layout.constrainWidth
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Dimension
import javax.swing.JLabel
import javax.swing.JPanel

/** The parameters [LayoutFixture] varies: the inset the policy places its child at and the size of the container. */
public data class LayoutParameters(
    val inset: Int,
    val width: Int,
    val height: Int,
)

/**
 * A [Layout] whose policy places a label at an inset, against a raw [JPanel] with a label at the same offset and
 * the same size.
 */
public object LayoutFixture : ComposableFixture<LayoutParameters, JPanel>(
    initial = LayoutParameters(inset = 0, width = 180, height = 120),
) {
    override val alternatives: List<LayoutParameters> =
        listOf(
            initial.copy(inset = 8),
            initial.copy(width = 200),
            initial.copy(height = 140),
        )

    @Composable
    override fun Content(parameters: LayoutParameters): Unit =
        Content(inset = parameters.inset, width = parameters.width, height = parameters.height)

    /** Composes a [Layout] of [width] by [height] whose policy places a label [inset] from its start and top. */
    @Composable
    public fun Content(
        inset: Int,
        width: Int,
        height: Int,
    ) {
        val policy = remember(inset) { insetPolicy(inset) }
        Box {
            Layout(measurePolicy = policy, modifier = SwingModifier.size(width, height)) {
                Label(CHILD_TEXT)
            }
        }
    }

    /** Builds the raw panel holding the null-layout panel that holds the label, before any offset or size is set. */
    override fun createRawSwing(): JPanel =
        JPanel(null).apply {
            isOpaque = false
            add(
                JPanel(null).apply {
                    isOpaque = false
                    alignmentX = 0f
                    alignmentY = 0f
                    add(JLabel(CHILD_TEXT))
                },
            )
        }

    /** Sizes the panels of [component] and moves the label to the inset of [parameters]. */
    override fun updateRawSwing(
        component: JPanel,
        parameters: LayoutParameters,
    ) {
        val size = Dimension(parameters.width, parameters.height)
        component.preferredSize = size
        val layout = component.components.single() as JPanel
        layout.preferredSize = size
        layout.maximumSize = size
        layout.setBounds(0, 0, size.width, size.height)
        val label = layout.components.single()
        val labelSize = label.preferredSize
        label.setBounds(parameters.inset, parameters.inset, labelSize.width, labelSize.height)
    }

    private fun insetPolicy(inset: Int): MeasurePolicy =
        MeasurePolicy { measurables, constraints ->
            val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
            val width = constraints.constrainWidth((placeables.maxOfOrNull { it.width } ?: 0) + inset)
            val height = constraints.constrainHeight((placeables.maxOfOrNull { it.height } ?: 0) + inset)
            layout(width, height) {
                placeables.forEach { it.placeRelative(inset, inset) }
            }
        }

    private const val CHILD_TEXT = "Child"
}
