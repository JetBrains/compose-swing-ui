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
import javax.swing.JPanel

/** The parameters [GlassPaneFixture] varies: the glass pane's name, its foreground and the text of its label. */
public data class GlassPaneParameters(
    val name: String,
    val color: Color,
    val text: String,
)

/** The internal frame glass pane, with a name, a foreground and one label, against the same pane on a raw frame. */
public object GlassPaneFixture : ComposableFixture<GlassPaneParameters, JDesktopPane>(
    initial = GlassPaneParameters(name = "Before", color = Color.BLUE, text = "Before"),
) {
    override val alternatives: List<GlassPaneParameters> =
        listOf(
            initial.copy(name = "After"),
            initial.copy(color = Color.RED),
            initial.copy(text = "After"),
        )

    @Composable
    override fun Content(parameters: GlassPaneParameters): Unit =
        Content(parameters.name, parameters.color, parameters.text)

    /**
     * Composes a desktop holding one frame whose glass pane is named [name], has [color] and shows a label of
     * [text].
     */
    @Composable
    public fun Content(
        name: String,
        color: Color,
        text: String,
    ) {
        DesktopPane {
            InternalFrame(
                title = FRAME_TITLE,
                bounds = Rectangle(0, 0, FRAME_WIDTH, FRAME_HEIGHT),
                onClose = {},
            ) {
                GlassPane(
                    modifier = SwingModifier.name(name),
                    style = { foreground(color) },
                ) {
                    Label(text = text)
                }
            }
        }
    }

    /** Builds a raw desktop holding one visible frame whose own glass pane is shown and holds one label. */
    override fun createRawSwing(): JDesktopPane =
        JDesktopPane().apply {
            add(
                JInternalFrame(FRAME_TITLE).apply {
                    bounds = Rectangle(0, 0, FRAME_WIDTH, FRAME_HEIGHT)
                    defaultCloseOperation = JInternalFrame.DO_NOTHING_ON_CLOSE
                    (glassPane as JPanel).apply {
                        add(JLabel())
                        isVisible = true
                    }
                    isVisible = true
                },
            )
        }

    /** Applies the name, color and label text of [parameters] to the glass pane of the frame of [component]. */
    override fun updateRawSwing(
        component: JDesktopPane,
        parameters: GlassPaneParameters,
    ) {
        val pane = component.allFrames.first().glassPane as JPanel
        pane.name = parameters.name
        pane.foreground = parameters.color
        val label = pane.components.single() as JLabel
        label.text = parameters.text
        label.foreground = parameters.color
    }

    private const val FRAME_TITLE = "Frame"
    private const val FRAME_WIDTH = 200
    private const val FRAME_HEIGHT = 100
}
