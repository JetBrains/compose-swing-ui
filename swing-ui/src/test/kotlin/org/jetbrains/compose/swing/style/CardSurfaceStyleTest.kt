package org.jetbrains.compose.swing.style

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.style.Labels
import org.jetbrains.compose.swing.components.style.Panels
import org.jetbrains.compose.swing.core.awaitUntil
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.underMetal
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Color
import java.awt.Graphics
import java.awt.GraphicsEnvironment
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.RepaintManager
import javax.swing.UIManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CardSurfaceStyleTest {
    @Test
    fun aNestedPanelOnTheCardPaintsTheCardColor() = underMetal {
        runComposeSwingTest {
            setContent {
                Panel(style = CardStyle) {
                    Panel(modifier = SwingModifier.testTag("nested"), style = { preferredSize(40, 40) }) {}
                    Button("button", onClick = {})
                }
            }

            val image = onNodeWithTag("nested").captureToImage()
            assertNotEquals(CardColor, UIManager.getColor("Panel.background"))
            assertEquals(CardColor.rgb, image.getRGB(20, 20), "the nested panel paints the card color")
            val button = onNodeOfType<JButton>()
            button.assertProperty(UIManager.getColor("Button.background")) { background }
            button.assertProperty(true) { isContentAreaFilled == isOpaque }
        }
    }

    @Test
    fun aStyleThatStopsSettingOpaqueOnAButtonReturnsTheFlagToContentAreaFilled() = underMetal {
        runComposeSwingTest {
            var styled by mutableStateOf(true)
            setContent { Button("button", onClick = {}, style = { if (styled) opaque(true) }) }
            val button = onNodeOfType<JButton>()
            button.assertProperty(true) { isOpaque }

            button.fetch().isContentAreaFilled = false
            awaitIdle()
            button.assertProperty(true, "the style keeps its flag over the one the look and feel derives") { isOpaque }

            styled = false
            awaitIdle()
            button.assertProperty(false, "the flag follows contentAreaFilled again, not the value read first") {
                isOpaque
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun opaqueLabelsRepaintWithoutRepaintingTheCard(styleLabels: Boolean) = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        underMetal {
            val style =
                if (styleLabels) {
                    CardStyle
                } else {
                    SwingStyle<ContainerStyleScope> {
                        provideNestedStyle(Panels) { cardSurface(CardColor) }
                    }
                }
            val frame = JFrame()
            val card = PaintCountingPanel()
            frame.isVisible = true
            val content =
                frame.setContent {
                    SwingNode(factory = { card }, modifier = SwingModifier.styleable(style, CardKind)) {
                        Label("on the card")
                    }
                }
            try {
                frame.setSize(200, 120)
                frame.validate()
                awaitUntil("the label shows") { card.componentCount == 1 && card.getComponent(0).isShowing }
                val label = card.getComponent(0) as JLabel
                val repaintManager = RepaintManager.currentManager(label)
                repaintManager.paintDirtyRegions()
                card.paints = 0

                label.repaint()
                repaintManager.paintDirtyRegions()
                if (styleLabels) {
                    assertEquals(0, card.paints, "an opaque label repaints on its own")
                } else {
                    assertTrue(card.paints > 0, "a label that is not opaque repaints from the card")
                }
            } finally {
                content.dispose()
                frame.dispose()
            }
        }
    }

    /** A panel that counts its own paints. */
    private class PaintCountingPanel : JPanel() {
        var paints = 0

        override fun paintComponent(g: Graphics) {
            paints++
            super.paintComponent(g)
        }
    }

    private companion object {
        val CardColor = Color(0x33, 0x99, 0x66)

        val CardStyle =
            SwingStyle<ContainerStyleScope> {
                provideNestedStyle(Panels) { cardSurface(CardColor) }
                provideNestedStyle(Labels) { cardSurface(CardColor) }
            }

        val CardKind =
            SwingStyleKind<ContainerStyleScope, Unit>({ base, _ ->
                object : ContainerStyleScope, NestedStyleProviderScope by base {}
            })

        fun SwingComponentStyleScope.cardSurface(color: Color) {
            background(color)
            opaque(true)
        }
    }
}
