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
 * Adapted from androidx.compose.ui.graphics.drawscope.DrawTransform in AndroidX's ui-graphics;
 * see this module's META-INF/NOTICE for the synced version. The transforms apply to a Java2D
 * canvas instead of Skia.
 */

@file:JvmMultifileClass
@file:JvmName("GraphicsDrawScopeKt")

package org.jetbrains.compose.swing.foundation.graphics.drawscope

import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.Shape
import java.awt.geom.Rectangle2D

/**
 * Defines transformations that can be applied to the canvas coordinate system within a [withTransform] block.
 */
@DrawScopeMarker
public sealed interface DrawTransform {
    /** Translates the coordinate origin by [dx] horizontally and [dy] vertically. */
    public fun translate(
        dx: Float,
        dy: Float,
    )

    /** Rotates the coordinate system by [degrees] clockwise around pivot point ([px], [py]). */
    public fun rotate(
        degrees: Float,
        px: Float,
        py: Float,
    )

    /**
     * Scales the coordinate system by [scaleX] horizontally and [scaleY] vertically around
     * pivot point ([px], [py]).
     */
    public fun scale(
        scaleX: Float,
        scaleY: Float,
        px: Float,
        py: Float,
    )

    /** Intersects the current clip with a rectangle defined by ([x], [y], [width], [height]). */
    public fun clipRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
    )

    /** Intersects the current clip with the given [shape]. */
    public fun clipPath(shape: Shape)
}

/**
 * Concrete implementation of [DrawTransform] delegating to [Graphics2D].
 */
@PublishedApi
internal class DrawTransformImpl(
    private val graphics: Graphics2D,
) : DrawTransform {
    override fun translate(
        dx: Float,
        dy: Float,
    ) {
        graphics.translate(dx.toDouble(), dy.toDouble())
    }

    override fun rotate(
        degrees: Float,
        px: Float,
        py: Float,
    ) {
        graphics.rotate(Math.toRadians(degrees.toDouble()), px.toDouble(), py.toDouble())
    }

    override fun scale(
        scaleX: Float,
        scaleY: Float,
        px: Float,
        py: Float,
    ) {
        graphics.translate(px.toDouble(), py.toDouble())
        graphics.scale(scaleX.toDouble(), scaleY.toDouble())
        graphics.translate(-px.toDouble(), -py.toDouble())
    }

    override fun clipRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
    ) {
        graphics.clip(Rectangle2D.Float(x, y, width, height))
    }

    override fun clipPath(shape: Shape) {
        graphics.clip(shape)
    }
}

/**
 * Executes [drawBlock] with transformations applied in [transformBlock], restoring the previous
 * transform and clip state afterwards.
 */
public inline fun DrawScope.withTransform(
    transformBlock: DrawTransform.() -> Unit,
    drawBlock: DrawScope.() -> Unit,
) {
    val savedTransform = graphics.transform
    val savedClip = graphics.deviceClip(savedTransform)
    try {
        DrawTransformImpl(graphics).transformBlock()
        drawBlock()
    } finally {
        graphics.restoreTransformAndClip(savedTransform, savedClip)
    }
}

/**
 * Translates the drawing origin by [dx] and [dy] for the duration of [block].
 */
public inline fun DrawScope.translate(
    dx: Float,
    dy: Float,
    block: DrawScope.() -> Unit,
) {
    withTransform({ translate(dx, dy) }, block)
}

/**
 * Rotates the drawing space by [degrees] around pivot ([pivotX], [pivotY]) for the duration of [block].
 */
public inline fun DrawScope.rotate(
    degrees: Float,
    pivotX: Float = size.width / 2f,
    pivotY: Float = size.height / 2f,
    block: DrawScope.() -> Unit,
) {
    withTransform({ rotate(degrees, pivotX, pivotY) }, block)
}

/**
 * Scales the drawing space by [scaleX] and [scaleY] around pivot ([pivotX], [pivotY]) for the duration of [block].
 */
public inline fun DrawScope.scale(
    scaleX: Float,
    scaleY: Float = scaleX,
    pivotX: Float = size.width / 2f,
    pivotY: Float = size.height / 2f,
    block: DrawScope.() -> Unit,
) {
    withTransform({ scale(scaleX, scaleY, pivotX, pivotY) }, block)
}

/**
 * Insets the drawing area by [left], [top], [right] and [bottom] for the duration of [block]: the origin moves to
 * ([left], [top]), and [DrawScope.size] shrinks by the insets, rounded down. Nothing is clipped.
 *
 * @throws IllegalArgumentException when the insets leave a negative width or height.
 */
public inline fun DrawScope.inset(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    block: DrawScope.() -> Unit,
) {
    val outerSize = size
    val width = outerSize.width - left - right
    val height = outerSize.height - top - bottom
    require(width >= 0f && height >= 0f) { "Width and height must be greater than or equal to zero" }
    graphics.translate(left.toDouble(), top.toDouble())
    resize(Dimension(width.toInt(), height.toInt()))
    try {
        block()
    } finally {
        resize(outerSize)
        graphics.translate(-left.toDouble(), -top.toDouble())
    }
}

/** Insets every side of the drawing area by [inset] for the duration of [block]; see the four-sided `inset`. */
public inline fun DrawScope.inset(
    inset: Float,
    block: DrawScope.() -> Unit,
): Unit = inset(inset, inset, inset, inset, block)

/**
 * Insets the left and right of the drawing area by [horizontal] and its top and bottom by [vertical] for the
 * duration of [block]; see the four-sided `inset`.
 */
public inline fun DrawScope.inset(
    horizontal: Float = 0f,
    vertical: Float = 0f,
    block: DrawScope.() -> Unit,
): Unit = inset(horizontal, vertical, horizontal, vertical, block)

/**
 * Intersects the clip with a rectangle defined by [x], [y], [width], [height] for the duration of [block].
 */
public inline fun DrawScope.clipRect(
    x: Float = 0f,
    y: Float = 0f,
    width: Float = size.width.toFloat() - x,
    height: Float = size.height.toFloat() - y,
    block: DrawScope.() -> Unit,
) {
    withTransform({ clipRect(x, y, width, height) }, block)
}

/**
 * Intersects the clip with [shape] for the duration of [block].
 */
public inline fun DrawScope.clipPath(
    shape: Shape,
    block: DrawScope.() -> Unit,
) {
    withTransform({ clipPath(shape) }, block)
}
