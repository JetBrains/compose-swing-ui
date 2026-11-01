package org.jetbrains.compose.swing.modifier

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.appearance.foreground
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.modifier.layout.visible
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import javax.swing.JLabel
import kotlin.test.Test

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
            val label = onNodeWithName(id)
            label.assertProperty(true, "$id starts visible") { isVisible }
            label.assertProperty(Color.RED, "$id took the shared foreground") { foreground }
        }

        shown = false
        awaitIdle()

        for (id in listOf("a", "b", "c")) {
            onNodeWithName(id).assertProperty(false, "$id followed the shared change") { isVisible }
        }

        shown = true
        awaitIdle()

        for (id in listOf("a", "b", "c")) {
            onNodeWithName(id).assertProperty(true, "$id followed the shared change back") { isVisible }
        }
    }

    @Test
    fun aSharedElementLeavingTheChainRestoresEveryComponentThatCarriedIt() = runComposeSwingTest {
        var styled by mutableStateOf(true)
        setContent {
            val shared = SwingModifier.foreground(Color.RED)
            Label(text = "a", modifier = SwingModifier.name("a").let { if (styled) it then shared else it })
            Label(text = "b", modifier = SwingModifier.name("b").let { if (styled) it then shared else it })
        }

        for (id in listOf("a", "b")) {
            onNodeWithName(id).assertProperty(Color.RED, "$id took the shared foreground") { foreground }
        }

        styled = false
        awaitIdle()

        val lookAndFeelForeground = JLabel().foreground
        for (id in listOf("a", "b")) {
            onNodeWithName(id).assertProperty(
                lookAndFeelForeground,
                "$id is restored to the foreground it had before the shared element",
            ) { foreground }
        }
    }
}
