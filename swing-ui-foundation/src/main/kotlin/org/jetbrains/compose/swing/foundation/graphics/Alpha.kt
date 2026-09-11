@file:JvmMultifileClass
@file:JvmName("GraphicsKt")

package org.jetbrains.compose.swing.foundation.graphics

import androidx.annotation.FloatRange
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Graphics2D

/**
 * Paints the component at [alpha] of its opacity, content and children alike, cut to the decorated box as
 * androidx's `Modifier.alpha` cuts it.
 *
 * The fraction applies to each shape as it is painted, not to the faded area as one image: shapes that
 * overlap inside the faded area show through each other, where a group faded as one image would have
 * hidden the lower shape.
 *
 * Fades nest: a faded component inside a faded one paints at the product of the two.
 *
 * @param alpha the fraction of full opacity, from `0` for invisible to `1` for unchanged. A value above `1`
 *   paints as `1`, and one below `0` or `NaN` as `0`.
 * @return this chain with the fade declared on it; `1` decorates nothing.
 */
public fun SwingModifier.alpha(
    @FloatRange(from = 0.0, to = 1.0) alpha: Float,
): SwingModifier = decoration(DecoratorElement(AlphaDecorator(alpha).takeIf { alpha != 1f }))

/**
 * Multiplies the graphics composite used for the content, and clips the content to the area.
 *
 * @see alpha
 */
private data class AlphaDecorator(
    private val alpha: Float,
) : Decorator {
    override val isOpaque: Boolean get() = false

    override fun paint(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    ) {
        graphics.clipRect(0, 0, width, height)
        graphics.composite = graphics.composite.withAlpha(alpha)
        content(graphics, width, height)
    }
}
