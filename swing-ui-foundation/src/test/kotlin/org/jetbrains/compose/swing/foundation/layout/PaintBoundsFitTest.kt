package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.graphics.Decorator
import org.jetbrains.compose.swing.foundation.graphics.decoration
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.Rectangle
import java.awt.Shape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaintBoundsFitTest {
    @Test
    fun aFitWorksOutAStepsPaintBoundsOnceWhenNoChildOverflows() =
        runComposeSwingTest {
            val step = CountingBounds()
            setContent {
                Row {
                    Box(modifier = SwingModifier.testTag("box").size(20, 20).decoration(step))
                }
            }
            val component = onNodeWithTag("box").fetch<Component>()
            val decoration = (component as Decoratable).decoration
            step.calls = 0

            val fitted = decoration.fitted(component)

            assertEquals(1, step.calls)
            assertEquals(Insets(0, 0, 0, 5), fitted.heldPaintOutsets)
            assertTrue(fitted.holdsTransform, "the shifted paint bounds move the content")
        }
}

private class CountingBounds : Decorator {
    var calls: Int = 0

    override fun paint(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    ) = content(graphics, width, height)

    override fun paintBounds(
        content: Shape,
        width: Int,
        height: Int,
    ): Shape {
        calls++
        return Rectangle(0, 0, width, height).apply { add(content.bounds.apply { translate(5, 0) }) }
    }
}
