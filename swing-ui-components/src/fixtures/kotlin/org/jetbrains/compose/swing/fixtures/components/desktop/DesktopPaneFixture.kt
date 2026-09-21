// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.desktop

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.desktop.DesktopPane
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.style.foreground
import java.awt.Color
import java.awt.Rectangle
import javax.swing.JDesktopPane
import javax.swing.JInternalFrame
import javax.swing.JLabel

/** The parameters [DesktopPaneFixture] varies. */
public data class DesktopPaneParameters(
    val name: String,
    val color: Color,
    val labelText: String,
)

/** A [DesktopPane] holding one internal frame against a raw [JDesktopPane] holding the same frame. */
public object DesktopPaneFixture : ComposableFixture<DesktopPaneParameters, JDesktopPane>(
    initial = DesktopPaneParameters(name = "Before", color = Color.BLUE, labelText = "Before"),
) {
    override val alternatives: List<DesktopPaneParameters> =
        listOf(
            initial.copy(name = "After"),
            initial.copy(color = Color.RED),
            initial.copy(labelText = "After"),
        )

    @Composable
    override fun Content(parameters: DesktopPaneParameters): Unit =
        Content(parameters.name, parameters.color, parameters.labelText)

    /** Composes a [DesktopPane] named [name] with foreground [color], holding one frame that shows [labelText]. */
    @Composable
    public fun Content(
        name: String,
        color: Color,
        labelText: String,
    ) {
        DesktopPane(
            modifier = SwingModifier.name(name),
            style = { foreground(color) },
        ) {
            InternalFrame("Frame", Rectangle(0, 0, FRAME_WIDTH, FRAME_HEIGHT), onClose = {}) {
                Label(text = labelText)
            }
        }
    }

    /** Builds a raw [JDesktopPane] holding an unlabeled, visible frame of the same bounds. */
    override fun createRawSwing(): JDesktopPane =
        JDesktopPane().apply {
            add(
                JInternalFrame("Frame").apply {
                    defaultCloseOperation = JInternalFrame.DO_NOTHING_ON_CLOSE
                    bounds = Rectangle(0, 0, FRAME_WIDTH, FRAME_HEIGHT)
                    contentPane.add(JLabel())
                    isVisible = true
                },
            )
        }

    /**
     * Applies [parameters] to [component]: its name, its foreground, and the text of its frame's label. The frame and
     * the label take the color too, as the style's foreground is inherited.
     */
    override fun updateRawSwing(
        component: JDesktopPane,
        parameters: DesktopPaneParameters,
    ) {
        component.name = parameters.name
        component.foreground = parameters.color
        val frame = component.allFrames.single()
        frame.foreground = parameters.color
        val label = frame.contentPane.components.single() as JLabel
        label.text = parameters.labelText
        label.foreground = parameters.color
    }

    private const val FRAME_HEIGHT = 100
    private const val FRAME_WIDTH = 200
}
