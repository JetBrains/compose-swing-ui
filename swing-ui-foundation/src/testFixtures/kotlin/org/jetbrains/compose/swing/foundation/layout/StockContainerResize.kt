package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.test.assertEquals

private const val WIDTH = 320
private const val NARROWING_STEP = 60

/** A container under the modifier given, holding the content handed to it. */
public typealias Holder = @Composable (SwingModifier, @Composable ConstrainedScope.() -> Unit) -> Unit

/**
 * Shows a stock container holding wrapping text in each of [holders], under a stock parent that narrows twice with no
 * paint between, then revalidates the holder, and asserts the text is resized once. A measure that runs ahead of the
 * text's own reflow, as an animation frame does, leaves the text at the size the narrowing laid it out at.
 */
public fun assertStockContainerLaysTextOutOnceAtANarrowerWidth(holders: Map<String, Holder>) {
    val resizes = mutableMapOf<String, Int>()
    for ((name, holder) in holders) {
        runComposeSwingTest {
            var width by mutableIntStateOf(WIDTH)
            val area = ResizeCountingTextArea()
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(width, SLOT_HEIGHT)) {
                    holder(SwingModifier.north().testTag("holder")) {
                        SwingNode(
                            factory = { JPanel(BorderLayout()).apply { add(area) } },
                            modifier = SwingModifier.fillMaxWidth(),
                        )
                    }
                }
            }
            awaitIdle()
            captureToImage()
            awaitIdle()
            // Not painted at this width, the area keeps the line breaks of the width before.
            width = WIDTH - NARROWING_STEP
            awaitIdle()
            width = WIDTH - 2 * NARROWING_STEP
            area.resizes = 0
            awaitIdle()

            // A measure ahead of the area's own reflow, as an animation frame runs one.
            onNodeWithTag("holder").fetch<JComponent>().revalidate()
            awaitIdle()

            resizes[name] = area.resizes
        }
    }
    assertEquals(holders.keys.associateWith { 1 }, resizes, "the times the area is resized")
}
