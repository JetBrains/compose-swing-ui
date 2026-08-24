package org.jetbrains.compose.swing.modifier.interaction

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JButton
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behavioral coverage for the [onFocus] interaction modifier. Focus gain and loss are delivered to the
 * live component as real focus notifications; the test asserts the user's onGained/onLost callbacks fire
 * and that they stop once the element leaves the chain.
 */
class OnFocusModifierTest {
    @Test
    fun onFocusFiresGainedAndLost() = runComposeSwingTest {
        var gained = 0
        var lost = 0
        setContent {
            Button("X", onClick = { }, modifier = SwingModifier.onFocus(onGained = { gained++ }, onLost = { lost++ }))
        }

        onNodeOfType<JButton>().performFocusGained()
        assertEquals(1, gained, "focus gained should fire the gained callback once")
        assertEquals(0, lost, "focus gained should not fire the lost callback")

        onNodeOfType<JButton>().performFocusLost()
        assertEquals(1, gained, "the gained callback should not fire again on focus lost")
        assertEquals(1, lost, "focus lost should fire the lost callback once")
    }

    @Test
    fun onFocusStopsAfterItsElementIsRemoved() = runComposeSwingTest {
        var enabled by mutableStateOf(true)
        var gained = 0
        setContent {
            Button(
                "X",
                onClick = { },
                modifier = if (enabled) SwingModifier.onFocus(onGained = { gained++ }) else SwingModifier,
            )
        }

        onNodeOfType<JButton>().performFocusGained()
        assertEquals(1, gained, "the gained callback should fire while the modifier is present")

        enabled = false
        awaitIdle()
        // The element left the chain, so its focus listener is gone: a later notification must not
        // reach the removed callback.
        onNodeOfType<JButton>().performFocusGained()
        assertEquals(1, gained, "the focus listener must be removed when its element leaves the chain")
    }
}
