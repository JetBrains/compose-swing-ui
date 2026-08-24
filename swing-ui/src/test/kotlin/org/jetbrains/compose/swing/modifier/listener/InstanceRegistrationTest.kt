package org.jetbrains.compose.swing.modifier.listener

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.event.MouseAdapter
import javax.swing.JButton
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A registration by identity is the listener together with the pairing that installed it. One
 * instance can sit on several registrations - a `MouseAdapter` is a mouse, motion and wheel listener at
 * once - so the instance alone does not say where it is registered, and reconciling on it alone
 * leaves a declared registration uninstalled.
 */
class InstanceRegistrationTest {
    @Test
    fun theSameInstanceDeclaredOnAnotherRegistrationIsAddedThere() = runComposeSwingTest {
        var motion by mutableStateOf(false)
        setContent {
            FlowPanel {
                val handler = remember { object : MouseAdapter() {} }
                Button(
                    text = if (motion) "motion" else "plain",
                    onClick = { },
                    modifier =
                        if (motion) {
                            SwingModifier.mouseMotionListener(handler)
                        } else {
                            SwingModifier.mouseListener(handler)
                        },
                )
            }
        }
        val button = onNodeOfType<JButton>().fetch<JButton>()
        assertEquals(1, button.mouseListeners.count { it is MouseAdapter }, "declared as a mouse listener")

        motion = true
        awaitIdle()

        assertEquals(
            1,
            button.mouseMotionListeners.count { it is MouseAdapter },
            "the same instance declared on the motion registration is added there",
        )
        assertEquals(
            0,
            button.mouseListeners.count { it is MouseAdapter },
            "and left the registration it was declared on before",
        )
    }

    @Test
    fun theSameInstanceOnTheSameRegistrationIsNotReRegistered() = runComposeSwingTest {
        var attachments = 0
        var removals = 0
        var label by mutableStateOf("first")
        setContent {
            FlowPanel {
                val handler = remember { object : MouseAdapter() {} }
                // Remembered, so every pass hands the seam the same registration and only the instance
                // could differ.
                val counted =
                    remember {
                        ListenerRegistration<JButton, MouseAdapter>(
                            { component, listener ->
                                attachments++
                                component.addMouseListener(listener)
                            },
                            { component, listener ->
                                removals++
                                component.removeMouseListener(listener)
                            },
                        )
                    }
                Button(text = label, onClick = { }, modifier = SwingModifier.listener(handler, counted))
            }
        }
        onNodeOfType<JButton>().fetch<JButton>()
        assertEquals(1, attachments, "installed once")

        label = "second"
        awaitIdle()

        assertEquals(1, attachments, "a pass that changes nothing about the registration installs nothing")
        assertEquals(0, removals, "and removes nothing")
    }
}
