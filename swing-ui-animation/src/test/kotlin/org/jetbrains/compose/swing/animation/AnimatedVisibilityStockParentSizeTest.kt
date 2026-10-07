package org.jetbrains.compose.swing.animation

import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.foundation.layout.aspectRatio
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JComponent
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals

/** An [AnimatedVisibility] under a stock parent, which asks its sizes with no constraints. */
class AnimatedVisibilityStockParentSizeTest {
    @Test
    fun `under a stock parent a container that rests with a size change asks the height at the width it asks for`() {
        val transitions =
            mapOf(
                "expandVertically" to (expandVertically() to ExitTransition.None),
                "expandIn" to (expandIn() to ExitTransition.None),
            )
        for ((kind, transition) in transitions) {
            val (enter, exit) = transition
            runComposeSwingTest {
                setContent {
                    Panel(PanelLayout.Flow()) {
                        AnimatedVisibility(
                            visible = true,
                            modifier = SwingModifier.testTag(CONTAINER),
                            enter = enter,
                            exit = exit,
                        ) {
                            Label("Preview", modifier = SwingModifier.testTag(CONTENT).aspectRatio(RATIO))
                        }
                    }
                }
                val container = onNodeWithTag(CONTAINER).fetch<JComponent>()
                val label = onNodeWithTag(CONTENT).fetch<JComponent>()
                val atRatio = Dimension(label.preferredSize.width, (label.preferredSize.width / RATIO).roundToInt())

                assertEquals(atRatio, container.preferredSize, "$kind: the ratio's height at that width")
                assertEquals(Rectangle(Point(), atRatio), label.bounds, "$kind: the label keeps its full width")
            }
        }
    }
}

private const val RATIO = 16f / 9f
private const val CONTENT = "content"
private const val CONTAINER = "container"
