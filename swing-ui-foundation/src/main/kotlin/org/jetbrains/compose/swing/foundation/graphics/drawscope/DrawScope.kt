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
 * this module's META-INF/NOTICE for the synced version. The startAngle and alpha parameter
 * docs, and the default radius/center/size arguments, are upstream's.
 */

@file:JvmMultifileClass
@file:JvmName("GraphicsDrawScopeKt")

package org.jetbrains.compose.swing.foundation.graphics.drawscope

import androidx.annotation.FloatRange
import org.jetbrains.compose.swing.foundation.graphics.ImageLayer
import org.jetbrains.compose.swing.foundation.graphics.withAlpha
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.Image
import java.awt.Paint
import java.awt.Shape
import java.awt.Stroke
import java.awt.font.TextLayout
import java.awt.geom.AffineTransform
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.ImageObserver

/**
 * The receiver scope for drawing operations inside [org.jetbrains.compose.swing.foundation.Canvas],
 * aligned with androidx's `DrawScope`.
 *
 * Provides high-level, declarative drawing primitives operating on floating-point coordinates while
 * exposing the underlying Swing [Graphics2D] via [graphics] for direct Java2D integration.
 */
@DrawScopeMarker
// Each primitive takes its geometry and style as separate scalars, as androidx's takes value classes, so a caller
// allocates no geometry object to draw.
@Suppress("LongParameterList")
public sealed interface DrawScope {
    /** The size of the canvas drawing surface. Each read answers with a fresh [Dimension]. */
    public val size: Dimension

    /** Direct access to the underlying Swing [Graphics2D] surface. */
    public val graphics: Graphics2D

    /** Convenience accessor for the integer width of the drawing surface. */
    public val width: Int get() = size.width

    /** Convenience accessor for the integer height of the drawing surface. */
    public val height: Int get() = size.height

    /** The center of the drawing surface, at half its width and half its height. */
    public val center: Point2D get() = Point2D.Float(size.width / 2f, size.height / 2f)

    /**
     * Draws a straight line between ([x1], [y1]) and ([x2], [y2]).
     *
     * @param paint the paint or color used to render the line.
     * @param x1 horizontal start coordinate.
     * @param y1 vertical start coordinate.
     * @param x2 horizontal end coordinate.
     * @param y2 vertical end coordinate.
     * @param stroke the [Stroke] used to outline the line.
     * @param alpha the opacity to render with, from `0.0f` (transparent) to `1.0f` (opaque).
     */
    public fun drawLine(
        paint: Paint,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        stroke: Stroke = DrawStyle.DefaultStroke.stroke,
        @FloatRange(from = 0.0, to = 1.0) alpha: Float = 1.0f,
    )

    /**
     * Draws a rectangle with the specified bounds. Defaults to covering the entire surface.
     *
     * @param paint the paint or color used to render the rectangle.
     * @param x horizontal coordinate of the top-left corner.
     * @param y vertical coordinate of the top-left corner.
     * @param width width of the rectangle.
     * @param height height of the rectangle.
     * @param style whether to fill or outline the rectangle.
     * @param alpha the opacity to render with.
     */
    public fun drawRect(
        paint: Paint,
        x: Float = 0f,
        y: Float = 0f,
        width: Float = size.width.toFloat() - x,
        height: Float = size.height.toFloat() - y,
        style: DrawStyle = DrawStyle.Fill,
        @FloatRange(from = 0.0, to = 1.0) alpha: Float = 1.0f,
    )

    /**
     * Draws a rounded rectangle whose corners are elliptical arcs with radii [cornerRadiusX] and [cornerRadiusY].
     *
     * @param paint the paint or color used to render the rounded rectangle.
     * @param x horizontal coordinate of the top-left corner.
     * @param y vertical coordinate of the top-left corner.
     * @param width width of the rectangle.
     * @param height height of the rectangle.
     * @param cornerRadiusX horizontal radius of the corner arcs.
     * @param cornerRadiusY vertical radius of the corner arcs.
     * @param style whether to fill or outline the shape.
     * @param alpha the opacity to render with.
     */
    public fun drawRoundRect(
        paint: Paint,
        x: Float = 0f,
        y: Float = 0f,
        width: Float = size.width.toFloat() - x,
        height: Float = size.height.toFloat() - y,
        cornerRadiusX: Float = 0f,
        cornerRadiusY: Float = cornerRadiusX,
        style: DrawStyle = DrawStyle.Fill,
        @FloatRange(from = 0.0, to = 1.0) alpha: Float = 1.0f,
    )

