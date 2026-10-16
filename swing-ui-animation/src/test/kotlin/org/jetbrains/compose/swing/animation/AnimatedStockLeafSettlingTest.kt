package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.Stretch.RATIO
import org.jetbrains.compose.swing.animation.core.LinearEasing
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.foundation.layout.WRAPPING_TEXT
import org.jetbrains.compose.swing.foundation.layout.WrappingTexts
import org.jetbrains.compose.swing.foundation.layout.aspectRatio
import org.jetbrains.compose.swing.foundation.layout.cyclesUntilStable
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JScrollPane
import kotlin.test.assertEquals

/**
 * An [AnimatedContent] under a stock parent, around a stock leaf whose answer follows the size it holds. The size
 * animation aims at the leaf's height at its natural width, which does not follow the size the animation gives it.
 */
class AnimatedStockLeafSettlingTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("leafNames")
    fun `a content that entered a stock leaf at an aspect ratio under a stock parent goes idle at the leaf's size`(
        leafName: String,
    ) = runComposeSwingTest {
        var entered by mutableStateOf(false)
        setContent {
            Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(320, 600)) {
                AnimatedContent(
                    entered,
                    SwingModifier.north().testTag(CONTAINER),
                    transitionSpec = {
                        EnterTransition.None togetherWith ExitTransition.None using
                            SizeTransform { _, _ -> tween(1000, easing = LinearEasing) }
                    },
                ) {
                    if (it) {
                        Leaves.getValue(leafName)(SwingModifier.testTag(CONTENT).aspectRatio(RATIO))
                    } else {
                        Label("Hi")
                    }
                }
            }
        }
        mainClock.autoAdvance = false
        entered = true
        driveOneFrame()
        driveOneFrame()
        val container = onNodeWithTag(CONTAINER).fetch<JComponent>()
        val content = onNodeWithTag(CONTENT).fetch<JComponent>()
        cyclesUntilStable(container)

        mainClock.autoAdvance = true
        awaitIdle()
        cyclesUntilStable(container)
        assertEquals(content.size, container.size, "the container once idle")
    }

    private companion object {
        const val CONTAINER = "container"
        const val CONTENT = "content"

        /** A label that keeps its size, then the stock components whose height follows their width. */
        val Leaves: Map<String, @Composable (SwingModifier) -> Unit> =
            mapOf<String, @Composable (SwingModifier) -> Unit>(
                "Label" to { Label("Preview", modifier = it) },
                "JPanel holding a wrapping JTextArea" to {
                    SwingNode(factory = { wrappingTextPanel(WRAPPING_TEXT.length) }, modifier = it)
                },
                "JScrollPane holding a JList" to {
                    SwingNode(factory = { JScrollPane(JList(arrayOf("One", "Two", "Three"))) }, modifier = it)
                },
            ) + WrappingTexts

        @JvmStatic
        fun leafNames(): List<String> = Leaves.keys.toList()
    }
}
