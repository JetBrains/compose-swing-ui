package org.jetbrains.compose.swing.test.interaction

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.modifier.listener.mouseListener
import org.jetbrains.compose.swing.modifier.listener.mouseMotionListener
import org.jetbrains.compose.swing.modifier.listener.mouseWheelListener
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Point
import javax.swing.JButton
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins where a gesture lands: a position the caller names is the node's own, and a gesture given none
 * is aimed at the middle of the node. A UI reads the position off the event to decide what the gesture
 * means - which half of a slider was clicked, which tab, which row.
 */
class NodeInputPositionTest {
    @Test
    fun aGestureGivenNoPositionLandsInTheMiddleOfTheNode() = runComposeSwingTest {
        val points = mutableListOf<Point>()
        setContent { PositionReportingButton { points += it } }

        onNodeOfType<JButton>().performMousePress()
        onNodeOfType<JButton>().performMouseRelease()
        onNodeOfType<JButton>().performClick()
        onNodeOfType<JButton>().performContextClick()
        onNodeOfType<JButton>().performMouseEnter()
        onNodeOfType<JButton>().performMouseExit()
        onNodeOfType<JButton>().performMouseWheel(rotation = 1)

        val button = onNodeOfType<JButton>().fetch<JButton>()
        assertEquals(
            listOf(Point(button.width / 2, button.height / 2)),
            points.distinct(),
            "every gesture given no position must land in the middle of the node",
        )
    }

    @Test
    fun aGestureLandsOnThePointItIsAimedAt() = runComposeSwingTest {
        val points = mutableListOf<Point>()
        setContent { PositionReportingButton { points += it } }

        val aimedAt = Point(3, 4)
        onNodeOfType<JButton>().performMousePress(aimedAt)
        onNodeOfType<JButton>().performMouseRelease(aimedAt)
        onNodeOfType<JButton>().performClick(aimedAt)
        onNodeOfType<JButton>().performContextClick(aimedAt)
        onNodeOfType<JButton>().performMouseEnter(aimedAt)
        onNodeOfType<JButton>().performMouseExit(aimedAt)
        onNodeOfType<JButton>().performMouseMove(aimedAt)
        onNodeOfType<JButton>().performMouseWheel(rotation = 1, position = aimedAt)

        assertEquals(
            listOf(aimedAt),
            points.distinct(),
            "every gesture must land on the point it names, in the node's own coordinates",
        )
    }
}

/** A button handing [onPoint] the point every pointer gesture reaches it at. */
@Composable
private fun PositionReportingButton(onPoint: (Point) -> Unit) {
    Button(
        text = "Go",
        onClick = { },
        modifier =
            SwingModifier
                .preferredSize(width = 120, height = 30)
                .mouseListener { onPoint(Point(it.x, it.y)) }
                .mouseMotionListener { onPoint(Point(it.x, it.y)) }
                .mouseWheelListener { onPoint(Point(it.x, it.y)) },
    )
}
