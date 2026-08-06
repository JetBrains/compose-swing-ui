package org.jetbrains.compose.swing.modifier

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.appearance.foreground
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.modifier.layout.visible
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * One modifier value handed to several components: each keeps its own applied state, follows the
 * shared declaration on every change, and is restored on its own when the declaration leaves.
 */
class SharedModifierElementTest {
    @Test
    fun oneModifierInstanceDrivesEveryComponentItIsGivenTo() = runComposeSwingTest {
        var shown by mutableStateOf(true)
        setContent {
            val gated = SwingModifier.visible(shown).foreground(Color.RED)
            Label(text = "a", modifier = SwingModifier.name("a") then gated)
            Label(text = "b", modifier = SwingModifier.name("b") then gated)
            Label(text = "c", modifier = SwingModifier.name("c") then gated)
        }

        for (id in listOf("a", "b", "c")) {
            val label = onNodeWithName(id).fetch<JLabel>()
            assertTrue(label.isVisible, "$id starts visible")
            assertEquals(Color.RED, label.foreground, "$id took the shared foreground")
        }

        shown = false
        awaitIdle()

        for (id in listOf("a", "b", "c")) {
            assertFalse(onNodeWithName(id).fetch<JLabel>().isVisible, "$id followed the shared change")
        }

        shown = true
        awaitIdle()

        for (id in listOf("a", "b", "c")) {
            assertTrue(onNodeWithName(id).fetch<JLabel>().isVisible, "$id followed the shared change back")
        }
    }

    @Test
    fun aSharedElementLeavingTheChainRestoresEveryComponentThatCarriedIt() = runComposeSwingTest {
        var styled by mutableStateOf(true)
        lateinit var originals: List<Color>
        setContent {
            val shared = SwingModifier.foreground(Color.RED)
            Label(text = "a", modifier = SwingModifier.name("a").let { if (styled) it then shared else it })
            Label(text = "b", modifier = SwingModifier.name("b").let { if (styled) it then shared else it })
        }

        originals = listOf("a", "b").map { onNodeWithName(it).fetch<JLabel>().foreground }
        assertEquals(listOf(Color.RED, Color.RED), originals)

        styled = false
        awaitIdle()

        val restored = listOf("a", "b").map { onNodeWithName(it).fetch<JLabel>().foreground }
        assertTrue(restored.none { it == Color.RED }, "both were restored, got $restored")
    }
}
