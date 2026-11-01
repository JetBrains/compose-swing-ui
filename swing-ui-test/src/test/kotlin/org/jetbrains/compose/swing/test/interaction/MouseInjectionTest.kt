package org.jetbrains.compose.swing.test.interaction

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.mouseListener
import org.jetbrains.compose.swing.modifier.listener.mouseMotionListener
import org.jetbrains.compose.swing.modifier.listener.mouseWheelListener
import org.jetbrains.compose.swing.platform.hostOs
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.EventQueue
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class MouseInjectionTest {
    @Test
    fun eventsOccupySeparateQueueTurns() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Mouse injection needs a display")
        val events = mutableListOf<String>()
        setContent {
            MouseTarget(
                SwingModifier.mouseListener {
                    when (it.id) {
                        MouseEvent.MOUSE_PRESSED -> {
                            events += "pressed"
                            EventQueue.invokeLater { events += "queued" }
                        }

                        MouseEvent.MOUSE_RELEASED -> {
                            events += "released"
                        }
                    }
                },
            )
        }
        val node = onWindowWithTitle("mouse").onNodeWithText("target")
        node.performMouseInput { click() }
        assertEquals(listOf("pressed", "queued", "released"), events)
    }

    @ParameterizedTest
    @MethodSource("secondCallQueries")
    fun gestureSpansCalls(secondCallQuery: (SwingNodeInteraction<*>) -> SwingNodeInteraction<*>) = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Mouse injection needs a display")
        val drags = mutableListOf<MouseEvent>()
        val events = mutableListOf<Int>()
        setContent {
            MouseTarget(
                SwingModifier
                    .mouseMotionListener {
                        if (it.id == MouseEvent.MOUSE_DRAGGED) drags += it
                    }.mouseListener(
                        onMousePressed = { events += it.id },
                        onMouseReleased = { events += it.id },
                        onMouseClicked = { events += it.id },
                    ),
            )
        }
        val node = onWindowWithTitle("mouse").onNodeWithText("target")
        val child = secondCallQuery(node)
        assertSame(node.fetch(), child.fetch())
        node.performMouseInput {
            moveTo(center)
            press()
        }
        child.performMouseInput {
            moveTo(Point(1, 1))
            release()
        }
        assertEquals(listOf(MouseEvent.BUTTON1_DOWN_MASK), drags.map { it.modifiersEx })
        assertEquals(listOf(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED), events)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("wheelRotations")
    fun scrollDeliversWheelRotationAtPointerPosition(rotation: Int) = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Mouse injection needs a display")
        val events = mutableListOf<MouseWheelEvent>()
        setContent {
            MouseTarget(SwingModifier.mouseWheelListener { events += it })
        }
        val node = onWindowWithTitle("mouse").onNodeWithText("target")
        node.performMouseInput {
            moveTo(Point(1, 1))
            scroll(rotation)
        }
        assertEquals(listOf(rotation), events.map { it.wheelRotation })
        assertEquals(listOf(Point(1, 1)), events.map { it.point })
        assertEquals(listOf(MouseWheelEvent.WHEEL_UNIT_SCROLL), events.map { it.scrollType })
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("buttons")
    fun clickDeliversTheSelectedButtonAndItsDownMask(
        button: Int,
        releasedModifiers: Int,
    ) = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Mouse injection needs a display")
        val events = mutableListOf<MouseEvent>()
        setContent {
            MouseTarget(
                SwingModifier.mouseListener(
                    onMousePressed = { events += it },
                    onMouseReleased = { events += it },
                    onMouseClicked = { events += it },
                ),
            )
        }
        val position = Point(1, 1)
        onWindowWithTitle("mouse").onNodeWithText("target").performMouseInput {
            click(position, button)
        }
        assertEquals(
            listOf(
                Triple(MouseEvent.MOUSE_PRESSED, button, InputEvent.getMaskForButton(button)) to position,
                Triple(MouseEvent.MOUSE_RELEASED, button, releasedModifiers) to position,
                Triple(MouseEvent.MOUSE_CLICKED, button, releasedModifiers) to position,
            ),
            events.map { Triple(it.id, it.button, it.modifiersEx) to it.point },
        )
        assertEquals(
            listOf(1, 1, 1),
            events.map { it.clickCount },
            "a press, a release and a click carry a click count of 1",
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("clickButtons")
    fun onlySecondaryClickTriggersPopupOnPlatformEvent(button: Int) = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Mouse injection needs a display")
        val events = mutableListOf<MouseEvent>()
        setContent {
            MouseTarget(
                SwingModifier.mouseListener(
                    onMousePressed = { events += it },
                    onMouseReleased = { events += it },
                    onMouseClicked = { events += it },
                ),
            )
        }
        onWindowWithTitle("mouse").onNodeWithText("target").performMouseInput {
            if (button == MouseEvent.BUTTON3) contextClick() else click()
        }
        val secondary = button == MouseEvent.BUTTON3
        val windows = hostOs.isWindows
        assertEquals(
            listOf(
                Triple(MouseEvent.MOUSE_PRESSED, button, secondary && !windows),
                Triple(MouseEvent.MOUSE_RELEASED, button, secondary && windows),
                Triple(MouseEvent.MOUSE_CLICKED, button, false),
            ),
            events.map { Triple(it.id, it.button, it.isPopupTrigger) },
        )
    }

    @Test
    fun pressBeforeMoveToFails() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Mouse injection needs a display")
        setContent { MouseTarget(SwingModifier) }
        val node = onWindowWithTitle("mouse").onNodeWithText("target")
        assertFailsWith<IllegalStateException> {
            node.performMouseInput { press() }
        }
    }

    @Test
    fun pressingHeldButtonFails() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Mouse injection needs a display")
        setContent { MouseTarget(SwingModifier) }
        val node = onWindowWithTitle("mouse").onNodeWithText("target")
        assertFailsWith<IllegalStateException> {
            node.performMouseInput {
                moveTo(center)
                press()
                press()
            }
        }
    }

    @Test
    fun inputToANodeOutsideAShowingWindowFails() = runComposeSwingTest {
        setContent { Label("target") }
        assertFailsWith<AssertionError> {
            onNodeWithText("target").performMouseInput { click() }
        }
    }

    companion object {
        @JvmStatic
        fun wheelRotations(): List<Named<Int>> = listOf(
            Named.of("away from the user", -1),
            Named.of("toward the user", 1),
        )

        @JvmStatic
        fun buttons(): List<Arguments> = listOf(
            Arguments.of(Named.of("primary button", MouseEvent.BUTTON1), 0),
            Arguments.of(Named.of("middle button", MouseEvent.BUTTON2), InputEvent.ALT_DOWN_MASK),
            Arguments.of(Named.of("right button", MouseEvent.BUTTON3), InputEvent.META_DOWN_MASK),
        )

        @JvmStatic
        fun clickButtons(): List<Named<Int>> = listOf(
            Named.of("primary click", MouseEvent.BUTTON1),
            Named.of("context click", MouseEvent.BUTTON3),
        )

        @JvmStatic
        fun secondCallQueries(): List<Named<(SwingNodeInteraction<*>) -> SwingNodeInteraction<*>>> = listOf(
            Named.of("same query", { node: SwingNodeInteraction<*> -> node }),
            Named.of("parent then first child", { node: SwingNodeInteraction<*> ->
                node.onParent().onChildren().onFirst()
            }),
        )
    }
}

@Composable
private fun MouseTarget(modifier: SwingModifier) {
    Window(onCloseRequest = {}, title = "mouse", visible = true) {
        Label("target", modifier)
    }
}
