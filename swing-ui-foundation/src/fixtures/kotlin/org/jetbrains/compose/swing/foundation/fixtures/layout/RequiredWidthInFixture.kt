// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.fixtures.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.requiredWidthIn
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JPanel

/** The width of the child that the composable and the raw Swing side must agree on. */
private const val CHILD_WIDTH = 240

/** The height of the child that the composable and the raw Swing side must agree on. */
private const val CHILD_HEIGHT = 180

/** The parameters [RequiredWidthInFixture] varies. */
public data class RequiredWidthInParameters(
    val min: Int,
    val max: Int,
)

/** A [Box] with [requiredWidthIn] against a raw [JPanel] holding the same width range by hand. */
public object RequiredWidthInFixture : ComposableFixture<RequiredWidthInParameters, JPanel>(
    initial = RequiredWidthInParameters(min = 20, max = 150),
) {
    override val alternatives: List<RequiredWidthInParameters> =
        listOf(
            initial.copy(min = 60),
            initial.copy(max = 100),
        )

    @Composable
    override fun Content(parameters: RequiredWidthInParameters): Unit = Content(parameters.min, parameters.max)

    /** Composes a [Box] whose width is required to lie between [min] and [max] around a child of its own. */
    @Composable
    public fun Content(
        min: Int,
        max: Int,
    ) {
        Box {
            Box(modifier = SwingModifier.requiredWidthIn(min = min, max = max)) {
                Box(modifier = SwingModifier.size(CHILD_WIDTH, CHILD_HEIGHT))
            }
        }
    }

    /**
     * Creates the raw host [JPanel]: a zero-gap [FlowLayout] around the child the range is declared on, which in
     * turn lays out the sized grandchild.
     */
    override fun createRawSwing(): JPanel =
        JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            alignmentX = 0f
            alignmentY = 0f
            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                    isOpaque = false
                    alignmentX = 0f
                    alignmentY = 0f
                    add(
                        JPanel().apply {
                            isOpaque = false
                            preferredSize = Dimension(CHILD_WIDTH, CHILD_HEIGHT)
                        },
                    )
                },
            )
        }

    /** Declares the range of [parameters] as the minimum and maximum size of the child of [component]. */
    override fun updateRawSwing(
        component: JPanel,
        parameters: RequiredWidthInParameters,
    ) {
        val child = component.components.single() as JComponent
        child.minimumSize = Dimension(parameters.min, 0)
        child.maximumSize = Dimension(parameters.max, Int.MAX_VALUE)
        component.revalidate()
    }
}
