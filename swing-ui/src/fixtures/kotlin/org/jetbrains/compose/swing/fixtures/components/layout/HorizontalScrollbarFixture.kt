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

/** The parameters [HorizontalScrollbarFixture] varies. */
public data class HorizontalScrollbarParameters(
    val enabled: Boolean,
    val color: Color,
)

/** A HorizontalScrollbar in a [ScrollPane], against the raw scroll bar of a [JScrollPane] with the same effect. */
public object HorizontalScrollbarFixture : ComposableFixture<HorizontalScrollbarParameters, JScrollPane>(
    initial = HorizontalScrollbarParameters(enabled = true, color = Color.BLUE),
) {
    override val alternatives: List<HorizontalScrollbarParameters> =
        listOf(
            initial.copy(enabled = false),
            initial.copy(color = Color.RED),
        )

    @Composable
    override fun Content(parameters: HorizontalScrollbarParameters): Unit =
        Content(parameters.enabled, parameters.color)

    /** Composes a [ScrollPane] whose horizontal scroll bar is enabled or disabled and drawn in [color]. */
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
            HorizontalScrollbar(
                style =
                    SwingStyle {
                        enabled(enabled)
                        foreground(color)
                    },
            )
            Viewport {
                Label(VIEW_TEXT, style = SwingStyle { preferredSize(VIEW_SIZE, VIEW_SIZE) })
            }
        }
    }

    override fun createRawSwing(): JScrollPane =
        JScrollPane(JLabel(VIEW_TEXT).apply { preferredSize = Dimension(VIEW_SIZE, VIEW_SIZE) }).apply {
            preferredSize = Dimension(PANE_WIDTH, PANE_HEIGHT)
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS
        }

    override fun updateRawSwing(
        component: JScrollPane,
        parameters: HorizontalScrollbarParameters,
    ) {
        component.horizontalScrollBar.isEnabled = parameters.enabled
        component.horizontalScrollBar.foreground = parameters.color
    }

    private const val VIEW_TEXT = "Position: 0, 0"
    private const val VIEW_SIZE = 1000
    private const val PANE_HEIGHT = 120
    private const val PANE_WIDTH = 180
}
