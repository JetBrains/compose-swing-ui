package org.jetbrains.compose.swing.components.button

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.interaction.enabled
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.runComposeSwingTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behavioral coverage for the [Button] component. Each test asserts what an observer of the live
 * `JButton` sees: its rendered text, that a click fires `onClick`, that an `enabled` modifier is
 * reflected on the component, and that a state-driven label change is mirrored after recomposition.
 */
class ButtonTest {
    @Test
    fun rendersTheGivenText() = runComposeSwingTest {
        setContent {
            Button(text = "Save", onClick = {})
        }
        onNodeWithText("Save").assertExists()
    }

    @Test
    fun clickFiresOnClick() = runComposeSwingTest {
        var clicks = 0
        setContent {
            Button(text = "Click me", onClick = { clicks++ })
        }
        onNodeWithText("Click me").performClick()
        assertEquals(1, clicks, "a single click must fire onClick once")
    }

    @Test
    fun enabledModifierIsReflectedOnTheComponent() = runComposeSwingTest {
        setContent {
            Button(text = "Disabled", onClick = {}, modifier = SwingModifier.enabled(false))
        }
        onNodeWithText("Disabled").assertIsNotEnabled()
    }

    @Test
    fun stateDrivenTextUpdatesAfterRecomposition() = runComposeSwingTest {
        var count by mutableIntStateOf(0)
        setContent {
            Button(text = "Clicks: $count", onClick = { count++ })
        }
        onNodeWithText("Clicks: 0").assertExists()

        onNodeWithText("Clicks: 0").performClick()

        // The click drove state, which recomposed the Button with the new label; the old label is
        // gone and the new one is rendered on the same live component.
        onNodeWithText("Clicks: 0").assertDoesNotExist()
        onNodeWithText("Clicks: 1").assertExists()
    }

    @Test
    fun enabledStateTogglesAcrossRecomposition() = runComposeSwingTest {
        var enabledState by mutableStateOf(true)
        setContent {
            Button(text = "Toggle", onClick = {}, modifier = SwingModifier.enabled(enabledState))
        }
        onNodeWithText("Toggle").assertIsEnabled()

        enabledState = false
        awaitIdle()
        onNodeWithText("Toggle").assertIsNotEnabled()
    }
}
