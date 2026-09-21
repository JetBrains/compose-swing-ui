// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.style

import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.style.ContainerStyleScope
import org.jetbrains.compose.swing.style.SwingComponentStyleScope
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.background
import org.jetbrains.compose.swing.style.opaque
import org.jetbrains.compose.swing.style.preferredSize
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
        val cardColor = Color(0x33, 0x99, 0x66)
        val style =
            if (styleLabels) {
                SwingStyle<ContainerStyleScope> {
                    provideNestedStyle(Panels) { cardSurface(cardColor) }
                    provideNestedStyle(Labels) { cardSurface(cardColor) }
                }
            } else {
                SwingStyle<ContainerStyleScope> {
                    provideNestedStyle(Panels) { cardSurface(cardColor) }
                }
            }
        setContent {
            Panel(style = style) {
                Label("on the card")
            }
        }

        onNodeOfType<JLabel>().apply {
            assertProperty(styleLabels) { isOpaque }
            assertProperty(if (styleLabels) cardColor else UIManager.getColor("Label.background")) { background }
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
