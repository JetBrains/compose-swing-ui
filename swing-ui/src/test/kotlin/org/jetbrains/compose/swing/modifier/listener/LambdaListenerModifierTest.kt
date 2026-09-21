// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.modifier.listener

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.style.enabled
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import javax.swing.JButton
import javax.swing.JCheckBox
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A lambda handed to a builder is read when the event fires rather than registered by identity, so a
 * call site that writes one inline declares a fresh lambda every pass and still keeps one registration.
 */
class LambdaListenerModifierTest {
    @Test
    fun aNamedPropertyChangeLambdaFollowsTheNameItIsDeclaredWith() = runComposeSwingTest {
        val seen = mutableListOf<String>()
        var watched by mutableStateOf("enabled")
        setContent {
            Panel {
                // Captured during composition and reported beside the event's own name: moving the
                // registration builds the listener afresh, and the captured name is what says the
                // rebuilt listener reads the lambda of the pass that moved it.
                val captured = watched
                Button(
                    text = watched,
                    onClick = { },
                    modifier =
                        SwingModifier.propertyChangeListener(watched) {
                            seen += "$captured:${it.propertyName}"
                        },
                )
            }
        }

        val button = onNodeOfType<JButton>().fetch<JButton>()
        button.isEnabled = false
        assertEquals(listOf("enabled:enabled"), seen, "the declared property is the one reported")

        watched = "background"
        awaitIdle()

        button.isEnabled = true
        button.background = Color.RED
        assertEquals(
            listOf("enabled:enabled", "background:background"),
            seen,
            "declaring another name moves the registration to it, and off the one it replaces",
        )
    }

    @Test
    fun aScopedItemListenerReceivesTheTypedComponent() = runComposeSwingTest {
        var receiver: JCheckBox? = null
        setContent {
            CheckBox(
                text = "Wrap",
                checked = false,
                onCheckedChange = {},
                modifier = SwingModifier.itemListener<JCheckBox> { receiver = this },
            )
        }

        val box = onNodeOfType<JCheckBox>().fetch<JCheckBox>()
        onNodeOfType<JCheckBox>().performClick()

        assertEquals(box, receiver, "a scoped item listener runs with the box it fired on as its receiver")
    }

    @Test
    fun aScopedPropertyChangeListenerReceivesTheTypedComponent() = runComposeSwingTest {
        var receiver: JButton? = null
        var enabled by mutableStateOf(true)
        setContent {
            Button(
                text = "Click",
                onClick = {},
                modifier =
                    SwingModifier
                        .propertyChangeListener<JButton>("enabled") { receiver = this },
                style = { enabled(enabled) },
            )
        }

        val button = onNodeOfType<JButton>().fetch<JButton>()
        enabled = false
        awaitIdle()

        assertEquals(button, receiver, "a scoped property listener runs with the button it fired on as its receiver")
    }
}
