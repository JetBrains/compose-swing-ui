package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.graphics.layoutBounds
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `getBaseline(width, height)` takes a component's Swing size and measures from the top of its bounds, as
 * `FlowLayout` calls it, so a Foundation layout asks a decorated component with its paint outsets around the layout
 * size.
 */
class PaintOutsetsBaselineTest {
    @Test
    fun aShadowedBaselineLinesUpTheSameInARowAndInAFlowLayout() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag("row")) {
                    Label("row", modifier = SwingModifier.testTag("plain").alignByBaseline())
                    DecoratedBaselineChild(
                        10,
                        SwingModifier.testTag("card").alignByBaseline().shadow(6, Color.BLACK),
                    )
                }
                SwingNode(
                    factory = { JPanel(FlowLayout(FlowLayout.LEADING, 0, 0).apply { alignOnBaseline = true }) },
                ) {
                    Label("flow", modifier = SwingModifier.testTag("next"))
                    DecoratedBaselineChild(
                        10,
                        SwingModifier.testTag("content").shadow(6, Color.BLACK),
                    )
                }
            }
            val inRow =
                onNodeWithTag("card").fetch<JComponent>().layoutY - onNodeWithTag("plain").fetch<JComponent>().y
            val inFlow =
                onNodeWithTag("content").fetch<JComponent>().layoutY - onNodeWithTag("next").fetch<JComponent>().y

            assertEquals(inFlow, inRow, "the layout top sits the same distance from the label's top in both")
            val content = onNodeWithTag("content").fetch<JComponent>()
            assertEquals(content.bounds, content.layoutBounds, "in a FlowLayout the shadow takes no paint outsets")
        }

    private companion object {
        val Component.layoutY: Int get() = layoutBounds.y
    }
}
