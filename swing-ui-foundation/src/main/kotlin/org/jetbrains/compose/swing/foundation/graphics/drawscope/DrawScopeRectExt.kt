/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Adapted from androidx.compose.ui.graphics.drawscope.DrawScope in AndroidX's ui-graphics; see
 * this module's META-INF/NOTICE for the synced version. The drawRect/drawArc(topLeft, size)
 * overloads and their size parameter doc mirror upstream's.
 */

@file:JvmMultifileClass
@file:JvmName("GraphicsDrawScopeKt")

package org.jetbrains.compose.swing.foundation.graphics.drawscope

import androidx.annotation.FloatRange
import java.awt.Image
import java.awt.Paint
import java.awt.geom.Dimension2D
import java.awt.geom.Point2D

/**
 * Draws a rectangle positioned at [topLeft] with size [size].
 *
 * @param paint the paint or color used to render the rectangle.
 * @param topLeft coordinates of the top-left corner.
 * @param size dimensions of the rectangle.
 * @param style whether to fill or outline the rectangle.
 * @param alpha the opacity to render with.
 */
public fun DrawScope.drawRect(
    paint: Paint,
    topLeft: Point2D,
    size: Dimension2D,
    style: DrawStyle = DrawStyle.Fill,
    @FloatRange(from = 0.0, to = 1.0) alpha: Float = 1.0f,
): Unit =
    drawRect(paint, topLeft.x.toFloat(), topLeft.y.toFloat(), size.width.toFloat(), size.height.toFloat(), style, alpha)

/**
 * Draws a rounded rectangle positioned at [topLeft] with size [size].
 *
 * @param paint the paint or color used to render the rounded rectangle.
 * @param topLeft coordinates of the top-left corner.
 * @param size dimensions of the rounded rectangle.
 * @param cornerRadiusX horizontal radius of the corner arcs.
 * @param cornerRadiusY vertical radius of the corner arcs.
 * @param style whether to fill or outline the shape.
 * @param alpha the opacity to render with.
 */
@Suppress("LongParameterList") // DrawScope.drawRoundRect's parameters, with its box as a point and a size.
public fun DrawScope.drawRoundRect(
    paint: Paint,
    topLeft: Point2D,
    size: Dimension2D,
    cornerRadiusX: Float = 0f,
    cornerRadiusY: Float = cornerRadiusX,
    style: DrawStyle = DrawStyle.Fill,
    @FloatRange(from = 0.0, to = 1.0) alpha: Float = 1.0f,
): Unit =
    drawRoundRect(
        paint = paint,
        x = topLeft.x.toFloat(),
        y = topLeft.y.toFloat(),
        width = size.width.toFloat(),
        height = size.height.toFloat(),
        cornerRadiusX = cornerRadiusX,
        cornerRadiusY = cornerRadiusY,
        style = style,
        alpha = alpha,
    )

/**
 * Draws an oval within the bounding box defined by [topLeft] and [size].
 *
 * @param paint the paint or color used to render the oval.
 * @param topLeft coordinates of the top-left corner.
 * @param size dimensions of the bounding box.
 * @param style whether to fill or outline the oval.
 * @param alpha the opacity to render with.
 */
public fun DrawScope.drawOval(
    paint: Paint,
    topLeft: Point2D,
    size: Dimension2D,
    style: DrawStyle = DrawStyle.Fill,
    @FloatRange(from = 0.0, to = 1.0) alpha: Float = 1.0f,
): Unit =
    drawOval(paint, topLeft.x.toFloat(), topLeft.y.toFloat(), size.width.toFloat(), size.height.toFloat(), style, alpha)

/**
 * Draws an arc positioned within the bounding box defined by [topLeft] and [size].
 *
 * @param paint the paint or color used to render the arc.
 * @param startAngle starting angle in degrees, `0` at 3 o'clock and increasing clockwise.
 * @param sweepAngle angular extent of the arc in degrees. Positive sweeps clockwise, negative counter-clockwise.
 * @param useCenter if true, closes the arc through the center forming a pie wedge.
 * @param topLeft coordinates of the top-left corner.
 * @param size dimensions of the bounding box.
 * @param style whether to fill or outline the arc.
 * @param alpha the opacity to render with.
 */
@Suppress("LongParameterList") // DrawScope.drawArc's parameters, with its box as a point and a size.
public fun DrawScope.drawArc(
    paint: Paint,
    startAngle: Float,
    sweepAngle: Float,
    useCenter: Boolean,
    topLeft: Point2D,
    size: Dimension2D,
    style: DrawStyle = DrawStyle.Fill,
    @FloatRange(from = 0.0, to = 1.0) alpha: Float = 1.0f,
): Unit =
    drawArc(
        paint = paint,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = useCenter,
        x = topLeft.x.toFloat(),
        y = topLeft.y.toFloat(),
        width = size.width.toFloat(),
        height = size.height.toFloat(),
        style = style,
        alpha = alpha,
    )

/**
 * Draws an [image] positioned at [topLeft], optionally scaled to [size].
 *
 * @param image the image to render.
 * @param topLeft coordinates of the top-left corner.
 * @param size optional dimensions to scale the image into.
 * @param alpha the opacity to render with.
 */
public fun DrawScope.drawImage(
    image: Image,
    topLeft: Point2D,
    size: Dimension2D? = null,
    @FloatRange(from = 0.0, to = 1.0) alpha: Float = 1.0f,
) {
    val x = topLeft.x.toFloat()
    val y = topLeft.y.toFloat()
    if (size == null) {
        drawImage(image, x, y, alpha)
    } else {
        drawImage(image, x, y, size.width.toFloat(), size.height.toFloat(), alpha)
    }
}

/**
 * Intersects the current clip with a rectangle defined by [topLeft] and [size].
 *
 * @param topLeft coordinates of the top-left corner.
 * @param size dimensions of the clip rectangle.
 * @param block the drawing commands executed with the clipped region.
 */
public inline fun DrawScope.clipRect(
    topLeft: Point2D,
    size: Dimension2D,
    block: DrawScope.() -> Unit,
): Unit = clipRect(topLeft.x.toFloat(), topLeft.y.toFloat(), size.width.toFloat(), size.height.toFloat(), block)
