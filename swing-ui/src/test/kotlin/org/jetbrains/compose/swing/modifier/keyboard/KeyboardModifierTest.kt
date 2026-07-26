package org.jetbrains.compose.swing.modifier.keyboard

import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.interaction.onPointerEvent
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JTextField
import javax.swing.KeyStroke
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Behavioral tests for the keyboard and pointer interaction modifiers. They assert what an observer
 * of the live Swing component sees - key events forwarded and consumed, key-stroke bindings present
 * in the real InputMap/ActionMap and firing their action, pointer phases delivered - never the
 * internal diff/record machinery.
 */
class KeyboardModifierTest {
    private fun keyPressed(
        component: Component,
        keyCode: Int,
    ): KeyEvent = KeyEvent(component, KeyEvent.KEY_PRESSED, 0L, 0, keyCode, KeyEvent.CHAR_UNDEFINED)

    /**
     * Delivers [event] to every [KeyListener] installed on this component. Headless tests cannot route
     * a real key event through the KeyboardFocusManager (no focused, showing peer), so we invoke the
     * installed listeners directly - this still asserts the observable behavior of the listener the
     * modifier attached (it forwards the event and consumes it when the callback returns true).
     */
    private fun Component.deliverKeyPressed(event: KeyEvent) {
        for (listener in keyListeners) listener.keyPressed(event)
    }

    private fun mousePressed(component: Component): MouseEvent =
        MouseEvent(component, MouseEvent.MOUSE_PRESSED, 0L, 0, 1, 1, 1, false)

    private fun mouseReleased(component: Component): MouseEvent =
        MouseEvent(component, MouseEvent.MOUSE_RELEASED, 0L, 0, 1, 1, 1, false)

    private fun mouseClicked(component: Component): MouseEvent =
        MouseEvent(component, MouseEvent.MOUSE_CLICKED, 0L, 0, 1, 1, 1, false)

    /** Fires the action bound to [keyStroke] in [condition] through the real InputMap/ActionMap. */
    private fun JComponent.fireBinding(
        keyStroke: KeyStroke,
        condition: Int = JComponent.WHEN_FOCUSED,
    ) {
        val inputMap = getInputMap(condition)
        val actionKey = inputMap.get(keyStroke) ?: error("No binding for $keyStroke")
        val action = actionMap.get(actionKey) ?: error("No action for $actionKey")
        action.actionPerformed(null)
    }

    @Test
    fun keyEventModifierForwardsEventsToTheCallback() = runComposeSwingTest {
        var seen: Int? = null
        setContent {
            TextField(
                value = "",
                onValueChange = {},
                modifier =
                    SwingModifier.onKeyEvent { e ->
                        seen = e.keyCode
                        false
                    },
            )
        }
        val field = onNodeOfType<JTextField>().fetch()

        field.deliverKeyPressed(keyPressed(field, KeyEvent.VK_A))
        assertEquals(KeyEvent.VK_A, seen, "the callback should receive the delivered key")
    }

