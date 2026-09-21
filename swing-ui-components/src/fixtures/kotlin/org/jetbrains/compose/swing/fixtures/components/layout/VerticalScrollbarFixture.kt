// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.enabled
import org.jetbrains.compose.swing.style.foreground
import org.jetbrains.compose.swing.style.preferredSize
import java.awt.Color
import java.awt.Dimension
import javax.swing.JLabel
import javax.swing.JScrollPane

/** The parameters [VerticalScrollbarFixture] varies: whether the scroll bar is enabled and its foreground color. */
public data class VerticalScrollbarParameters(
    val enabled: Boolean,
    val color: Color,
)

/** A [ScrollPane]'s vertical scroll bar against the raw vertical scroll bar of a [JScrollPane]. */
public object VerticalScrollbarFixture : ComposableFixture<VerticalScrollbarParameters, JScrollPane>(
    initial = VerticalScrollbarParameters(enabled = true, color = Color.BLUE),
) {
    private const val VIEW_SIZE = 1000
    private const val PANE_HEIGHT = 120
    private const val PANE_WIDTH = 180

    override val alternatives: List<VerticalScrollbarParameters> =
        listOf(
            initial.copy(enabled = false),
            initial.copy(color = Color.RED),
        )

    @Composable
    override fun Content(parameters: VerticalScrollbarParameters): Unit = Content(parameters.enabled, parameters.color)

    /** Composes a [ScrollPane] whose vertical scroll bar is enabled by [enabled] and drawn in [color]. */
    @Composable
    public fun Content(
        enabled: Boolean,
        color: Color,
    ) {
        ScrollPane(
            style = SwingStyle { preferredSize(PANE_WIDTH, PANE_HEIGHT) },
            verticalScrollbar = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
            horizontalScrollbar = JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS,
        ) {
            VerticalScrollbar(
                style =
                    SwingStyle {
                        enabled(enabled)
                        foreground(color)
                    },
            )
            Viewport {
                Label("Position: 0, 0", style = SwingStyle { preferredSize(VIEW_SIZE, VIEW_SIZE) })
            }
        }
    }

    override fun createRawSwing(): JScrollPane =
        JScrollPane(JLabel("Position: 0, 0").apply { preferredSize = Dimension(VIEW_SIZE, VIEW_SIZE) }).apply {
            preferredSize = Dimension(PANE_WIDTH, PANE_HEIGHT)
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS
        }

    override fun updateRawSwing(
        component: JScrollPane,
        parameters: VerticalScrollbarParameters,
    ) {
        component.verticalScrollBar.apply {
            isEnabled = parameters.enabled
            foreground = parameters.color
        }
    }
}
