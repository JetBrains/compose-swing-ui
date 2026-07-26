package org.jetbrains.compose.swing.modifier.listener

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.beans.PropertyChangeListener
import javax.swing.AbstractButton
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Behavioral tests for the typed instance listener builders. They assert what an observer of the live
 * Swing component sees: that an existing listener **object** attached via a builder fires on the real
 * AWT event, that the SAME instance is registered and later removed (proved via the component's
 * `getXxxListeners()`), that two of a kind both install, that supplying a different instance swaps the
 * attachment, and that a builder whose target type the node is not is rejected at apply with the
 * centralized wrong-target error — never the internal diff/record machinery.
 */
class RawListenerModifierTest {
    private fun mousePressed(component: Component): MouseEvent =
        MouseEvent(component, MouseEvent.MOUSE_PRESSED, 0L, 0, 1, 1, 1, false)

    private fun mousePressListener(onPress: () -> Unit): MouseListener = object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent): Unit = onPress()
    }

    @Test
    fun existingListenerInstanceFiresOnTheRealEvent() = runComposeSwingTest {
        var pressed = 0
        val listener = mousePressListener { pressed++ }
        setContent {
            Button("X", modifier = SwingModifier.name("b").mouseListener(listener))
        }
        val button = onNodeWithName("b").fetch<JComponent>()
        // The exact instance we passed is the one registered on the component.
        assertTrue(button.mouseListeners.any { it === listener }, "the exact listener instance should be registered")

        button.dispatchEvent(mousePressed(button))
        assertEquals(1, pressed, "the registered listener should fire once on a press")
    }

    @Test
    fun removingTheElementDetachesTheSameInstance() = runComposeSwingTest {
        var attached by mutableStateOf(true)
        var pressed = 0
        val listener = mousePressListener { pressed++ }
        setContent {
            Button(
                "X",
                modifier =
                    SwingModifier.name("b").let {
                        if (attached) it.mouseListener(listener) else it
                    },
            )
        }
        val button = onNodeWithName("b").fetch<JComponent>()
        assertTrue(button.mouseListeners.any { it === listener }, "the listener should be registered while present")

        attached = false
        awaitIdle()
        // AWT removes by identity: the exact instance must be gone, and it must stop firing.
        assertTrue(
            button.mouseListeners.none {
                it === listener
            },
            "the exact instance must be detached when its element leaves",
        )
        button.dispatchEvent(mousePressed(button))
        assertEquals(0, pressed, "the detached instance must not fire after its element leaves the chain")
    }

    @Test
    fun twoListenersOfTheSameTypeBothInstall() = runComposeSwingTest {
        var first = 0
        var second = 0
        val firstListener = mousePressListener { first++ }
        val secondListener = mousePressListener { second++ }
        setContent {
            Button(
                "X",
                modifier =
                    SwingModifier
                        .name("b")
                        .mouseListener(firstListener)
                        .mouseListener(secondListener),
            )
        }
        val button = onNodeWithName("b").fetch<JComponent>()
        assertTrue(button.mouseListeners.any { it === firstListener }, "the first listener should be installed")
        assertTrue(button.mouseListeners.any { it === secondListener }, "the second listener should be installed")

        button.dispatchEvent(mousePressed(button))
        // Additive: both instances install and both fire, neither replaces the other.
        assertEquals(1, first, "the first listener should fire once")
        assertEquals(1, second, "the second listener should fire once")
    }

    @Test
    fun swappingTheInstanceDetachesTheOldAndAttachesTheNew() = runComposeSwingTest {
        var useFirst by mutableStateOf(true)
        var first = 0
        var second = 0
        val firstListener = mousePressListener { first++ }
        val secondListener = mousePressListener { second++ }
        setContent {
            Button(
                "X",
                modifier =
                    SwingModifier.name("b").mouseListener(if (useFirst) firstListener else secondListener),
            )
        }
        val button = onNodeWithName("b").fetch<JComponent>()
        assertTrue(
            button.mouseListeners.any { it === firstListener },
            "the first instance should be installed initially",
        )
        button.dispatchEvent(mousePressed(button))
        assertEquals(1, first, "the first instance should fire while installed")

        // A different instance arrives on recomposition: the old one must be removed and the new
        // one attached (no duplicate, the old stops firing).
        useFirst = false
        awaitIdle()
        assertTrue(button.mouseListeners.none { it === firstListener }, "swapping should detach the old instance")
        assertTrue(button.mouseListeners.any { it === secondListener }, "swapping should attach the new instance")

        button.dispatchEvent(mousePressed(button))
        assertEquals(1, first, "the swapped-out instance must no longer fire")
        assertEquals(1, second, "the swapped-in instance must fire")
    }

    @Test
    fun boundPropertyChangeListenerInstanceFiresOnlyOnItsProperty() = runComposeSwingTest {
        var seenNew: Any? = null
        var fired = 0
        val listener =
            PropertyChangeListener { event ->
                fired++
                seenNew = event.newValue
            }
        setContent {
            Label("X", modifier = SwingModifier.name("lbl").propertyChangeListener("enabled", listener))
        }
        val label = onNodeWithName("lbl").fetch<JComponent>()

        label.toolTipText = "changed"
        assertEquals(0, fired, "a different bound property must not notify the enabled-bound listener")

        label.isEnabled = false
        assertEquals(1, fired, "the enabled-bound listener should fire on its own property")
        assertEquals(false, seenNew, "the listener should receive the new property value")
    }

    @Test
    fun unboundPropertyChangeListenerInstanceFiresOncePerChangeOnAnyBoundProperty() = runComposeSwingTest {
        val seenProperties = mutableListOf<String?>()
        var fired = 0
        val listener =
            PropertyChangeListener { event ->
                fired++
                seenProperties += event.propertyName
            }
        setContent {
            Label("X", modifier = SwingModifier.name("lbl").propertyChangeListener(listener))
        }
        val label = onNodeWithName("lbl").fetch<JComponent>()

        // The unbound overload observes every bound property: each distinct change fires it once.
        label.isEnabled = false
        assertEquals(1, fired, "the first bound-property change must notify the unbound listener once")

        label.toolTipText = "changed"
        assertEquals(2, fired, "a second, distinct bound-property change must notify it once more")

        assertTrue(
            "enabled" in seenProperties && "ToolTipText" in seenProperties,
            "the unbound listener must see both changed properties, but saw: $seenProperties",
        )
    }

    @Test
    fun actionListenerOnANonButtonTargetThrowsTheCentralizedWrongTargetError() = runComposeSwingTest {
        val error =
            assertFailsWith<IllegalStateException> {
                setContent {
                    // A Label is a JComponent but not an AbstractButton, so actionListener's
                    // AbstractButton target is wrong and the applier must reject it.
                    Label(
                        "X",
                        modifier =
                            SwingModifier.name("lbl").actionListener(ActionListener { }),
                    )
                }
                awaitIdle()
            }
        val message = error.message.orEmpty()
        assertTrue(
            AbstractButton::class.java.name in message,
            "the wrong-target error must name the required AbstractButton target, but was: $message",
        )
    }

    @Test
    fun actionListenerOnAButtonFiresOnAction() = runComposeSwingTest {
        var actions = 0
        val listener = ActionListener { actions++ }
        setContent {
            Button("X", modifier = SwingModifier.name("b").actionListener(listener))
        }
        val button = onNodeWithName("b").fetch<AbstractButton>()
        assertTrue(button.actionListeners.any { it === listener }, "the action listener instance should be registered")

        val event = ActionEvent(button, ActionEvent.ACTION_PERFORMED, "x")
        button.actionListeners.forEach { it.actionPerformed(event) }
        assertEquals(1, actions, "the registered action listener should fire once")
    }
}
