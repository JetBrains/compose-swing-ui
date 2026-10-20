package org.jetbrains.compose.swing.style

import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.style.Labels
import org.jetbrains.compose.swing.components.style.Panels
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.underMetal
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Color
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.UIManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

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

            assertNotEquals(CardColor, UIManager.getColor("Panel.background"))
            assertEquals(
                CardColor.rgb,
                onNodeWithTag("nested").captureToImage().getRGB(20, 20),
                "the nested panel paints the card color",
            )
            onNodeOfType<JButton>().apply {
                assertProperty(UIManager.getColor("Button.background")) { background }
                assertProperty(true) { isContentAreaFilled == isOpaque }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun onlyANestedLabelStyleMakesALabelOnTheCardOpaque(styleLabels: Boolean) = runComposeSwingTest {
        val style =
            if (styleLabels) {
                CardStyle
            } else {
                SwingStyle<ContainerStyleScope> {
                    provideNestedStyle(Panels) { cardSurface(CardColor) }
                }
            }
        setContent {
            Panel(style = style) {
                Label("on the card")
            }
        }

        onNodeOfType<JLabel>().apply {
            assertProperty(styleLabels) { isOpaque }
            assertProperty(if (styleLabels) CardColor else UIManager.getColor("Label.background")) { background }
        }
    }

    private companion object {
        val CardColor = Color(0x33, 0x99, 0x66)

        val CardStyle =
            SwingStyle<ContainerStyleScope> {
                provideNestedStyle(Panels) { cardSurface(CardColor) }
                provideNestedStyle(Labels) { cardSurface(CardColor) }
            }

        fun SwingComponentStyleScope.cardSurface(color: Color) {
            background(color)
            opaque(true)
        }
    }
}
