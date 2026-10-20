package org.jetbrains.compose.swing.modifier

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.appearance.clientProperty
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.test.interaction.assertIsOfType
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JLabel
import kotlin.test.Test

/**
 * One modifier value handed to several components: each keeps its own applied state, follows the
 * shared declaration on every change, and is restored on its own when the declaration leaves.
 */
class SharedModifierElementTest {
    private val opaque =
        ComponentPropertyDescriptor<JLabel, Boolean>(
            "opaque",
            { it.isOpaque },
            { label, value -> label.isOpaque = value },
        )

    @Test
    fun oneModifierInstanceDrivesEveryComponentItIsGivenTo() = runComposeSwingTest {
        val key = "shared"
        var shown by mutableStateOf(true)
        setContent {
            val gated = SwingModifier.property(opaque, shown).clientProperty(key, "value")
            Label(text = "a", modifier = SwingModifier.name("a") then gated)
            Label(text = "b", modifier = SwingModifier.name("b") then gated)
            Label(text = "c", modifier = SwingModifier.name("c") then gated)
        }

        for (id in listOf("a", "b", "c")) {
            onNodeWithName(id).assertIsOfType<JLabel>().apply {
                assertProperty(true, "$id starts opaque") { isOpaque }
                assertProperty("value", "$id took the shared client property") { getClientProperty(key) }
            }
        }

        shown = false
        awaitIdle()

        for (id in listOf("a", "b", "c")) {
            onNodeWithName(id).assertProperty(false, "$id followed the shared change") { isOpaque }
        }

        shown = true
        awaitIdle()

        for (id in listOf("a", "b", "c")) {
            onNodeWithName(id).assertProperty(true, "$id followed the shared change back") { isOpaque }
        }
    }

    @Test
    fun aSharedElementLeavingTheChainRestoresEveryComponentThatCarriedIt() = runComposeSwingTest {
        val key = "shared"
        var styled by mutableStateOf(true)
        setContent {
            val shared = SwingModifier.clientProperty(key, "value")
            Label(text = "a", modifier = SwingModifier.name("a").let { if (styled) it then shared else it })
            Label(text = "b", modifier = SwingModifier.name("b").let { if (styled) it then shared else it })
        }

        for (id in listOf("a", "b")) {
            onNodeWithName(id)
                .assertIsOfType<JLabel>()
                .assertProperty("value", "$id took the shared client property") { getClientProperty(key) }
        }

        styled = false
        awaitIdle()

        for (id in listOf("a", "b")) {
            onNodeWithName(id).assertIsOfType<JLabel>().assertProperty(
                null,
                "$id is restored to the client property it had before the shared element",
            ) {
                getClientProperty(key)
            }
        }
    }
}