    /**
     * Draws a circle centered at ([centerX], [centerY]) with the given [radius].
     *
     * @param paint the paint or color used to render the circle.
     * @param radius radius of the circle, defaulting to half the smaller dimension of the surface.
     * @param centerX horizontal center coordinate, defaulting to the surface's center.
     * @param centerY vertical center coordinate, defaulting to the surface's center.
     * @param style whether to fill or outline the circle.
     * @param alpha the opacity to render with.
     */
    public fun drawCircle(
        paint: Paint,
        radius: Float = minOf(size.width, size.height) / 2.0f,
        centerX: Float = size.width / 2.0f,
        centerY: Float = size.height / 2.0f,
        style: DrawStyle = DrawStyle.Fill,
        @FloatRange(from = 0.0, to = 1.0) alpha: Float = 1.0f,
    )

    /**
     * Draws an oval fitting within the bounding box defined by [x], [y], [width], and [height].
     *
     * @param paint the paint or color used to render the oval.
     * @param x horizontal coordinate of the bounding box.
     * @param y vertical coordinate of the bounding box.
     * @param width width of the bounding box.
     * @param height height of the bounding box.
     * @param style whether to fill or outline the oval.
     * @param alpha the opacity to render with.
     */
    public fun drawOval(
        paint: Paint,
        x: Float = 0f,
        y: Float = 0f,
        width: Float = size.width.toFloat() - x,
        height: Float = size.height.toFloat() - y,
        style: DrawStyle = DrawStyle.Fill,
        @FloatRange(from = 0.0, to = 1.0) alpha: Float = 1.0f,
    )

    /**
     * Draws an arc from [startAngle] sweeping [sweepAngle] degrees.
     *
     * @param paint the paint or color used to render the arc.
     * @param startAngle starting angle in degrees, `0` at 3 o'clock and increasing clockwise.
     * @param sweepAngle angular extent of the arc in degrees. Positive sweeps clockwise, negative counter-clockwise.
     * @param useCenter if true, closes the arc through the center forming a pie wedge; if false, leaves the arc open.
     * @param x horizontal coordinate of the bounding box.
     * @param y vertical coordinate of the bounding box.
     * @param width width of the bounding box.
     * @param height height of the bounding box.
     * @param style whether to fill or outline the arc.
     * @param alpha the opacity to render with.
     */
    public fun drawArc(
        paint: Paint,
        startAngle: Float,
        sweepAngle: Float,
        useCenter: Boolean,
        x: Float = 0f,
        y: Float = 0f,
        width: Float = size.width.toFloat() - x,
        height: Float = size.height.toFloat() - y,
        style: DrawStyle = DrawStyle.Fill,
        @FloatRange(from = 0.0, to = 1.0) alpha: Float = 1.0f,
    )

    /**
     * Draws an arbitrary geometric [path] or [Shape] (e.g. `Path2D`, `Polygon`, `GeneralPath`).
     *
     * @param path the shape to draw.
     * @param paint the paint or color used to render the shape.
     * @param style whether to fill or outline the shape.
     * @param alpha the opacity to render with.
     */
    public fun drawPath(
        path: Shape,
        paint: Paint,
        style: DrawStyle = DrawStyle.Fill,
        @FloatRange(from = 0.0, to = 1.0) alpha: Float = 1.0f,
    )

    /**
     * Draws an [image] at its own size with its top-left corner at ([x], [y]). An image still loading draws
     * once it has loaded, as `Graphics.drawImage` does with its component as the observer.
     *
     * @param image the image to draw.
     * @param x horizontal coordinate of the top-left corner.
     * @param y vertical coordinate of the top-left corner.
     * @param alpha the opacity to render with.
     */
    public fun drawImage(
        image: Image,
        x: Float = 0f,
        y: Float = 0f,
        @FloatRange(from = 0.0, to = 1.0) alpha: Float = 1.0f,
    )

    /**
     * Draws an [image] scaled into the specified rectangle. An image still loading draws once it has loaded,
     * as `Graphics.drawImage` does with its component as the observer.
     *
     * @param image the image to draw.
     * @param x horizontal coordinate of the top-left corner.
     * @param y vertical coordinate of the top-left corner.
     * @param width destination width.
     * @param height destination height.
     * @param alpha the opacity to render with.
     */
    public fun drawImage(
        image: Image,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        @FloatRange(from = 0.0, to = 1.0) alpha: Float = 1.0f,
    )

