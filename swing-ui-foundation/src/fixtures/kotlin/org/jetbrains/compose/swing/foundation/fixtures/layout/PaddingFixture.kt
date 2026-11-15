// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.fixtures.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

/** The parameters [PaddingFixture] varies: the space reserved along each edge of the child. */
public data class PaddingParameters(
    val start: Int,
    val top: Int,
    val end: Int,
    val bottom: Int,
)

/**
 * A [Box] padded along each edge around a fixed-size child, against a raw [JPanel] whose child sits at the same
 * bounds.
 */
public object PaddingFixture : ComposableFixture<PaddingParameters, JPanel>(
    initial = PaddingParameters(start = 4, top = 4, end = 4, bottom = 4),
) {
    override val alternatives: List<PaddingParameters> =
        listOf(
            initial.copy(start = 16),
            initial.copy(top = 16),
            initial.copy(end = 16),
            initial.copy(bottom = 16),
            initial.copy(start = 16, end = 16),
            initial.copy(top = 16, bottom = 16),
            initial.copy(start = 16, top = 16, end = 16, bottom = 16),
        )

    @Composable
    override fun Content(parameters: PaddingParameters): Unit =
        Content(
            start = parameters.start,
            top = parameters.top,
            end = parameters.end,
            bottom = parameters.bottom,
        )

    /** Composes a [Box] padded by [start], [top], [end] and [bottom] around a child of the fixed size. */
    @Composable
    public fun Content(
        start: Int,
        top: Int,
        end: Int,
        bottom: Int,
    ) {
        Box {
            Box(modifier = SwingModifier.padding(start = start, top = top, end = end, bottom = bottom)) {
                Box(modifier = SwingModifier.size(CHILD_WIDTH, CHILD_HEIGHT))
            }
        }
    }

    /**
     * Builds the raw outer panel, which holds the padded panel whose `EmptyBorder` reserves the edges around the
     * fixed-size child.
     */
    override fun createRawSwing(): JPanel =
        JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            alignmentX = 0f
            alignmentY = 0f
            add(
                JPanel(BorderLayout()).apply {
                    isOpaque = false
                    alignmentX = 0f
                    alignmentY = 0f
                    add(
                        JPanel(null).apply {
                            isOpaque = false
                            preferredSize = Dimension(CHILD_WIDTH, CHILD_HEIGHT)
                        },
                        BorderLayout.CENTER,
                    )
                },
            )
        }

    /** Sets the border of the padded panel of [component] to the edges of [parameters] and revalidates it. */
    override fun updateRawSwing(
        component: JPanel,
        parameters: PaddingParameters,
    ) {
        val padded = component.components.single() as JPanel
        padded.border = EmptyBorder(parameters.top, parameters.start, parameters.bottom, parameters.end)
        padded.revalidate()
    }

    /** The width of the fixed-size child. */
    private const val CHILD_WIDTH = 240

    /** The height of the fixed-size child. */
    private const val CHILD_HEIGHT = 180
}
