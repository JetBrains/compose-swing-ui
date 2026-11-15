// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.fixtures.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Layout
import org.jetbrains.compose.swing.foundation.layout.LayoutParentDataProtocol
import org.jetbrains.compose.swing.foundation.layout.MeasurePolicy
import org.jetbrains.compose.swing.foundation.layout.constrainHeight
import org.jetbrains.compose.swing.foundation.layout.constrainWidth
import org.jetbrains.compose.swing.foundation.layout.layoutParentDataProtocol
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Dimension
import javax.swing.JLabel
import javax.swing.JPanel

/** The parameters [LayoutParentDataProtocolFixture] varies, the parent-data protocol the layout declares. */
public data class LayoutParentDataProtocolParameters(
    val protocol: LayoutParentDataProtocol,
)

/**
 * A [Layout] declaring a parent-data protocol, whose policy places a label at its start and top, against a raw
 * [JPanel] holding a panel of the same size with a label at the same offset; the raw side has no protocol and
 * ignores a new one.
 */
public object LayoutParentDataProtocolFixture : ComposableFixture<LayoutParentDataProtocolParameters, JPanel>(
    initial = LayoutParentDataProtocolParameters(protocol = layoutParentDataProtocol("Before")),
) {
    override val alternatives: List<LayoutParentDataProtocolParameters> =
        listOf(
            initial.copy(protocol = layoutParentDataProtocol("After")),
        )

    @Composable
    override fun Content(parameters: LayoutParentDataProtocolParameters): Unit = Content(protocol = parameters.protocol)

    /**
     * Composes a [Layout] declaring [protocol], whose policy places a label at its start and top.
     */
    @Composable
    public fun Content(protocol: LayoutParentDataProtocol) {
        val policy = remember { startTopPolicy() }
        Box {
            key(protocol) {
                Layout(
                    measurePolicy = policy,
                    modifier = SwingModifier.size(WIDTH, HEIGHT),
                    parentDataProtocol = protocol,
                ) {
                    Label(CHILD_TEXT)
                }
            }
        }
    }

    /** Builds the raw panel holding the sized null-layout panel and its label, offset as the composed layout is. */
    override fun createRawSwing(): JPanel =
        JPanel(null).apply {
            isOpaque = false
            preferredSize = Dimension(WIDTH, HEIGHT)
            add(newLayoutPanel())
        }

    /** Leaves [component] as it is: the raw panel has no parent-data protocol for [parameters] to change. */
    override fun updateRawSwing(
        component: JPanel,
        parameters: LayoutParentDataProtocolParameters,
    ): Unit = Unit

    private fun newLayoutPanel(): JPanel =
        JPanel(null).apply {
            isOpaque = false
            alignmentX = 0f
            alignmentY = 0f
            val size = Dimension(WIDTH, HEIGHT)
            preferredSize = size
            maximumSize = size
            setBounds(0, 0, WIDTH, HEIGHT)
            val label = JLabel(CHILD_TEXT)
            add(label)
            val labelSize = label.preferredSize
            label.setBounds(0, 0, labelSize.width, labelSize.height)
        }

    private fun startTopPolicy(): MeasurePolicy =
        MeasurePolicy { measurables, constraints ->
            val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
            val width = constraints.constrainWidth(placeables.maxOfOrNull { it.width } ?: 0)
            val height = constraints.constrainHeight(placeables.maxOfOrNull { it.height } ?: 0)
            layout(width, height) {
                placeables.forEach { it.placeRelative(0, 0) }
            }
        }

    private const val CHILD_TEXT = "Child"
    private const val WIDTH = 180
    private const val HEIGHT = 120
}
