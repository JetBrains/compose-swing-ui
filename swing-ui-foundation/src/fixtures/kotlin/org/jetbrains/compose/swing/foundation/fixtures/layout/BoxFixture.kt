// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.fixtures.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.OverlayLayout

/** The parameters [BoxFixture] varies. */
public data class BoxParameters(
    val width: Int,
    val height: Int,
    val contentAlignment: Alignment,
    val propagateMinConstraints: Boolean,
    val text: String,
)

/** A fixed-size [Box] holding one label, against a raw [JPanel] with an [OverlayLayout] aligning the same label. */
public object BoxFixture : ComposableFixture<BoxParameters, JPanel>(
    initial =
        BoxParameters(
            width = 160,
            height = 100,
            contentAlignment = Alignment.TopStart,
            propagateMinConstraints = false,
            text = "Before",
        ),
) {
    override val alternatives: List<BoxParameters> =
        listOf(
            initial.copy(width = 180),
            initial.copy(height = 120),
            initial.copy(contentAlignment = Alignment.Center),
            initial.copy(contentAlignment = Alignment.BottomEnd),
            initial.copy(propagateMinConstraints = true),
            initial.copy(text = "After"),
        )

    @Composable
    override fun Content(parameters: BoxParameters): Unit =
        Content(
            width = parameters.width,
            height = parameters.height,
            contentAlignment = parameters.contentAlignment,
            propagateMinConstraints = parameters.propagateMinConstraints,
            text = parameters.text,
        )

    /** Composes a [Box] of [width] by [height] with a [Label] showing [text], placed by [contentAlignment]. */
    @Composable
    public fun Content(
        width: Int,
        height: Int,
        contentAlignment: Alignment,
        propagateMinConstraints: Boolean,
        text: String,
    ) {
        Box {
            Box(
                modifier = SwingModifier.size(width, height),
                contentAlignment = contentAlignment,
                propagateMinConstraints = propagateMinConstraints,
            ) {
                Label(text = text)
            }
        }
    }

    /** Builds the raw panel holding the sized panel that holds the label, before any size or placement is applied. */
    override fun createRawSwing(): JPanel =
        JPanel(FlowLayout(FlowLayout.LEADING, 0, 0)).apply {
            isOpaque = false
            add(
                JPanel().apply {
                    isOpaque = false
                    layout = OverlayLayout(this)
                    add(JLabel())
                },
            )
        }

    /** Gives the panel of [component] its declared size and the label the alignment and size [parameters] declare. */
    override fun updateRawSwing(
        component: JPanel,
        parameters: BoxParameters,
    ) {
        val box = component.components.single() as JPanel
        val size = Dimension(parameters.width, parameters.height)
        box.preferredSize = size
        box.minimumSize = size
        box.maximumSize = size
        val label = box.components.single() as JLabel
        label.text = parameters.text
        when (parameters.contentAlignment) {
            Alignment.Center -> {
                label.alignmentX = Component.CENTER_ALIGNMENT
                label.alignmentY = Component.CENTER_ALIGNMENT
            }

            Alignment.BottomEnd -> {
                label.alignmentX = Component.RIGHT_ALIGNMENT
                label.alignmentY = Component.BOTTOM_ALIGNMENT
            }

            else -> {
                label.alignmentX = Component.LEFT_ALIGNMENT
                label.alignmentY = Component.TOP_ALIGNMENT
            }
        }
        component.revalidate()
    }
}
