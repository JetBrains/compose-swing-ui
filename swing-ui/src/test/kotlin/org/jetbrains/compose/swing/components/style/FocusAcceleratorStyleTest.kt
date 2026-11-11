// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.style

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.style.setterCaseTests
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import javax.swing.JComponent
import javax.swing.JTextField
import kotlin.test.Test

class FocusAcceleratorStyleTest {
    @TestFactory
    fun settersWriteUpdateAndRestore(): List<DynamicTest> = setterCaseTests(
        listOf(
            textCase("focusAccelerator", 'A', 'B', { it.focusAccelerator }) { focusAccelerator(it) },
        ),
    )

    @Test
    fun acceleratorRegistersAndRestoresTheWindowBinding() = runComposeSwingTest {
        var declared by mutableStateOf(false)
        setContent {
            TextField(value = "hello world", onValueChange = {}, style = { if (declared) focusAccelerator('s') })
        }
        onNodeOfType<JTextField>().assertProperty(Char.MIN_VALUE) { focusAccelerator }

        declared = true
        awaitIdle()
        onNodeOfType<JTextField>().apply {
            assertProperty('S') { focusAccelerator }
            assertProperty(true) { requestsFocusFromItsWindow() }
        }

        declared = false
        awaitIdle()
        onNodeOfType<JTextField>().apply {
            assertProperty(Char.MIN_VALUE) { focusAccelerator }
            assertProperty(false) { requestsFocusFromItsWindow() }
        }
    }

    private fun JTextField.requestsFocusFromItsWindow(): Boolean {
        val bindings = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        return bindings.allKeys()?.any { bindings[it] == "requestFocus" } == true
    }
}
