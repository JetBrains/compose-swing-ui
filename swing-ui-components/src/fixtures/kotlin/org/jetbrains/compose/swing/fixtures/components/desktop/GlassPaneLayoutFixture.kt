// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.desktop

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.desktop.DesktopPane
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import java.awt.FlowLayout
import java.awt.Rectangle
import javax.swing.JDesktopPane
import javax.swing.JInternalFrame
import javax.swing.JLabel
import javax.swing.JPanel

/** The parameter [GlassPaneLayoutFixture] varies: the horizontal gap between the glass pane's children. */
public data class GlassPaneLayoutParameters(
    val hgap: Int,
)

/** The internal frame glass pane under a flow layout, against the same pane on a raw frame. */
public object GlassPaneLayoutFixture : ComposableFixture<GlassPaneLayoutParameters, JDesktopPane>(
    initial = GlassPaneLayoutParameters(hgap = 5),
) {
    override val alternatives: List<GlassPaneLayoutParameters> =
        listOf(
            initial.copy(hgap = 10),
        )

    @Composable
    override fun Content(parameters: GlassPaneLayoutParameters): Unit = Content(parameters.hgap)

    /** Composes a desktop holding one frame whose glass pane lays a label out in a flow with a gap of [hgap]. */
    @Composable
    public fun Content(hgap: Int) {
        DesktopPane {
            InternalFrame(
                title = FRAME_TITLE,
                bounds = Rectangle(0, 0, FRAME_WIDTH, FRAME_HEIGHT),
                onClose = {},
            ) {
                GlassPane(layout = PanelLayout.Flow(hgap = hgap)) {
                    Label(text = LABEL_TEXT)
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
                        add(JLabel(LABEL_TEXT))
                        isVisible = true
                    }
                    isVisible = true
                },
            )
        }

    /** Sets the gap of [parameters] on the flow layout the glass pane of the frame of [component] already holds. */
    override fun updateRawSwing(
        component: JDesktopPane,
        parameters: GlassPaneLayoutParameters,
    ) {
        val pane = component.allFrames.first().glassPane as JPanel
        val flow = pane.layout as FlowLayout
        if (flow.hgap != parameters.hgap) {
            flow.hgap = parameters.hgap
            pane.revalidate()
        }
    }

    private const val FRAME_TITLE = "Frame"
    private const val FRAME_WIDTH = 200
    private const val FRAME_HEIGHT = 100
    private const val LABEL_TEXT = "Before"
}