    /**
     * Draws a pre-measured Java2D [textLayout] with its top-left corner positioned at ([x], [y]).
     *
     * @param textLayout the pre-computed text layout to render.
     * @param x horizontal offset of the top-left corner.
     * @param y vertical offset of the top-left corner.
     * @param paint the [Paint] to draw with, or null to keep the graphics context's current paint.
     * @param alpha the opacity to render with.
     */
    public fun drawText(
        textLayout: TextLayout,
        x: Float = 0f,
        y: Float = 0f,
        paint: Paint? = null,
        @FloatRange(from = 0.0, to = 1.0) alpha: Float = 1.0f,
    )
}

/**
 * Draws a straight line between ([x1], [y1]) and ([x2], [y2]) using a line of width [strokeWidth].
 *
 * @param paint the paint or color used to render the line.
 * @param x1 horizontal start coordinate.
 * @param y1 vertical start coordinate.
 * @param x2 horizontal end coordinate.
 * @param y2 vertical end coordinate.
 * @param strokeWidth the width of the stroked line.
 * @param alpha the opacity to render with, from `0.0f` to `1.0f`.
 */
@Suppress("LongParameterList") // DrawScope.drawLine's parameters, with a stroke width in place of its stroke.
public fun DrawScope.drawLine(
    paint: Paint,
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float,
    strokeWidth: Float,
    @FloatRange(from = 0.0, to = 1.0) alpha: Float = 1.0f,
): Unit = drawLine(paint, x1, y1, x2, y2, DrawStyle.Stroke(width = strokeWidth).stroke, alpha)

/**
 * Records [block] into [layer], the counterpart of androidx's `GraphicsLayer.record` inside a `DrawScope`: [block]
 * draws in this scope's coordinates and size, onto a recording aligned to the device pixels this scope draws on, so a
 * `drawContent()` inside it records the content where it would have painted. Draw it back with [ImageLayer.draw] on
 * [DrawScope.graphics]. Where the clip leaves nothing to draw, [block] does not run and [layer] is left empty.
 *
 * @param layer the layer to record into, replacing what it held.
 * @param block draws the recording.
 * @see ImageLayer.record
 */
public fun DrawScope.record(
    layer: ImageLayer,
    block: DrawScope.() -> Unit,
) {
    layer.record(graphics, size.width, size.height) { recording -> drawingOn(recording) { block() } }
}

/** The [DrawScope] driving a [Graphics2D] surface. */
internal open class CanvasDrawScope : DrawScope {
    private var widthInternal: Int = 0
    private var heightInternal: Int = 0

    override var size: Dimension
        get() = Dimension(widthInternal, heightInternal)
        set(value) {
            widthInternal = value.width
            heightInternal = value.height
        }

    /** The graphics this scope draws into; `null` while it is not drawing. */
    var drawingGraphics: Graphics2D? = null

    /** What [drawImage] hands an image still loading: the component drawn, which repaints once it has loaded. */
    var imageObserver: ImageObserver? = null

    override val graphics: Graphics2D
        get() = checkNotNull(drawingGraphics) { "This DrawScope is used outside the draw it was handed to." }

    override fun drawLine(
        paint: Paint,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        stroke: Stroke,
        alpha: Float,
    ) {
        graphics.drawWithPaintAndAlpha(paint, alpha) { graphics.drawStroked(Line2D.Float(x1, y1, x2, y2), stroke) }
    }

    override fun drawRect(
        paint: Paint,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        style: DrawStyle,
        alpha: Float,
    ) {
        drawShape(Rectangle2D.Float(x, y, width, height), paint, style, alpha)
    }

    override fun drawRoundRect(
        paint: Paint,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        cornerRadiusX: Float,
        cornerRadiusY: Float,
        style: DrawStyle,
        alpha: Float,
    ) {
        drawShape(
            RoundRectangle2D.Float(x, y, width, height, cornerRadiusX * 2, cornerRadiusY * 2),
            paint,
            style,
            alpha,
        )
    }

    override fun drawCircle(
        paint: Paint,
        radius: Float,
        centerX: Float,
        centerY: Float,
        style: DrawStyle,
        alpha: Float,
    ) {
        val diameter = radius * 2.0f
        drawShape(Ellipse2D.Float(centerX - radius, centerY - radius, diameter, diameter), paint, style, alpha)
    }

