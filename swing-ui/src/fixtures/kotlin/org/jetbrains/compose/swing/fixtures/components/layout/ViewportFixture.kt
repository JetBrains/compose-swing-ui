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

/**
 * The parameters [ViewportFixture] varies: the enabled state, the foreground color, the unit and block increments,
 * and the text of the viewport's child.
 */
public data class ViewportParameters(
    val enabled: Boolean,
    val color: Color,
    val unitIncrement: Int,
    val blockIncrement: Int,
    val content: String,
)

/**
 * A Viewport inside a [ScrollPane] against a raw [JScrollPane] holding one label, with the same enabled state,
 * foreground color, scroll increments and label text.
 */
public object ViewportFixture : ComposableFixture<ViewportParameters, JScrollPane>(
    initial =
        ViewportParameters(
            enabled = true,
            color = Color.BLUE,
            unitIncrement = 1,
            blockIncrement = 10,
            content = "Before",
        ),
) {
    private const val VIEW_SIZE = 1000
    private const val PANE_WIDTH = 180
    private const val PANE_HEIGHT = 120

    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<ViewportParameters> =
        listOf(
            initial.copy(enabled = false),
            initial.copy(color = Color.RED),
            initial.copy(unitIncrement = 5),
            initial.copy(blockIncrement = 50),
            initial.copy(content = "After"),
        )

    @Composable
    override fun Content(parameters: ViewportParameters): Unit =
        Content(
            parameters.enabled,
            parameters.color,
            parameters.unitIncrement,
            parameters.blockIncrement,
            parameters.content,
        )

    /**
     * Composes a [ScrollPane] whose Viewport is enabled or not, drawn in [color], with the given scroll increments,
     * holding one [Label] showing [content].
     */
    @Composable
    public fun Content(
        enabled: Boolean,
        color: Color,
        unitIncrement: Int,
        blockIncrement: Int,
        content: String,
    ) {
        ScrollPane(style = SwingStyle { preferredSize(PANE_WIDTH, PANE_HEIGHT) }) {
            Viewport(
                style =
                    SwingStyle {
                        enabled(enabled)
                        foreground(color)
                    },
                unitIncrement = unitIncrement,
                blockIncrement = blockIncrement,
            ) {
                Label(content, style = SwingStyle { preferredSize(VIEW_SIZE, VIEW_SIZE) })
            }
        }
    }

    /** Builds a bare [JScrollPane] holding one empty [JLabel] at the fixture's sizes, before any values are applied. */
    override fun createRawSwing(): JScrollPane =
        JScrollPane(JLabel().apply { preferredSize = Dimension(VIEW_SIZE, VIEW_SIZE) }).apply {
            preferredSize = Dimension(PANE_WIDTH, PANE_HEIGHT)
        }

    /**
     * Applies the enabled state, foreground color, scroll increments and label text of [parameters] to [component],
     * setting the increments on both scroll bars.
     */
    override fun updateRawSwing(
        component: JScrollPane,
        parameters: ViewportParameters,
    ) {
        component.viewport.isEnabled = parameters.enabled
        component.viewport.foreground = parameters.color
        for (bar in arrayOf(component.verticalScrollBar, component.horizontalScrollBar)) {
            bar.unitIncrement = parameters.unitIncrement
            bar.blockIncrement = parameters.blockIncrement
        }
        for (child in component.viewport.components) {
            if (child is JLabel) {
                child.text = parameters.content
                child.foreground = parameters.color
            }
        }
    }
}
