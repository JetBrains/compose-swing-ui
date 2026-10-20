package org.jetbrains.compose.swing.modifier

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.toolTip
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
    @Test
    fun oneModifierInstanceDrivesEveryComponentItIsGivenTo() = runComposeSwingTest {
        var shown by mutableStateOf(true)
        setContent {
            val gated = SwingModifier.opaque(shown).toolTip(SHARED_TIP)
            Label(text = "a", modifier = SwingModifier.name("a") then gated)
            Label(text = "b", modifier = SwingModifier.name("b") then gated)
            Label(text = "c", modifier = SwingModifier.name("c") then gated)
        }

        for (id in listOf("a", "b", "c")) {
            val label = onNodeWithName(id).assertIsOfType<JLabel>()
            label.assertProperty(true, "$id starts opaque") { isOpaque }
            label.assertProperty(SHARED_TIP, "$id took the shared tooltip") { toolTipText }
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
        var styled by mutableStateOf(true)
        setContent {
            val shared = SwingModifier.toolTip(SHARED_TIP)
            Label(text = "a", modifier = SwingModifier.name("a").let { if (styled) it then shared else it })
            Label(text = "b", modifier = SwingModifier.name("b").let { if (styled) it then shared else it })
        }

        for (id in listOf("a", "b")) {
            val label = onNodeWithName(id).assertIsOfType<JLabel>()
            label.assertProperty(SHARED_TIP, "$id took the shared tooltip") { toolTipText }
        }

        styled = false
        awaitIdle()

        for (id in listOf("a", "b")) {
            val label = onNodeWithName(id).assertIsOfType<JLabel>()
            label.assertProperty(null, "$id is restored to the tooltip it had before the shared element") {
                toolTipText
            }
        }
    }
}

private const val SHARED_TIP = "shared"
