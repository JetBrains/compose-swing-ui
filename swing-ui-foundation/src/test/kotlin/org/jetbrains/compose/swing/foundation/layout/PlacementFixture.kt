// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.graphics.TransformOrigin
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import java.awt.Component
import java.awt.Point
import java.awt.Rectangle
import java.awt.geom.AffineTransform
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import javax.swing.JTextField
import javax.swing.SwingUtilities
import kotlin.math.floor
import kotlin.test.assertIs

internal const val STAGE_TAG = "stage"

/** The tags [TurnedTextField] sets on its layered box and on its field. */
internal const val LAYERED_TAG = "layered"
internal const val INNER_TAG = "inner"

@Composable
internal inline fun BoxScope.Stage(
    modifier: SwingModifier = SwingModifier,
    crossinline content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.testTag(STAGE_TAG).styleable { preferredSize(300, 300) },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The matrix a layer turning a [width] by [height] box by [rotation] and [scale] about its center paints through. */
internal fun turn(
    width: Int,
    height: Int,
    rotation: Float,
    scale: Float = 1f,
): AffineTransform =
    TransformOrigin.Center.createTransform(
        Rectangle2D.Double(0.0, 0.0, width.toDouble(), height.toDouble()),
        scale,
        scale,
        rotation,
    )

/** This decorated component's paint bounds, its bounds, from its layout origin. */
internal val Component.paintBounds: Rectangle
    get() {
        val outsets = assertIs<Decoratable>(this).decoration.paintOutsets()
        return Rectangle(-outsets.left, -outsets.top, width, height)
    }

/** Where ([x], [y]) of this decorated component's layout bounds stands unturned, in [stage]'s coordinates. */
internal fun Component.unturnedAt(
    stage: Component,
    x: Int,
    y: Int,
): Point = SwingUtilities.convertPoint(this, 0, 0, stage).apply { translate(x - paintBounds.x, y - paintBounds.y) }

/**
 * Where this layered component paints ([x], [y]) of [inner], its descendant or itself, through [turn], in
 * [stage]'s coordinates.
 */
internal fun Component.paintedAt(
    stage: Component,
    inner: Component,
    x: Double,
    y: Double,
    turn: AffineTransform,
): Point {
    val offset = SwingUtilities.convertPoint(inner, 0, 0, this)
    val painted = turn.transform(Point2D.Double(offset.x + x + paintBounds.x, offset.y + y + paintBounds.y), null)
    val origin = SwingUtilities.convertPoint(this, 0, 0, stage)
    return Point(
        floor(painted.x - paintBounds.x).toInt() + origin.x,
        floor(painted.y - paintBounds.y).toInt() + origin.y,
    )
}

@Composable
internal fun BoxScope.TurnedTextField(
    modifier: SwingModifier = SwingModifier,
    rotation: Float = 0f,
    scale: Float = 1f,
) {
    Stage(modifier) {
        Box(
            modifier =
                SwingModifier.testTag(LAYERED_TAG).size(200, 30).placementLayer {
                    rotationZ = rotation
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            TextField("abcdefghij", onValueChange = {}, modifier = SwingModifier.testTag(INNER_TAG).fillMaxSize())
        }
    }
}

/** Where this layered component paints the middle of [field]'s character [index], in [stage]'s coordinates. */
internal fun Component.charAt(
    stage: Component,
    field: JTextField,
    index: Int,
    turn: AffineTransform,
): Point {
    val start = field.modelToView2D(index)
    val end = field.modelToView2D(index + 1)
    return paintedAt(stage, field, (start.x + end.x) / 2 - (end.x - start.x) / 4, start.centerY, turn)
}
