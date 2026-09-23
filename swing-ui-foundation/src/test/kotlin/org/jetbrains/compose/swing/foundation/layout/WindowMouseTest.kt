package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.modifier.listener.mouseListener
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowMouseTest {
    @Test
    fun aPressAReleaseAndAClickCarryAClickCountOf1() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val clickCounts = ArrayList<Int>()
            setWindowContent {
                Canvas(
                    modifier =
                        SwingModifier
                            .testTag(CANVAS_TAG)
                            .preferredSize(40, 40)
                            .mouseListener(
                                onMousePressed = { clickCounts += it.clickCount },
                                onMouseReleased = { clickCounts += it.clickCount },
                                onMouseClicked = { clickCounts += it.clickCount },
                            ),
                ) {}
            }
            val frame = onWindowWithTitle(WINDOW_TITLE).fetch<JFrame>()
            val canvas = onWindowWithTitle(WINDOW_TITLE).onNodeWithTag(CANVAS_TAG).fetch<JComponent>()
            val at = SwingUtilities.convertPoint(canvas, 20, 20, frame)

            sendMouse(frame, MouseEvent.MOUSE_PRESSED, at, InputEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1)
            sendMouse(frame, MouseEvent.MOUSE_RELEASED, at, 0, MouseEvent.BUTTON1)
            sendMouse(frame, MouseEvent.MOUSE_CLICKED, at, 0, MouseEvent.BUTTON1)

            assertEquals(listOf(1, 1, 1), clickCounts, "a press, a release and a click carry a click count of 1")
        }
}

private const val CANVAS_TAG = "canvas"
