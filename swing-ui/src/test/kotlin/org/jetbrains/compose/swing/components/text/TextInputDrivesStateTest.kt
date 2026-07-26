package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.BoxPanel
import org.jetbrains.compose.swing.test.runComposeSwingTest
import kotlin.test.Test

/**
 * Proves the full two-way data flow: a [TextField] action mutates Compose state through its document
 * listener, the recomposition re-renders a sibling [Label] bound to the same state, and the new text
 * is observable in the AWT tree. Exercises both replacement and incremental input.
 *
 * The field starts with a distinct, non-empty value so it can be located unambiguously by text.
 */
class TextInputDrivesStateTest {
    @Test
    fun textReplacementUpdatesBoundLabel() = runComposeSwingTest {
        var value by mutableStateOf("seed")
        setContent {
            BoxPanel {
                TextField(value = value, onValueChange = { value = it })
                Label(text = "Echo: $value")
            }
        }

        onNodeWithText("seed").assertExists()
        onNodeWithText("Echo: seed").assertExists()

        // Replace the field's text; the bound label must reflect it after recomposition.
        onNodeWithText("seed").performTextReplacement("hello")
        onNodeWithText("Echo: hello").assertExists()
        onNodeWithText("Echo: seed").assertDoesNotExist()

        onNodeWithText("hello").performTextReplacement("world")
        onNodeWithText("Echo: world").assertExists()
        onNodeWithText("Echo: hello").assertDoesNotExist()
    }

    @Test
    fun incrementalTextInputAccumulatesIntoState() = runComposeSwingTest {
        var value by mutableStateOf("ab")
        setContent {
            BoxPanel {
                TextField(value = value, onValueChange = { value = it })
                Label(text = "Echo: $value")
            }
        }

        onNodeWithText("Echo: ab").assertExists()

        // performTextInput appends to the current content.
        onNodeWithText("ab").performTextInput("cd")
        onNodeWithText("Echo: abcd").assertExists()
    }
}
