@file:JvmMultifileClass
@file:JvmName("GraphicsKt")

package org.jetbrains.compose.swing.foundation.graphics

import androidx.annotation.FloatRange
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints

/**
 * Fills [shape]'s outline with [brush] under the component's content.
 *
 * An opaque component, such as a custom `JPanel`-based [Decoratable] under most looks and feels, fills its
 * background over the brush, so declare [opaque][org.jetbrains.compose.swing.modifier.appearance.opaque]`(false)`
 * on such a component for the fill to show. A [Row][org.jetbrains.compose.swing.foundation.layout.Row],
 * [Column][org.jetbrains.compose.swing.foundation.layout.Column] and
 * [Box][org.jetbrains.compose.swing.foundation.layout.Box] are transparent unless set opaque. An outline other than
 * [RectangleShape] is filled with an antialiased edge, as AndroidX's `background` fills it.
 *
 * @param brush the fill, resolved against the [decorated box][decoration].
 * @param shape the outline to fill; the whole decorated box by default.
 * @param alpha the fraction of the brush's opacity to fill with; `1` by default. A value above `1` fills as `1`,
 *   and one below `0` or `NaN` as `0`.
 * @return this chain with the background declared on it.
 */
public fun SwingModifier.background(
    brush: Brush,
    shape: Shape = RectangleShape,
    @FloatRange(from = 0.0, to = 1.0) alpha: Float = 1f,
): SwingModifier = decoration(BackgroundDecorator(brush, shape, alpha))

/**
 * Fills [shape]'s outline with [color] under the component's content; the solid-color overload of
 * [background][SwingModifier.background].
 *
 * [shape] has no default here: the [background][org.jetbrains.compose.swing.modifier.appearance.background] taking
 * a [Color] alone writes the Swing property, which is the one a look and feel honors, and a call naming only a color
 * keeps meaning that one wherever both are imported.
 *
 * @param color the fill.
 * @param shape the outline to fill; [RectangleShape] for the whole decorated box.
 * @return this chain with the background declared on it.
 */
public fun SwingModifier.background(
    color: Color,
    shape: Shape,
): SwingModifier = decoration(BackgroundDecorator(SolidColor(color), shape, 1f))

/**
 * Fills the shape's outline with the brush before painting its content.
 *
 * @see background
 */
private data class BackgroundDecorator(
    private val brush: Brush,
    private val shape: Shape,
    private val alpha: Float,
) : Decorator {
    override fun paint(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    ) {
        val carriedPaint = graphics.paint
        val carriedComposite = graphics.composite
        graphics.paint = brush.paint(width, height)
        if (alpha != 1f) graphics.composite = carriedComposite.withAlpha(alpha)
        if (shape == RectangleShape) {
            graphics.fillRect(0, 0, width, height)
        } else {
            val carriedAntialiasing = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.fill(shape.outline(width, height))
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, carriedAntialiasing)
        }
        graphics.paint = carriedPaint
        graphics.composite = carriedComposite
        content(graphics, width, height)
    }
}
