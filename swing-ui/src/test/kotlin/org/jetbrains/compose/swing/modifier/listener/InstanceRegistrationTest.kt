// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.modifier.listener

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.interaction.assertProperty
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
            Panel {
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
        val button = onNodeOfType<JButton>()
        button.assertProperty(1, "declared as a mouse listener") { mouseListeners.count { it is MouseAdapter } }

        motion = true
        awaitIdle()

        button.assertProperty(
            1,
            "the same instance declared on the motion registration is added there",
        ) { mouseMotionListeners.count { it is MouseAdapter } }
        button.assertProperty(
            0,
            "and left the registration it was declared on before",
        ) { mouseListeners.count { it is MouseAdapter } }
    }

    @Test
    fun theSameInstanceOnTheSameRegistrationIsNotReRegistered() = runComposeSwingTest {
        var attachments = 0
        var removals = 0
        var label by mutableStateOf("first")
        setContent {
            Panel {
                val handler = remember { object : MouseAdapter() {} }
                // Remembered, so every pass hands the seam the same registration and only the instance
                // could differ.
                val counted =
                    remember {
                        ListenerRegistration<JButton, MouseAdapter>(
                            name = "mouseAdapter",
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
        onNodeOfType<JButton>().assertExists()
        assertEquals(1, attachments, "installed once")

        label = "second"
        awaitIdle()

        assertEquals(1, attachments, "a pass that changes nothing about the registration installs nothing")
        assertEquals(0, removals, "and removes nothing")
    }
}
