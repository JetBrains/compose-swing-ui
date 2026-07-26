package org.jetbrains.compose.swing.modifier.interaction

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import java.awt.event.MouseEvent
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behavioral coverage for the [onPointerEvent] modifier. The harness never realizes an on-screen
 * peer, so there is no real cursor: each test dispatches a synthetic [MouseEvent] through the
 * component's own registered `MouseListener`s (the same listeners Swing's dispatch invokes), then
 * asserts the registered callbacks fired with the event that was delivered. Removing the modifier
 * from the chain must detach the listener so later events no longer reach the callbacks.
 */
class PointerEventModifierTest {
    private fun pressEvent(component: Component): MouseEvent =
        MouseEvent(component, MouseEvent.MOUSE_PRESSED, 0L, 0, 3, 7, 1, false, MouseEvent.BUTTON1)

    private fun releaseEvent(component: Component): MouseEvent =
        MouseEvent(component, MouseEvent.MOUSE_RELEASED, 0L, 0, 11, 13, 1, false, MouseEvent.BUTTON1)

    private fun clickEvent(component: Component): MouseEvent =
        MouseEvent(component, MouseEvent.MOUSE_CLICKED, 0L, 0, 11, 13, 1, false, MouseEvent.BUTTON1)

    /** Delivers [event] to every `MouseListener` registered on [component], as Swing's dispatch does. */
    private fun dispatch(
        component: Component,
        event: MouseEvent,
    ) {
        for (listener in component.mouseListeners) {
            when (event.id) {
                MouseEvent.MOUSE_PRESSED -> listener.mousePressed(event)
                MouseEvent.MOUSE_RELEASED -> listener.mouseReleased(event)
                MouseEvent.MOUSE_CLICKED -> listener.mouseClicked(event)
            }
        }
    }

    @Test
    fun pressReleaseAndClickCallbacksFireWithTheDeliveredEvent() = runComposeSwingTest {
        val pressed = mutableListOf<MouseEvent>()
        val released = mutableListOf<MouseEvent>()
        val clicked = mutableListOf<MouseEvent>()
        setContent {
            Label(
                text = "target",
                modifier =
                    SwingModifier.onPointerEvent(
                        onPress = { pressed += it },
                        onRelease = { released += it },
                        onClick = { clicked += it },
                    ),
            )
        }
        val label = onNodeOfType<JLabel>().fetch()

        val press = pressEvent(label)
        val release = releaseEvent(label)
        val click = clickEvent(label)
        dispatch(label, press)
        dispatch(label, release)
        dispatch(label, click)

        // Each callback fires exactly once and receives the very event that was delivered.
        assertEquals(listOf(press), pressed, "the press callback should fire once with the delivered event")
        assertEquals(listOf(release), released, "the release callback should fire once with the delivered event")
        assertEquals(listOf(click), clicked, "the click callback should fire once with the delivered event")
        // The press callback sees the press event's coordinates, proving the MouseEvent is the
        // one carrying the gesture's data rather than a different synthesized event.
        assertEquals(3, pressed.single().x, "the press event should carry its x coordinate")
        assertEquals(7, pressed.single().y, "the press event should carry its y coordinate")
    }

    @Test
    fun callbacksStopFiringAfterTheModifierLeavesTheChain() = runComposeSwingTest {
        var enabled by mutableStateOf(true)
        val pressed = mutableListOf<MouseEvent>()
        setContent {
            Label(
                text = "target",
                modifier =
                    if (enabled) SwingModifier.onPointerEvent(onPress = { pressed += it }) else SwingModifier,
            )
        }
        val label = onNodeOfType<JLabel>().fetch()

        dispatch(label, pressEvent(label))
        assertEquals(1, pressed.size, "the press callback should fire while the modifier is present")

        // Drop the modifier: its listener must detach, so a later press reaches no callback.
        enabled = false
        awaitIdle()
        dispatch(label, pressEvent(label))
        assertEquals(1, pressed.size, "press callback must not fire after the modifier left the chain")
    }
}
