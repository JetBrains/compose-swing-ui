package org.jetbrains.compose.swing.components.style

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.TestTextNodeKind
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JComponent
import javax.swing.JTextField
import kotlin.test.Test

class FocusAcceleratorStyleTest {
    @Test
    fun acceleratorRegistersAndRestoresTheWindowBinding() = runComposeSwingTest {
        var declared by mutableStateOf(false)
        setContent {
            SwingNode(
                factory = { JTextField("hello world") },
                modifier = SwingModifier.styleable(TestTextNodeKind) { if (declared) focusAccelerator('s') },
            )
        }
        val field = onNodeOfType<JTextField>()
        field.assertProperty(Char.MIN_VALUE) { focusAccelerator }

        declared = true
        awaitIdle()
        field.assertProperty('S') { focusAccelerator }
        field.assertProperty(true) { requestsFocusFromItsWindow() }

        declared = false
        awaitIdle()
        field.assertProperty(Char.MIN_VALUE) { focusAccelerator }
        field.assertProperty(false) { requestsFocusFromItsWindow() }
    }

    private fun JTextField.requestsFocusFromItsWindow(): Boolean {
        val bindings = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        return bindings.allKeys()?.any { bindings[it] == "requestFocus" } == true
    }
}
