package org.jetbrains.compose.swing.modifier.keyboard

import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertDeclaredChainCarriedOnce
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
     * Delivers [event] to every [java.awt.event.KeyListener] installed on this component. Headless
     * tests cannot route a real key event through the KeyboardFocusManager (no focused, showing peer),
     * so this invokes the installed listeners directly - it still asserts the observable behavior the
     * modifier attached: it forwards the event and consumes it when the callback returns true.
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
                // The check runs a turn after the pass that bound these, once it has settled.
                awaitIdle()
            }
        assertTrue(
            failure.message?.contains("already bound") == true,
            "the collision message must explain the double-bind, was: ${failure.message}",
        )
    }

    @Test
    fun twoBindingsMayExchangeTheirKeyStrokesInOnePass() = runComposeSwingTest {
        var swapped by mutableStateOf(false)
        var save = 0
        var open = 0
        setContent {
            TextField(
                value = "",
                onValueChange = {},
                modifier =
                    if (!swapped) {
                        SwingModifier
                            .onKeyStroke("ctrl S") { save++ }
                            .onKeyStroke("ctrl O") { open++ }
                    } else {
                        SwingModifier
                            .onKeyStroke("ctrl O") { save++ }
                            .onKeyStroke("ctrl S") { open++ }
                    },
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        field.fireBinding(KeyStroke.getKeyStroke("ctrl S"))
        field.fireBinding(KeyStroke.getKeyStroke("ctrl O"))
        assertEquals(1, save, "the ctrl-S binding should fire the save action before the swap")
        assertEquals(1, open, "the ctrl-O binding should fire the open action before the swap")

        swapped = true
        awaitIdle()

        field.fireBinding(KeyStroke.getKeyStroke("ctrl O"))
        field.fireBinding(KeyStroke.getKeyStroke("ctrl S"))
        assertEquals(2, save, "ctrl-O must now fire the save action, the callback that declared it after the swap")
        assertEquals(2, open, "ctrl-S must now fire the open action, the callback that declared it after the swap")
    }

    @Test
    fun aSecondBindingAddedByALaterRecompositionIsStillReportedAsADoubleBind() = runComposeSwingTest {
        var addSecond by mutableStateOf(false)
        val stroke = KeyStroke.getKeyStroke("ctrl S")
        val failure =
            assertFailsWith<IllegalStateException> {
                setContent {
                    TextField(
                        value = "",
                        onValueChange = {},
                        modifier =
                            if (!addSecond) {
                                SwingModifier.onKeyStroke(stroke) {}
                            } else {
                                SwingModifier
                                    .onKeyStroke(stroke) {}
                                    .onKeyStroke(stroke) {}
                            },
                    )
                }
                // The first binding settles alone, unchallenged, before the second one arrives.
                awaitIdle()

                addSecond = true
                // The check runs a turn after this pass that added the second binding, once it has settled.
                awaitIdle()
            }
        assertTrue(
            failure.message?.contains("already bound") == true,
            "the collision message must explain the double-bind, was: ${failure.message}",
        )
    }

    @Test
    fun aStableSiblingLosingItsStrokeIsStillReportedAsADoubleBind() = runComposeSwingTest {
        val saveStroke = KeyStroke.getKeyStroke("ctrl S")
        val openStroke = KeyStroke.getKeyStroke("ctrl O")
        var remapped by mutableStateOf(false)
        val onSave: () -> Unit = {}
        val onOpen: () -> Unit = {}
        val failure =
            assertFailsWith<IllegalStateException> {
                setContent {
                    TextField(
                        value = "",
                        onValueChange = {},
                        modifier =
                            SwingModifier
                                .onKeyStroke(saveStroke, onAction = onSave)
                                .onKeyStroke(if (remapped) saveStroke else openStroke, onAction = onOpen),
                    )
                }
                awaitIdle()

                remapped = true
                // The first binding's stroke and callback are unchanged, so its element still equals the
                // one its slot holds and its own update does not run this pass - only the second,
                // remapped one does, taking over the stroke the first still believes it owns.
                awaitIdle()
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
    fun recomposingWithADifferentKeyStrokeUnbindsTheOldOneAndBindsTheNew() = runComposeSwingTest {
        var stroke by mutableStateOf(KeyStroke.getKeyStroke("ctrl S"))
        var fired = 0
        setContent {
            TextField(
                value = "",
                onValueChange = {},
                modifier = SwingModifier.onKeyStroke(stroke) { fired++ },
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        field.fireBinding(KeyStroke.getKeyStroke("ctrl S"))
        assertEquals(1, fired, "the first stroke should fire before recomposition")

        stroke = KeyStroke.getKeyStroke("ctrl O")
        awaitIdle()

        assertTrue(
            field.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("ctrl S")) == null,
            "the old stroke must no longer be bound once recomposition declares a different one",
        )
        field.fireBinding(KeyStroke.getKeyStroke("ctrl O"))
        assertEquals(2, fired, "the newly declared stroke must fire its action after recomposition")
    }

    @Test
    fun recomposingWithADifferentConditionMovesTheBindingToTheNewInputMap() = runComposeSwingTest {
        var condition by mutableStateOf(JComponent.WHEN_FOCUSED)
        var fired = 0
        val stroke = KeyStroke.getKeyStroke("ctrl S")
        setContent {
            TextField(
                value = "",
                onValueChange = {},
                modifier = SwingModifier.onKeyStroke(stroke, condition) { fired++ },
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        field.fireBinding(stroke, JComponent.WHEN_FOCUSED)
        assertEquals(1, fired, "the binding should fire under the first condition before recomposition")

        condition = JComponent.WHEN_IN_FOCUSED_WINDOW
        awaitIdle()

        assertTrue(
            field.getInputMap(JComponent.WHEN_FOCUSED).get(stroke) == null,
            "the old condition's InputMap must no longer hold the stroke once recomposition declares a new condition",
        )
        field.fireBinding(stroke, JComponent.WHEN_IN_FOCUSED_WINDOW)
        assertEquals(2, fired, "the binding must fire under the newly declared condition after recomposition")
    }

    @Test
    fun pointerEventModifierDeliversPressReleaseAndClick() = runComposeSwingTest {
        var pressed = 0
        var released = 0
        var clicked = 0
        setContent {
            Button(
                "X",
                onClick = { },
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
                onClick = { },
                modifier =
                    if (enabled) {
                        SwingModifier.onPointerEvent(onPress = {
                            pressed++
                        })
                    } else {
                        SwingModifier
                    },
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

        // Deactivation drains the additive binding via resetModifierState, removing the InputMap/
        // ActionMap entries; reactivation reinstalls them on the reused node.
        active = false
        awaitIdle()
        active = true
        awaitIdle()

        field.fetch<JTextField>().fireBinding(stroke)
        assertEquals(2, fired, "the binding must be re-installed after reuse")
    }

    @Test
    fun everyKeyboardBuilderAppendsToTheChainWithoutRepeatingIt() {
        assertDeclaredChainCarriedOnce { onKeyEvent { true } }
        assertDeclaredChainCarriedOnce { onKeyStroke(KeyStroke.getKeyStroke("A")) { } }
        assertDeclaredChainCarriedOnce { onKeyStroke("A") { } }
    }
}
