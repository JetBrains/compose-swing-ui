package org.jetbrains.compose.swing.style

import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.style.Labels
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Color
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.UIManager

class StylePublisherTest {
    @ParameterizedTest
    @ValueSource(strings = ["move", "remove outer style", "raw SwingNode"])
    fun providerChangesReachDescendants(change: String) = runComposeSwingTest {
        var providing by mutableStateOf(false)
        setContent {
            val content =
                remember {
                    movableContentOf {
                        Panel(style = { provideNestedStyle(Labels) { foreground(Color.GREEN) } }) { Label("label") }
                    }
                }
            Panel(style = {
                if (providing || change == "move") provideNestedStyle(Labels) { background(Color.RED) }
            }) {
                when (change) {
                    "move" -> if (providing) content()
                    "raw SwingNode" -> SwingNode(factory = { JPanel() }) { content() }
                    else -> content()
                }
            }
            if (change == "move") Panel { if (!providing) content() }
        }
        val label = onNodeOfType<JLabel>()
        val original = UIManager.getColor("Label.background")
        label.assertProperty(original) { background }
        label.assertProperty(Color.GREEN) { foreground }

        providing = true
        awaitIdle()
        label.assertProperty(Color.RED) { background }
        label.assertProperty(Color.GREEN) { foreground }

        providing = false
        awaitIdle()
        label.assertProperty(original) { background }
        label.assertProperty(Color.GREEN) { foreground }
    }
}
