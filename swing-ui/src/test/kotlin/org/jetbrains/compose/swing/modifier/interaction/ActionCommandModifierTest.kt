package org.jetbrains.compose.swing.modifier.interaction

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.event.ActionListener
import javax.swing.AbstractButton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The command string is what a listener shared by several buttons reads to learn which of them fired,
 * so these drive it through an action event rather than through the property. They pin that a declared
 * command reaches the event, that it follows the state driving it, and that dropping it restores the
 * substitution a button makes when no command is set - reporting its own text.
 */
class ActionCommandModifierTest {
    @Test
    fun oneListenerTellsTwoButtonsApartByTheirCommands() = runComposeSwingTest {
        val fired = mutableListOf<String>()
        setContent {
            val listener = remember { ActionListener { fired += it.actionCommand } }
            Button("Save", actionListener = listener, modifier = SwingModifier.actionCommand("save"))
            Button("Discard", actionListener = listener, modifier = SwingModifier.actionCommand("discard"))
        }

        onNodeWithText("Discard").performClick()
        onNodeWithText("Save").performClick()

        assertEquals(listOf("discard", "save"), fired, "each button's event must carry its own command")
    }

    @Test
    fun theCommandFollowsTheStateDrivingIt() = runComposeSwingTest {
        var command by mutableStateOf("save")
        val fired = mutableListOf<String>()
        setContent {
            val listener = remember { ActionListener { fired += it.actionCommand } }
            Button("Save", actionListener = listener, modifier = SwingModifier.actionCommand(command))
        }

        onNodeWithText("Save").performClick()
        command = "save-as"
        awaitIdle()
        onNodeWithText("Save").performClick()

        assertEquals(listOf("save", "save-as"), fired, "the command follows the state driving it")
    }

    @Test
    fun droppingTheCommandRestoresTheButtonsOwnText() = runComposeSwingTest {
        var named by mutableStateOf(true)
        val fired = mutableListOf<String>()
        setContent {
            val listener = remember { ActionListener { fired += it.actionCommand } }
            Button(
                "Save",
                actionListener = listener,
                modifier = if (named) SwingModifier.actionCommand("save") else SwingModifier,
            )
        }

        onNodeWithText("Save").performClick()
        named = false
        awaitIdle()
        onNodeWithText("Save").performClick()

        // With nothing setting a command, a button substitutes its own text, so that is what returns -
        // not the text frozen into the property at the moment the element was applied.
        assertEquals(listOf("save", "Save"), fired, "dropping the modifier restores the text substitution")
    }

    @Test
    fun aComponentThatIsNotAButtonIsRejected() {
        val failure =
            assertFailsWith<IllegalStateException> {
                runComposeSwingTest {
                    setContent { Label("Legend", modifier = SwingModifier.actionCommand("legend")) }
                }
            }

        assertTrue(
            AbstractButton::class.java.name in failure.message.orEmpty(),
            "the message should name the required type, but was: ${failure.message}",
        )
    }
}