    @Test
    fun returningTrueFromKeyEventConsumesTheEvent() = runComposeSwingTest {
        setContent {
            TextField(
                value = "",
                onValueChange = {},
                modifier = SwingModifier.onKeyEvent { true },
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        val event = keyPressed(field, KeyEvent.VK_A)

        field.deliverKeyPressed(event)
        assertTrue(event.isConsumed, "returning true must consume the event")
    }

    @Test
    fun returningFalseFromKeyEventLeavesItUnconsumed() = runComposeSwingTest {
        setContent {
            TextField(
                value = "",
                onValueChange = {},
                modifier = SwingModifier.onKeyEvent { false },
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        val event = keyPressed(field, KeyEvent.VK_A)

        field.deliverKeyPressed(event)
        assertFalse(event.isConsumed, "returning false must leave the event unconsumed")
    }

    @Test
    fun keyEventModifierSeesTheLatestCallbackWithoutReinstalling() = runComposeSwingTest {
        var target by mutableStateOf("first")
        var captured = ""
        setContent {
            TextField(
                value = "",
                onValueChange = {},
                modifier =
                    SwingModifier.onKeyEvent {
                        captured = target
                        false
                    },
            )
        }
        val field = onNodeOfType<JTextField>().fetch()

        field.deliverKeyPressed(keyPressed(field, KeyEvent.VK_A))
        assertEquals("first", captured, "the listener should read the first callback")

        target = "second"
        awaitIdle()
        field.deliverKeyPressed(keyPressed(field, KeyEvent.VK_A))
        assertEquals("second", captured, "the installed key listener must read the latest callback")
    }

    @Test
    fun keyStrokeBindingTriggersItsAction() = runComposeSwingTest {
        var fired = 0
        val stroke = KeyStroke.getKeyStroke("ctrl S")
        setContent {
            TextField(
                value = "",
                onValueChange = {},
                modifier = SwingModifier.onKeyStroke(stroke) { fired++ },
            )
        }
        onNodeOfType<JTextField>().fetch().fireBinding(stroke)
        assertEquals(1, fired, "the bound key stroke should fire its action once")
    }

    @Test
    fun stringKeyStrokeOverloadBindsTheParsedStroke() = runComposeSwingTest {
        var fired = 0
        setContent {
            TextField(
                value = "",
                onValueChange = {},
                modifier = SwingModifier.onKeyStroke("ctrl S") { fired++ },
            )
        }
        onNodeOfType<JTextField>().fetch().fireBinding(KeyStroke.getKeyStroke("ctrl S"))
        assertEquals(1, fired, "the bound key stroke should fire its action once")
    }

    @Test
    fun distinctKeyStrokesComposeIndependently() = runComposeSwingTest {
        var save = 0
        var open = 0
        setContent {
            TextField(
                value = "",
                onValueChange = {},
                modifier =
                    SwingModifier
                        .onKeyStroke("ctrl S") { save++ }
                        .onKeyStroke("ctrl O") { open++ },
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        field.fireBinding(KeyStroke.getKeyStroke("ctrl S"))
        field.fireBinding(KeyStroke.getKeyStroke("ctrl O"))
        assertEquals(1, save, "the ctrl-S binding should fire its own action once")
        assertEquals(1, open, "the ctrl-O binding should fire its own action once")
    }

    @Test
    fun bindingTheSameKeyStrokeAndConditionTwiceThrows() = runComposeSwingTest {
        val failure =
            assertFailsWith<IllegalStateException> {
                setContent {
                    TextField(
                        value = "",
                        onValueChange = {},
                        modifier =
                            SwingModifier
                                .onKeyStroke("ctrl S") {}
                                .onKeyStroke("ctrl S") {},
                    )
                }
            }
        assertTrue(
            failure.message?.contains("already bound") == true,
            "the collision message must explain the double-bind, was: ${failure.message}",
        )
    }

    @Test
    fun sameKeyStrokeInDifferentConditionsDoesNotCollide() = runComposeSwingTest {
        var focused = 0
        var window = 0
        setContent {
            TextField(
                value = "",
                onValueChange = {},
                modifier =
                    SwingModifier
                        .onKeyStroke("ctrl S", JComponent.WHEN_FOCUSED) { focused++ }
                        .onKeyStroke("ctrl S", JComponent.WHEN_IN_FOCUSED_WINDOW) { window++ },
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        field.fireBinding(KeyStroke.getKeyStroke("ctrl S"), JComponent.WHEN_FOCUSED)
        field.fireBinding(KeyStroke.getKeyStroke("ctrl S"), JComponent.WHEN_IN_FOCUSED_WINDOW)
        assertEquals(1, focused, "the WHEN_FOCUSED binding should fire its own action once")
        assertEquals(1, window, "the WHEN_IN_FOCUSED_WINDOW binding should fire its own action once")
    }

    @Test
    fun keyStrokeBindingIsRemovedWhenItsElementLeavesTheChain() = runComposeSwingTest {
        var bound by mutableStateOf(true)
        val stroke = KeyStroke.getKeyStroke("ctrl S")
        setContent {
            TextField(
                value = "",
                onValueChange = {},
                modifier = if (bound) SwingModifier.onKeyStroke(stroke) {} else SwingModifier,
            )
        }
        val field = onNodeOfType<JTextField>()
        assertTrue(
            field.fetch<JTextField>().getInputMap(JComponent.WHEN_FOCUSED).get(stroke) != null,
            "the binding must be installed while its element is present",
        )

        bound = false
        awaitIdle()
        assertTrue(
            field.fetch<JTextField>().getInputMap(JComponent.WHEN_FOCUSED).get(stroke) == null,
            "the binding must be removed when its element leaves the chain",
        )
    }

    @Test
    fun pointerEventModifierDeliversPressReleaseAndClick() = runComposeSwingTest {
        var pressed = 0
        var released = 0
        var clicked = 0
        setContent {
            Button(
                "X",
                modifier =
                    SwingModifier
                        .onPointerEvent(
                            onPress = { pressed++ },
                            onRelease = { released++ },
                            onClick = { clicked++ },
                        ),
            )
        }
        val button = onNodeOfType<JButton>().fetch()

        button.dispatchEvent(mousePressed(button))
        button.dispatchEvent(mouseReleased(button))
        button.dispatchEvent(mouseClicked(button))
        assertEquals(1, pressed, "the press callback should fire once")
        assertEquals(1, released, "the release callback should fire once")
        assertEquals(1, clicked, "the click callback should fire once")
    }

    @Test
    fun pointerEventModifierStopsAfterItsElementIsRemoved() = runComposeSwingTest {
        var enabled by mutableStateOf(true)
        var pressed = 0
        setContent {
            Button(
                "X",
                modifier = if (enabled) SwingModifier.onPointerEvent(onPress = { pressed++ }) else SwingModifier,
            )
        }
        val button = onNodeOfType<JButton>().fetch()
        button.dispatchEvent(mousePressed(button))
        assertEquals(1, pressed, "the press callback should fire while the modifier is present")

        enabled = false
        awaitIdle()
        button.dispatchEvent(mousePressed(button))
        assertEquals(1, pressed, "the pointer listener must be removed when its element leaves the chain")
    }

    @Test
    fun keyStrokeBindingSurvivesReuseAndIsReinstalled() = runComposeSwingTest {
        var active by mutableStateOf(true)
        var fired = 0
        val stroke = KeyStroke.getKeyStroke("ctrl S")
        setContent {
            ReusableContentHost(active = active) {
                TextField(
                    value = "",
                    onValueChange = {},
                    modifier = SwingModifier.onKeyStroke(stroke) { fired++ },
                )
            }
        }
        val field = onNodeOfType<JTextField>()
        field.fetch<JTextField>().fireBinding(stroke)
        assertEquals(1, fired, "the binding should fire once before reuse")

        // Deactivate then reactivate: resetModifierState drains the additive binding (removing the
        // InputMap/ActionMap entries); re-activation reinstalls them on the reused node.
        active = false
        awaitIdle()
        active = true
        awaitIdle()

        field.fetch<JTextField>().fireBinding(stroke)
        assertEquals(2, fired, "the binding must be re-installed after reuse")
    }
}
