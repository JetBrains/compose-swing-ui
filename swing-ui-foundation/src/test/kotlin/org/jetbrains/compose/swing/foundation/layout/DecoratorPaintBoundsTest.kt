package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.graphics.Decorator
import org.jetbrains.compose.swing.foundation.graphics.decoration
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.Rectangle
import java.awt.Shape
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals

class DecoratorPaintBoundsTest {
    @Test
    fun aCustomDecoratorsPaintBoundsWorkAtLayoutAndModifierBoxes() =
        runComposeSwingTest {
            val decorator =
                object : Decorator {
                    override val isOpaque: Boolean get() = false

                    override fun paintBounds(
                        content: Shape,
                        width: Int,
                        height: Int,
                    ): Shape = Rectangle(-6, -6, width + 12, height + 12)

                    override fun paint(
                        graphics: Graphics2D,
                        width: Int,
                        height: Int,
                        content: (Graphics2D, Int, Int) -> Unit,
                    ) {
                        graphics.color = Color.RED
                        graphics.fillRect(-6, -6, width + 12, height + 12)
                        content(graphics, width, height)
                    }
                }
            setContent {
                Row {
                    Box(modifier = SwingModifier.testTag("direct").size(20, 20).decoration(decorator))
                    Box(modifier = SwingModifier.testTag("boxed").decoration(decorator).padding(2)) {
                        Box(modifier = SwingModifier.size(20, 20))
                    }
                }
            }

            val direct = onNodeWithTag("direct").fetch<JComponent>()
            val boxed = onNodeWithTag("boxed").fetch<JComponent>()
            assertEquals(Insets(6, 6, 6, 6), (direct as Decoratable).decoration.paintOutsets())
            assertEquals(Insets(8, 8, 8, 8), (boxed as Decoratable).decoration.paintOutsets())
            assertEquals(Color.RED.rgb, direct.captureToImage().getRGB(0, 0))
            assertEquals(Color.RED.rgb, boxed.captureToImage().getRGB(0, 0))
        }
}
