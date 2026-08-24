package org.jetbrains.compose.swing.modifier.listener

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import javax.swing.JButton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * A registration by callback is the registration the listener was built for. Two registrations can build their
 * listener the same way and still register it in different places - two bound properties of one
 * component do - so the way it is built does not say where it sits, and reconciling on that alone
 * leaves a declared registration uninstalled.
 */
class CallbackRegistrationTest {
    private val builtTheSameWay: (() -> (PropertyChangeEvent) -> Unit) -> PropertyChangeListener =
        { current -> PropertyChangeListener { event -> current()(event) } }

    private val onEnabled =
        CallbackRegistration(
            builtTheSameWay,
            ListenerRegistration<JButton, PropertyChangeListener>(
                { component, listener -> component.addPropertyChangeListener("enabled", listener) },
                { component, listener -> component.removePropertyChangeListener("enabled", listener) },
            ),
        )

    private val onBackground =
        CallbackRegistration(
            builtTheSameWay,
            ListenerRegistration<JButton, PropertyChangeListener>(
                { component, listener -> component.addPropertyChangeListener("background", listener) },
                { component, listener -> component.removePropertyChangeListener("background", listener) },
            ),
        )

    @Test
    fun aCallbackDeclaredOnAnotherRegistrationIsAddedThere() = runComposeSwingTest {
        var onBackgroundNow by mutableStateOf(false)
        val seen = mutableListOf<String>()
        setContent {
            FlowPanel {
                Button(
                    text = "declared",
                    modifier =
                        SwingModifier.listener(
                            { event: PropertyChangeEvent -> seen += event.propertyName },
                            if (onBackgroundNow) onBackground else onEnabled,
                        ),
                )
            }
        }
        val button = onNodeOfType<JButton>().fetch<JButton>()
        assertEquals(1, button.getPropertyChangeListeners("enabled").size, "declared on the enabled registration")
        assertEquals(0, button.getPropertyChangeListeners("background").size, "and on no other")

        onBackgroundNow = true
        awaitIdle()

        assertEquals(
            1,
            button.getPropertyChangeListeners("background").size,
            "a registration declared in its place is the one the callback is registered on",
        )
        assertEquals(
            0,
            button.getPropertyChangeListeners("enabled").size,
            "and it left the registration it was declared on before",
        )

        button.background = Color.RED
        button.isEnabled = false
        assertEquals(listOf("background"), seen, "so only the declared registration reaches the callback")
    }

    @Test
    fun aFreshCallbackOnTheSameRegistrationLeavesTheListenerWhereItIs() = runComposeSwingTest {
        var label by mutableStateOf("first")
        setContent {
            FlowPanel {
                // A lambda written here is a new object on every pass; the registration is not, and it is the
                // registration that says where the listener sits.
                Button(
                    text = label,
                    modifier = SwingModifier.listener({ _: PropertyChangeEvent -> }, onEnabled),
                )
            }
        }
        val button = onNodeOfType<JButton>().fetch<JButton>()
        val registered = button.getPropertyChangeListeners("enabled").single()

        label = "second"
        awaitIdle()

        assertSame(
            registered,
            button.getPropertyChangeListeners("enabled").single(),
            "a recomposition declaring the same registration registers nothing again",
        )
    }

    @Test
    fun oneChannelServesEveryDeclarationOfTheSameBoundProperty() = runComposeSwingTest {
        var label by mutableStateOf("first")
        setContent {
            FlowPanel {
                Button(
                    text = label,
                    modifier = SwingModifier.propertyChangeListener("enabled") { },
                )
            }
        }
        val button = onNodeOfType<JButton>().fetch<JButton>()
        val registered = button.getPropertyChangeListeners("enabled").single()

        label = "second"
        awaitIdle()

        assertSame(
            registered,
            button.getPropertyChangeListeners("enabled").single(),
            "the property name names one registration rather than a new one per pass",
        )
    }

    @Test
    fun aCallbackDeclaredOnAnotherBoundPropertyIsRegisteredThere() = runComposeSwingTest {
        var onBackgroundNow by mutableStateOf(false)
        setContent {
            FlowPanel {
                Button(
                    text = "declared",
                    modifier =
                        SwingModifier.propertyChangeListener(
                            if (onBackgroundNow) "background" else "enabled",
                        ) { },
                )
            }
        }
        val button = onNodeOfType<JButton>().fetch<JButton>()
        assertEquals(1, button.getPropertyChangeListeners("enabled").size, "declared on the enabled property")

        onBackgroundNow = true
        awaitIdle()

        assertEquals(
            1,
            button.getPropertyChangeListeners("background").size,
            "a different property name moves the registration to that property",
        )
        assertEquals(0, button.getPropertyChangeListeners("enabled").size, "leaving the one it was on")
    }
}