    override fun drawOval(
        paint: Paint,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        style: DrawStyle,
        alpha: Float,
    ) {
        drawShape(Ellipse2D.Float(x, y, width, height), paint, style, alpha)
    }

    override fun drawArc(
        paint: Paint,
        startAngle: Float,
        sweepAngle: Float,
        useCenter: Boolean,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        style: DrawStyle,
        alpha: Float,
    ) {
        val arcType = if (useCenter) Arc2D.PIE else Arc2D.OPEN
        // Arc2D's angles turn counter-clockwise on screen, and these turn clockwise.
        drawShape(Arc2D.Float(x, y, width, height, -startAngle, -sweepAngle, arcType), paint, style, alpha)
    }

    override fun drawPath(
        path: Shape,
        paint: Paint,
        style: DrawStyle,
        alpha: Float,
    ) {
        drawShape(path, paint, style, alpha)
    }

    override fun drawImage(
        image: Image,
        x: Float,
        y: Float,
        alpha: Float,
    ) {
        graphics.drawWithPaintAndAlpha(paint = null, alpha = alpha) {
            graphics.drawImage(image, AffineTransform.getTranslateInstance(x.toDouble(), y.toDouble()), imageObserver)
        }
    }

    override fun drawImage(
        image: Image,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        alpha: Float,
    ) {
        // Read with the observer: an image whose size is not known yet repaints the component once it has loaded.
        val imageWidth = image.getWidth(imageObserver)
        val imageHeight = image.getHeight(imageObserver)
        if (imageWidth <= 0 || imageHeight <= 0) return
        graphics.drawWithPaintAndAlpha(paint = null, alpha = alpha) {
            val transform = AffineTransform.getTranslateInstance(x.toDouble(), y.toDouble())
            transform.scale(width.toDouble() / imageWidth, height.toDouble() / imageHeight)
            graphics.drawImage(image, transform, imageObserver)
        }
    }

    override fun drawText(
        textLayout: TextLayout,
        x: Float,
        y: Float,
        paint: Paint?,
        alpha: Float,
    ) {
        graphics.drawWithPaintAndAlpha(paint, alpha) {
            // In Java2D, layout.draw(graphics, x, y) positions y at the baseline.
            // Offset by ascent to align top-left at (x, y), as androidx's `drawText` does.
            textLayout.draw(graphics, x, y + textLayout.ascent)
        }
    }

    private fun drawShape(
        shape: Shape,
        paint: Paint,
        style: DrawStyle,
        alpha: Float,
    ) {
        graphics.drawWithPaintAndAlpha(paint, alpha) {
            when (style) {
                is DrawStyle.Fill -> graphics.fill(shape)
                is DrawStyle.Stroke -> graphics.drawStroked(shape, style.stroke)
            }
        }
    }
}

/** Runs [draw] with [paint] and [alpha] applied, then puts back the paint and composite this graphics had. */
private inline fun Graphics2D.drawWithPaintAndAlpha(
    paint: Paint?,
    alpha: Float,
    draw: () -> Unit,
) {
    val previousPaint = this.paint
    val previousComposite = composite
    try {
        if (paint != null) {
            this.paint = paint
        }
        if (alpha != 1.0f) {
            composite = previousComposite.withAlpha(alpha)
        }
        draw()
    } finally {
        if (paint != null) {
            this.paint = previousPaint
        }
        if (alpha != 1.0f) {
            composite = previousComposite
        }
    }
}

/** Draws [shape] outlined with [stroke], then puts back the stroke this graphics had. */
private fun Graphics2D.drawStroked(
    shape: Shape,
    stroke: Stroke,
) {
    val previousStroke = this.stroke
    try {
        this.stroke = stroke
        draw(shape)
    } finally {
        this.stroke = previousStroke
    }
}

/** Sets [DrawScope.size], which the public inline `inset` shrinks for its block and puts back afterward. */
@PublishedApi
internal fun DrawScope.resize(size: Dimension) {
    // Every DrawScope is a CanvasDrawScope: the interface is sealed.
    (this as CanvasDrawScope).size = size
}

/** Points this scope at [graphics] for the duration of [block]. */
internal inline fun DrawScope.drawingOn(
    graphics: Graphics2D,
    block: () -> Unit,
) {
    // Every DrawScope is a CanvasDrawScope: the interface is sealed.
    val scope = this as CanvasDrawScope
    val previous = scope.drawingGraphics
    scope.drawingGraphics = graphics
    try {
        block()
    } finally {
        scope.drawingGraphics = previous
    }
}
