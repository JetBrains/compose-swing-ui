// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import org.jetbrains.compose.swing.annotations.HorizontalScrollbarPolicy
import org.jetbrains.compose.swing.annotations.VerticalScrollbarPolicy
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.layout.rememberScrollState
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.preferredSize
import java.awt.Dimension
import java.awt.Point
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JScrollPane
import javax.swing.border.Border

/**
 * The parameters [ScrollPaneFixture] varies: the scroll position, the scroll bar policies, the viewport border,
 * whether wheel scrolling is enabled and the text of the viewport's child.
 */
public data class ScrollPaneParameters(
    val scrollX: Int,
    val scrollY: Int,
    @param:VerticalScrollbarPolicy val verticalScrollbar: Int,
    @param:HorizontalScrollbarPolicy val horizontalScrollbar: Int,
    val viewportBorder: Border?,
    val wheelScrollingEnabled: Boolean,
    val content: String,
)

/**
 * A [ScrollPane] holding one large [Label] against a raw [JScrollPane] holding one large label, with the same
 * scroll position, scroll bar policies, viewport border, wheel scrolling and label text.
 */
public object ScrollPaneFixture : ComposableFixture<ScrollPaneParameters, JScrollPane>(
    initial =
        ScrollPaneParameters(
            scrollX = 0,
            scrollY = 0,
            verticalScrollbar = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            horizontalScrollbar = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED,
            viewportBorder = null,
            wheelScrollingEnabled = true,
            content = "Before",
        ),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<ScrollPaneParameters> =
        listOf(
            initial.copy(scrollX = 20),
            initial.copy(scrollY = 30),
            initial.copy(verticalScrollbar = JScrollPane.VERTICAL_SCROLLBAR_NEVER),
            initial.copy(horizontalScrollbar = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER),
            initial.copy(viewportBorder = BorderFactory.createEmptyBorder(1, 1, 1, 1)),
            initial.copy(wheelScrollingEnabled = false),
            initial.copy(content = "After"),
        )

    @Composable
    override fun Content(parameters: ScrollPaneParameters): Unit =
        Content(
            parameters.scrollX,
            parameters.scrollY,
            parameters.verticalScrollbar,
            parameters.horizontalScrollbar,
            parameters.viewportBorder,
            parameters.wheelScrollingEnabled,
            parameters.content,
        )

    /**
     * Composes a [ScrollPane] scrolled to [scrollX], [scrollY], with the given scroll bar policies, viewport
     * border and wheel scrolling, holding one large [Label] showing [content].
     */
    @Composable
    public fun Content(
        scrollX: Int,
        scrollY: Int,
        @VerticalScrollbarPolicy verticalScrollbar: Int,
        @HorizontalScrollbarPolicy horizontalScrollbar: Int,
        viewportBorder: Border?,
        wheelScrollingEnabled: Boolean,
        content: String,
    ) {
        val state = rememberScrollState()
        SideEffect {
            state.x = scrollX
            state.y = scrollY
        }
        ScrollPane(
            style = SwingStyle { preferredSize(PANE_WIDTH, PANE_HEIGHT) },
            state = state,
            verticalScrollbar = verticalScrollbar,
            horizontalScrollbar = horizontalScrollbar,
            viewportBorder = viewportBorder,
            wheelScrollingEnabled = wheelScrollingEnabled,
        ) {
            Viewport {
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
     * Applies the scroll position, scroll bar policies, viewport border, wheel scrolling and label text of
     * [parameters] to [component].
     */
    override fun updateRawSwing(
        component: JScrollPane,
        parameters: ScrollPaneParameters,
    ) {
        component.verticalScrollBarPolicy = parameters.verticalScrollbar
        component.horizontalScrollBarPolicy = parameters.horizontalScrollbar
        component.viewportBorder = parameters.viewportBorder
        component.isWheelScrollingEnabled = parameters.wheelScrollingEnabled
        for (child in component.viewport.components) {
            if (child is JLabel) {
                child.text = parameters.content
            }
        }
        component.viewport.viewPosition = Point(parameters.scrollX, parameters.scrollY)
    }

    private const val PANE_WIDTH = 180
    private const val PANE_HEIGHT = 120
    private const val VIEW_SIZE = 2000
}
