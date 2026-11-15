// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.fixtures.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.width
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JPanel

/** The parameters [WidthFixture] varies. */
public data class WidthParameters(
    val width: Int,
)

/** A [Box] carrying [SwingModifier.width] against a raw [JPanel] with the same exact width. */
public object WidthFixture : ComposableFixture<WidthParameters, JPanel>(
    initial = WidthParameters(width = 40),
) {
    /** The parameter changes the tests and benchmarks switch between, one per changed value. */
    override val alternatives: List<WidthParameters> =
        listOf(
            initial.copy(width = 80),
        )

    @Composable
    override fun Content(parameters: WidthParameters): Unit = Content(width = parameters.width)

    /** Composes a [Box] whose width is preferred to be exactly [width]. */
    @Composable
    public fun Content(width: Int) {
        Box {
            Box(modifier = SwingModifier.width(width = width))
        }
    }

    /** Builds the raw panel with an empty child that the exact width is applied to. */
    override fun createRawSwing(): JPanel =
        plainPanel().apply {
            alignmentX = 0f
            alignmentY = 0f
            layout = FlowLayout(FlowLayout.LEFT, 0, 0)
            add(
                plainPanel().apply {
                    alignmentX = 0f
                    alignmentY = 0f
                },
            )
        }

    /**
     * Applies [parameters] to [component], so its child's width matches the one the modifier declares, as a preferred
     * size that the flow layout of the host honors.
     */
    override fun updateRawSwing(
        component: JPanel,
        parameters: WidthParameters,
    ) {
        val child = component.components.single()
        child.preferredSize = Dimension(parameters.width, 0)
        component.revalidate()
    }

    // A composed Box is a plain JComponent that holds no look-and-feel font or colors, so its raw stand-in holds none.
    private fun plainPanel(): JPanel =
        JPanel().apply {
            font = null
            foreground = null
            background = null
            isOpaque = false
        }
}
