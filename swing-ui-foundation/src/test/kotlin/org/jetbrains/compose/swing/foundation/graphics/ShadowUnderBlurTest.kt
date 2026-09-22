package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import java.awt.geom.AffineTransform
import kotlin.test.Test
import kotlin.test.assertTrue

class ShadowUnderBlurTest {
    @Test
    fun aHalfScaleCastKeepsTheOffsetShadowOnTheRightAndBottom() =
        runComposeSwingTest {
            val canvas =
                decoratedCanvases(64, 64, 1, decoration = {
                    SwingModifier.shadow(0, Color(0, 0, 0, 100), 16, 16)
                }) {
                    graphics.color = Color.WHITE
                    graphics.fillRect(0, 0, width, height)
                }.single()
            val insets = canvas.paintOutsets
            val image =
                canvas.paintOnto(
                    canvas.width / 2,
                    canvas.height / 2,
                    AffineTransform.getScaleInstance(0.5, 0.5),
                )
            val layoutWidth = canvas.width - insets.left - insets.right
            val layoutHeight = canvas.height - insets.top - insets.bottom
            val right = image.opacityAt((insets.left + layoutWidth + 8) / 2, (insets.top + layoutHeight / 2) / 2)
            val bottom = image.opacityAt((insets.left + layoutWidth / 2) / 2, (insets.top + layoutHeight + 8) / 2)
            assertTrue(right >= 80, "the half-scale cast lost the right shadow: alpha $right")
            assertTrue(bottom >= 80, "the half-scale cast lost the bottom shadow: alpha $bottom")
        }

    @Test
    fun reducedBlurKeepsTheOffsetShadowOnTheRightAndBottom() =
        runComposeSwingTest {
            val canvases =
                decoratedCanvases(64, 64, 2, decoration = { index ->
                    SwingModifier.blur(index + 3).shadow(0, Color(0, 0, 0, 100), 16, 16)
                }) {
                    graphics.color = Color.WHITE
                    graphics.fillRect(0, 0, width, height)
                }

            canvases.forEachIndexed { index, canvas ->
                val insets = canvas.paintOutsets
                val image = canvas.paintOnto(canvas.width, canvas.height)
                val layoutWidth = canvas.width - insets.left - insets.right
                val layoutHeight = canvas.height - insets.top - insets.bottom
                val right = image.opacityAt(insets.left + layoutWidth + 8, insets.top + layoutHeight / 2)
                val bottom = image.opacityAt(insets.left + layoutWidth / 2, insets.top + layoutHeight + 8)
                assertTrue(right >= 80, "blur ${index + 3} erased the right shadow: alpha $right")
                assertTrue(bottom >= 80, "blur ${index + 3} erased the bottom shadow: alpha $bottom")
            }
        }
}
