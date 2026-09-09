@file:JvmMultifileClass
@file:JvmName("GraphicsDrawScopeKt")

package org.jetbrains.compose.swing.foundation.graphics.drawscope

import androidx.annotation.FloatRange
import java.awt.Font
import java.awt.Paint
import java.awt.font.TextLayout
import java.awt.geom.Point2D

/**
 * Measures the layout of [text] under [font] (or the current font if null) using the canvas's
 * font render context.
 *
 * @param text the text string to measure.
 * @param font the font to use for measurement, or null to use the current font on [DrawScope.graphics].
 * @return a Java2D [TextLayout] holding measured bounds, ascent, descent, and advance.
 * @throws IllegalArgumentException if [text] is empty.
 */
public fun DrawScope.measureText(
    text: String,
    font: Font? = null,
): TextLayout {
    require(text.isNotEmpty()) { "Cannot measure an empty string with TextLayout" }
    val effectiveFont = font ?: graphics.font
    return TextLayout(text, effectiveFont, graphics.fontRenderContext)
}

/**
 * Draws [text] with its top-left corner positioned at ([x], [y]).
 *
 * Unlike raw [java.awt.Graphics2D.drawString] which places the font baseline at [y], this follows AndroidX's
 * convention where ([x], [y]) is the top-left corner of the rendered text block.
 *
 * @param text the text to render.
 * @param x horizontal offset of the top-left corner.
 * @param y vertical offset of the top-left corner.
 * @param paint the [Paint] to draw with, or null to keep the graphics context's current paint.
 * @param font the [Font] to render with, or null to keep the graphics context's current font.
 * @param alpha the opacity to render with.
 */
@Suppress("LongParameterList") // The TextLayout overload's parameters, with the text and its font in its place.
public fun DrawScope.drawText(
    text: String,
    x: Float = 0f,
    y: Float = 0f,
    paint: Paint? = null,
    font: Font? = null,
    @FloatRange(from = 0.0, to = 1.0) alpha: Float = 1.0f,
) {
    if (text.isEmpty()) return
    drawText(measureText(text, font), x, y, paint, alpha)
}

/**
 * Draws [text] with its top-left corner positioned at [topLeft].
 *
 * @param text the text to render.
 * @param topLeft coordinates of the top-left corner.
 * @param paint the [Paint] to draw with, or null to keep the graphics context's current paint.
 * @param font the [Font] to render with, or null to keep the graphics context's current font.
 * @param alpha the opacity to render with.
 */
public fun DrawScope.drawText(
    text: String,
    topLeft: Point2D,
    paint: Paint? = null,
    font: Font? = null,
    @FloatRange(from = 0.0, to = 1.0) alpha: Float = 1.0f,
): Unit = drawText(text, topLeft.x.toFloat(), topLeft.y.toFloat(), paint, font, alpha)

/**
 * Draws [textLayout] with its top-left corner positioned at [topLeft].
 *
 * @param textLayout the pre-computed text layout to render.
 * @param topLeft coordinates of the top-left corner.
 * @param paint the [Paint] to draw with, or null to keep the graphics context's current paint.
 * @param alpha the opacity to render with.
 */
public fun DrawScope.drawText(
    textLayout: TextLayout,
    topLeft: Point2D,
    paint: Paint? = null,
    @FloatRange(from = 0.0, to = 1.0) alpha: Float = 1.0f,
): Unit = drawText(textLayout, topLeft.x.toFloat(), topLeft.y.toFloat(), paint, alpha)
