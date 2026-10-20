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
import java.awt.Color
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.UIManager
import kotlin.test.Test

/** The tests cover how a node finds the nearest ancestor that provides styles as the tree changes. */
class StylePublisherTest {
    @Test
    fun aSubtreeMovedUnderAProviderAndAwayTakesItsStylesWithIt() = runComposeSwingTest {
        var underProvider by mutableStateOf(false)
        setContent {
            val content = remember { movableContentOf { Label("label") } }
            Panel(style = { provideNestedStyle(Labels) { background(Color.RED) } }) { if (underProvider) content() }
            Panel { if (!underProvider) content() }
        }
        val label = onNodeOfType<JLabel>()
        val original = UIManager.getColor("Label.background")
        label.assertProperty(original, "a label outside the provider keeps its own background") { background }

        underProvider = true
        awaitIdle()
        label.assertProperty(Color.RED, "the moved label takes the provider's style") { background }

        underProvider = false
        awaitIdle()
        label.assertProperty(original, "the label that moved away returns to its own background") { background }
    }

    @Test
    fun aProviderThatStartsAndStopsAboveASwingNodeHoleReachesTheLabelBelowIt() = runComposeSwingTest {
        var providing by mutableStateOf(false)
        val provider = SwingStyle<ContainerStyleScope> { provideNestedStyle(Labels) { background(Color.RED) } }
        setContent {
            Panel(style = if (providing) provider else SwingStyle) {
                SwingNode(factory = { JPanel() }) { Label("label") }
            }
        }
        val label = onNodeOfType<JLabel>()
        val original = UIManager.getColor("Label.background")

        providing = true
        awaitIdle()
        label.assertProperty(Color.RED, "the provider reaches the label across the hole") { background }

        providing = false
        awaitIdle()
        label.assertProperty(original, "the label's own background returns") { background }
    }

    @Test
    fun aLabelUnderNestedProvidersKeepsTheInnerOnesStyleWhenTheOuterStops() = runComposeSwingTest {
        var outerProviding by mutableStateOf(true)
        setContent {
            Panel(
                style = {
                    if (outerProviding) provideNestedStyle(Labels) { background(Color.RED) }
                },
            ) {
                Panel(style = { provideNestedStyle(Labels) { foreground(Color.GREEN) } }) { Label("label") }
            }
        }
        val label = onNodeOfType<JLabel>()
        val original = UIManager.getColor("Label.background")
        label.assertProperty(Color.RED, "the outer provider styles the label") { background }
        label.assertProperty(Color.GREEN, "the inner provider styles the label") { foreground }

        outerProviding = false
        awaitIdle()
        label.assertProperty(original, "the label loses what the outer provider stopped providing") { background }
        label.assertProperty(Color.GREEN, "the inner provider still styles the label") { foreground }
    }
}
