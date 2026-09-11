package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Color
import java.awt.Graphics2D
import java.awt.geom.RoundRectangle2D

/** Runs [modifier], for a decoration declared on a component written outside a `Row`, `Column` or `Box`. */
internal fun decorated(modifier: () -> SwingModifier): SwingModifier = modifier()

internal fun SwingModifier.fill(color: Color): SwingModifier = decoration(Fill(color))

internal fun SwingModifier.cut(arc: Int = ELLIPSE): SwingModifier = decoration(Cut(arc))

private const val ELLIPSE = Int.MAX_VALUE

/** Fills the area with [color], then paints the content over it. */
internal data class Fill(
    private val color: Color,
) : Decorator {
    override fun paint(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    ) {
        graphics.color = color
        graphics.fillRect(0, 0, width, height)
        content(graphics, width, height)
    }
}

/** Cuts everything inside it to a rounded rectangle whose corners are [arc] across; the default cuts an ellipse. */
internal data class Cut(
    private val arc: Int = ELLIPSE,
) : Decorator {
    override val isOpaque: Boolean get() = false

    override fun paint(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    ) {
        val across = minOf(arc, width).toFloat()
        val down = minOf(arc, height).toFloat()
        graphics.clip(RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), across, down))
        content(graphics, width, height)
    }
}
