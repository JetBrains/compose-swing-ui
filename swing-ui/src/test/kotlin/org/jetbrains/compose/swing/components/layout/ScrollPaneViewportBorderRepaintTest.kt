package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertAskedForLayout
import org.jetbrains.compose.swing.assertAskedToRepaint
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.withRecordedRepaints
import java.awt.Color
import javax.swing.BorderFactory
import javax.swing.JScrollPane
import javax.swing.border.Border
import kotlin.test.Test

/**
 * Behavioral test that a declared viewport border reaches the screen.
 *
 * `JScrollPane.setViewportBorder` only fires a property change - it asks for neither the layout pass
 * that takes the viewport in by the border's insets nor the paint that draws the border in the room
 * that leaves - and no look and feel listens for the property. So a border that changes in a
 * recomposition stays invisible until something unrelated relayouts and repaints the pane, unless the
 * write asks for both itself.
 */
class ScrollPaneViewportBorderRepaintTest {
    @Test
    fun everyViewportBorderTransitionRelayoutsAndRepaintsThePane() = runComposeSwingTest {
        val thin: Border = BorderFactory.createLineBorder(Color.RED, 1)
        val thick: Border = BorderFactory.createLineBorder(Color.BLUE, 8)
        var border by mutableStateOf<Border?>(thin)
        setContent {
            ScrollPane(viewportBorder = border) {
                Label(text = "Body", modifier = SwingModifier.viewport())
            }
        }
        awaitIdle()

        val pane = onNodeOfType<JScrollPane>().fetch()
        withRecordedRepaints { recorded ->
            // A new border: the insets change, so the viewport has to be laid out again before the
            // border can be drawn around it.
            border = thick
            awaitIdle()
            recorded.assertAskedForLayout(pane, "a new declared border")
            recorded.assertAskedToRepaint(pane, "a new declared border")

            // A withdrawn border goes back through the same write, from the capture the slot released.
            recorded.forget()
            border = null
            awaitIdle()
            recorded.assertAskedForLayout(pane, "a withdrawn border")
            recorded.assertAskedToRepaint(pane, "a withdrawn border")

            // And a border declared again on a pane that had none.
            recorded.forget()
            border = thin
            awaitIdle()
            recorded.assertAskedForLayout(pane, "a border declared again")
            recorded.assertAskedToRepaint(pane, "a border declared again")
        }
    }
}
