@file:JvmMultifileClass
@file:JvmName("GraphicsDrawScopeKt")

package org.jetbrains.compose.swing.foundation.graphics.drawscope

import androidx.annotation.FloatRange
import java.awt.Paint
import java.awt.Stroke
import java.awt.geom.Point2D

/**
 * Draws a line between [start] and [end].
 *
 * @param paint the paint or color used to render the line.
 * @param start start point.
 * @param end end point.
 * @param stroke the [Stroke] used to outline the line.
 * @param alpha the opacity to render with.
 */
public fun DrawScope.drawLine(
    paint: Paint,
    start: Point2D,
    end: Point2D,
    stroke: Stroke = DrawStyle.DefaultStroke.stroke,
    @FloatRange(from = 0.0, to = 1.0) alpha: Float = 1.0f,
): Unit = drawLine(paint, start.x.toFloat(), start.y.toFloat(), end.x.toFloat(), end.y.toFloat(), stroke, alpha)

/**
 * Draws a circle with [radius] centered at [center].
 *
 * @param paint the paint or color used to render the circle.
 * @param radius the radius of the circle.
 * @param center center point.
 * @param style whether to fill or outline the circle.
 * @param alpha the opacity to render with.
 */
public fun DrawScope.drawCircle(
    paint: Paint,
    radius: Float = minOf(size.width, size.height) / 2.0f,
    center: Point2D,
    style: DrawStyle = DrawStyle.Fill,
    @FloatRange(from = 0.0, to = 1.0) alpha: Float = 1.0f,
): Unit = drawCircle(paint, radius, center.x.toFloat(), center.y.toFloat(), style, alpha)
