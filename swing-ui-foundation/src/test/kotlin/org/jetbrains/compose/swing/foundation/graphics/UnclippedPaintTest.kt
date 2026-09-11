package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Color
import java.awt.image.BufferedImage
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

/**
 * A paint through a graphics without a clip - a capture, or printing to a fresh image - paints what a
 * paint clipped to the component does. A decorator that hands its content a graphics without a clip fails
 * with guidance once a blur inside it records that content.
 */
class UnclippedPaintTest {
    @Test
    fun aShadowInsideABlurKeepsItsHalo() = assertUnclippedMatchesClipped { blur(2).shadow(8, Color.BLACK, 6, 6) }

    @Test
    fun aShadowInsideAShadowKeepsItsHalo() =
        assertUnclippedMatchesClipped { shadow(2, Color.BLACK).shadow(8, Color.BLACK, 6, 6) }

    @Test
    fun aDecoratorHandingABlurAnUnclippedGraphicsFails() =
        runComposeSwingTest {
            val offscreen =
                Decorator { _, width, height, content ->
                    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                    val graphics = image.createGraphics()
                    try {
                        content(graphics, width, height)
                    } finally {
                        graphics.dispose()
                    }
                }
            setContent {
                DecoratedCanvas(
                    modifier = {
                        SwingModifier
                            .testTag("decorated")
                            .preferredSize(48, 48)
                            .decoration(offscreen)
                            .blur(2)
                    },
                ) { drawRect(Color.RED) }
            }
            val component = onNodeWithTag("decorated").fetch<JComponent>()

            val failure =
                assertFailsWith<IllegalStateException> {
                    renderImage(component.width, component.height) {
                        it.clipRect(0, 0, component.width, component.height)
                        component.paint(it)
                    }
                }

            assertContains(failure.message.orEmpty(), "hand a decorator's content a clipped graphics")
        }

    private fun assertUnclippedMatchesClipped(decorate: SwingModifier.() -> SwingModifier) =
        runComposeSwingTest {
            setContent {
                Box {
                    DecoratedCanvas(
                        modifier = { SwingModifier.testTag("decorated").preferredSize(48, 48).decorate() },
                    ) {
                        drawRect(Color.RED)
                    }
                }
            }
            val component = onNodeWithTag("decorated").fetch<JComponent>()
            val clipped =
                renderImage(component.width, component.height) {
                    it.clipRect(0, 0, component.width, component.height)
                    component.paint(it)
                }

            assertImagesPixelPerfect(clipped, onNodeWithTag("decorated").captureToImage())
        }
}
