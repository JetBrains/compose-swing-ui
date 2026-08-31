package org.jetbrains.compose.swing.modifier.interaction

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JComponent
import javax.swing.JTextField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Behavioral coverage for the `SwingModifier.focusAccelerator` key. The declared key is asserted both
 * as the field reports it and as the binding it becomes: an accelerator is a key stroke registered for
 * the whole focused window, so a field that reports a key but registers nothing can never be reached
 * by it.
 */
class FocusAcceleratorModifierTest {
    @Test
    fun aDeclaredAcceleratorIsMatchedInUpperCaseAndIsPutBackOnRemoval() = runComposeSwingTest {
        var declared by mutableStateOf(false)
        val field = fieldAcceleratedWhile { declared }
        assertEquals(Char.MIN_VALUE, field.focusAccelerator, "a field starts with no accelerator")

        declared = true
        awaitIdle()
        assertEquals(ACCELERATOR.uppercaseChar(), field.focusAccelerator, "Swing matches the key in upper case")

        declared = false
        awaitIdle()
        assertEquals(Char.MIN_VALUE, field.focusAccelerator, "dropping it should put back the key the field carried")
    }

    @Test
    fun theAcceleratorBecomesAWindowWideBindingThatAsksForTheFocus() = runComposeSwingTest {
        var declared by mutableStateOf(false)
        val field = fieldAcceleratedWhile { declared }

        declared = true
        awaitIdle()
        assertTrue(field.requestsFocusFromItsWindow(), "the declared accelerator should register a focus request")

        declared = false
        awaitIdle()
        assertFalse(field.requestsFocusFromItsWindow(), "dropping it should leave no key asking for the focus")
    }

    /** Composes a field carrying the accelerator for as long as [declared] answers `true`. */
    private suspend fun ComposeSwingTest.fieldAcceleratedWhile(declared: () -> Boolean): JTextField {
        setContent {
            SwingNode(
                factory = { JTextField(TEXT) },
                modifier = if (declared()) SwingModifier.focusAccelerator(ACCELERATOR) else SwingModifier,
            )
        }
        awaitIdle()
        return onNodeOfType<JTextField>().fetch()
    }

    /** Whether a key stroke anywhere in the focused window asks this field for the focus. */
    private fun JTextField.requestsFocusFromItsWindow(): Boolean {
        val bindings = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        val strokes = bindings.allKeys() ?: return false
        return strokes.any { bindings[it] == FOCUS_REQUEST_ACTION }
    }
}

private const val TEXT = "hello world"
private const val ACCELERATOR = 's'

/** The action a focus accelerator's key stroke is bound to. */
private const val FOCUS_REQUEST_ACTION = "requestFocus"
