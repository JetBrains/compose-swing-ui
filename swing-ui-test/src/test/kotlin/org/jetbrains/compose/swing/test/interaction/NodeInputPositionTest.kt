package org.jetbrains.compose.swing.test.interaction

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.mouseListener
import org.jetbrains.compose.swing.modifier.listener.mouseMotionListener
import org.jetbrains.compose.swing.modifier.listener.mouseWheelListener
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Point
import java.util.stream.Stream
import javax.swing.JButton
import kotlin.test.assertEquals

/**
 * Pins where a gesture lands: a position the caller names is the node's own, and a gesture given none
 * is aimed at the middle of the node. A UI reads the position off the event to decide what the gesture
 * means - which half of a slider was clicked, which tab, which row.
 */
class NodeInputPositionTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("gesturesGivenNoPosition")
    fun aGestureGivenNoPositionLandsInTheMiddleOfTheNode(perform: suspend SwingNodeInteraction<JButton>.() -> Unit) =
        runComposeSwingTest {
            val points = mutableListOf<Point>()
            setContent { PositionReportingButton { points += it } }

            onNodeOfType<JButton>().perform()

            assertEquals(
                setOf(Point(60, 15)),
                points.toSet(),
                "a gesture given no position must land in the middle of the node",
            )
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gesturesAimedAtAPoint")
    fun aGestureLandsOnThePointItIsAimedAt(perform: suspend SwingNodeInteraction<JButton>.() -> Unit) =
        runComposeSwingTest {
            val points = mutableListOf<Point>()
            setContent { PositionReportingButton { points += it } }

            onNodeOfType<JButton>().perform()

            assertEquals(
                setOf(AIMED_AT),
                points.toSet(),
                "a gesture must land on the point it names, in the node's own coordinates",
            )
        }

    companion object {
        private val AIMED_AT = Point(3, 4)

        @JvmStatic
        fun gesturesGivenNoPosition(): Stream<Named<suspend SwingNodeInteraction<JButton>.() -> Unit>> = Stream.of(
            Named.of<suspend SwingNodeInteraction<JButton>.() -> Unit>("press") { performMousePress() },
            Named.of<suspend SwingNodeInteraction<JButton>.() -> Unit>("release") { performMouseRelease() },
            Named.of<suspend SwingNodeInteraction<JButton>.() -> Unit>("click") { performClick() },
            Named.of<suspend SwingNodeInteraction<JButton>.() -> Unit>("context click") { performContextClick() },
            Named.of<suspend SwingNodeInteraction<JButton>.() -> Unit>("enter") { performMouseEnter() },
            Named.of<suspend SwingNodeInteraction<JButton>.() -> Unit>("exit") { performMouseExit() },
            Named.of<suspend SwingNodeInteraction<JButton>.() -> Unit>("wheel") { performMouseWheel(rotation = 1) },
        )

        @JvmStatic
        fun gesturesAimedAtAPoint(): Stream<Named<suspend SwingNodeInteraction<JButton>.() -> Unit>> = Stream.of(
            Named.of<suspend SwingNodeInteraction<JButton>.() -> Unit>("press") { performMousePress(AIMED_AT) },
            Named.of<suspend SwingNodeInteraction<JButton>.() -> Unit>("release") { performMouseRelease(AIMED_AT) },
            Named.of<suspend SwingNodeInteraction<JButton>.() -> Unit>("click") { performClick(AIMED_AT) },
            Named.of<suspend SwingNodeInteraction<JButton>.() -> Unit>("context click") {
                performContextClick(AIMED_AT)
            },
            Named.of<suspend SwingNodeInteraction<JButton>.() -> Unit>("enter") { performMouseEnter(AIMED_AT) },
            Named.of<suspend SwingNodeInteraction<JButton>.() -> Unit>("exit") { performMouseExit(AIMED_AT) },
            Named.of<suspend SwingNodeInteraction<JButton>.() -> Unit>("move") { performMouseMove(AIMED_AT) },
            Named.of<suspend SwingNodeInteraction<JButton>.() -> Unit>("wheel") {
                performMouseWheel(rotation = 1, position = AIMED_AT)
            },
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
                .mouseListener { onPoint(Point(it.x, it.y)) }
                .mouseMotionListener { onPoint(Point(it.x, it.y)) }
                .mouseWheelListener { onPoint(Point(it.x, it.y)) },
        style = { preferredSize(width = 120, height = 30) },
    )
}
