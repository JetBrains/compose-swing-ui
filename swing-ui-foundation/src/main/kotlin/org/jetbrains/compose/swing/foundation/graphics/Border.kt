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
 * Adapted from androidx.compose.foundation.Border in AndroidX's foundation; see this module's
 * META-INF/NOTICE for the synced version. The line drawn as a doubled, centered stroke cut to the
 * outline, and the outline filled outright where lines from opposite edges meet, reimplement
 * upstream's drawing; BorderStroke follows upstream's class of that name. The code and KDoc are
 * this project's own.
 */

@file:JvmMultifileClass
@file:JvmName("GraphicsKt")

package org.jetbrains.compose.swing.foundation.graphics

import androidx.compose.runtime.Immutable
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Area
import java.awt.Shape as AwtShape

/**
 * Draws a line [width] wide along the inside of [shape]'s outline, over the content.
 *
 * The line is the part of the outline that lies within [width] of its edge, as AndroidX's `border` draws it: it
 * stays inside the outline, and a line wider than half the area's shorter side fills the whole outline. An outline
 * other than [RectangleShape] is drawn with an antialiased edge. A clip declared before the border only cuts the
 * line; declare the clip's shape here for the line to follow it.
 *
 * It is painted after the content so a look and feel's own fill cannot wipe it.
 *
 * This is a decoration step, not Swing's border property: it paints where it is declared among the decorations,
 * and a look and feel never replaces it.
 * [lineBorder][org.jetbrains.compose.swing.modifier.appearance.lineBorder] writes the Swing border property instead,
 * which reserves its thickness as insets and is what a widget's own painting knows about.
 *
 * @param width how wide the line is, in the component's own units; `0` or less draws nothing.
 * @param brush the line's fill, resolved against the [decorated box][decoration].
 * @param shape the outline the line runs along; the whole decorated box by default.
 * @return this chain with the border declared on it.
 */
public fun SwingModifier.border(
    width: Int,
    brush: Brush,
    shape: Shape = RectangleShape,
): SwingModifier = decoration(BorderElement(width, brush, shape))

/**
 * Draws a line [width] wide in [color] along the inside of [shape]'s outline; the solid-color overload of
 * [border][SwingModifier.border].
 *
 * @param width how wide the line is, in the component's own units; `0` or less draws nothing.
 * @param color the line's color.
 * @param shape the outline the line runs along; the whole decorated box by default.
 * @return this chain with the border declared on it.
 */
public fun SwingModifier.border(
    width: Int,
    color: Color,
    shape: Shape = RectangleShape,
): SwingModifier = decoration(BorderElement(width, SolidColor(color), shape))

/**
 * Draws [border] along the inside of [shape]'s outline; the [BorderStroke] overload of
 * [border][SwingModifier.border].
 *
 * @param border the line's width and fill.
 * @param shape the outline the line runs along; the whole decorated box by default.
 * @return this chain with the border declared on it.
 */
public fun SwingModifier.border(
    border: BorderStroke,
    shape: Shape = RectangleShape,
): SwingModifier = decoration(BorderElement(border.width, border.brush, shape))

/**
 * A line [width] wide filled with [brush]: what [border] draws.
 *
 * Two strokes compare equal when their widths and brushes do, so a chain rebuilt from unchanged values
 * declares the same border and nothing repaints.
 *
 * @property width how wide the line is, in the component's own units; `0` or less draws nothing.
 * @property brush the line's fill, resolved against the [decorated box][decoration].
 */
@Immutable
public class BorderStroke(
    public val width: Int,
    public val brush: Brush,
) {
    /**
     * A line [width] wide in [color].
     *
     * @param width how wide the line is, in the component's own units; `0` or less draws nothing.
     * @param color the line's color.
     */
    public constructor(width: Int, color: Color) : this(width, SolidColor(color))

    override fun equals(other: Any?): Boolean =
        this === other || (other is BorderStroke && width == other.width && brush == other.brush)

    override fun hashCode(): Int = 31 * width + brush.hashCode()

    override fun toString(): String = "BorderStroke(width=$width, brush=$brush)"
}

/** The additive element behind [SwingModifier.border]. */
private data class BorderElement(
    private val width: Int,
    private val brush: Brush,
    private val shape: Shape,
) : SwingModifier.NodeElement<Component, BorderNode>() {
    override val name: String get() = "border"

    override val additive: Boolean get() = true

    override val targetType: Class<Component> get() = Component::class.java

    override val declaredValues: Map<String, Any?>
        get() = mapOf("width" to width, "brush" to brush, "shape" to shape)

    override fun create(): BorderNode = BorderNode(width, brush, shape)

    override fun update(node: BorderNode) {
        val changed = width != node.width || brush != node.brush || shape != node.shape
        node.width = width
        node.brush = brush
        node.shape = shape
        if (changed) node.dropBand()
        node.component.repaint()
    }
}

/**
 * Paints the content first and draws the brush along the inside of the shape's outline, reusing the outline it
 * last built for as long as the size it paints at, and [width], [brush] and [shape], stay the same.
 *
 * @see border
 */
private class BorderNode(
    width: Int,
    brush: Brush,
    shape: Shape,
) : DecorationModifierNode<Component>() {
    var width: Int = width
    var brush: Brush = brush
    var shape: Shape = shape

    /** What [band] last built, and the size it was built for; null once [dropBand] runs. */
    private var band: AwtShape? = null
    private var bandWidth = -1
    private var bandHeight = -1

    /** Drops the cached band, asking [shape] again at the next paint. */
    fun dropBand() {
        band = null
    }

    override fun paint(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    ) {
        content(graphics, width, height)
        val line = this.width
        if (line <= 0 || width <= 0 || height <= 0) return
        graphics.paint = brush.paint(width, height)
        // Lines from opposite edges meet at half the shorter side, where the outline is filled once.
        val filled = line >= (minOf(width, height) + 1) / 2
        if (shape == RectangleShape) {
            if (filled) {
                graphics.fillRect(0, 0, width, height)
            } else {
                graphics.fillRect(0, 0, width, line)
                graphics.fillRect(0, height - line, width, line)
                graphics.fillRect(0, line, line, height - line * 2)
                graphics.fillRect(width - line, line, line, height - line * 2)
            }
        } else {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.fill(band(width, height, line, filled))
        }
    }

    /**
     * The part of [shape]'s outline at [width] by [height] within [line] of its edge, or the outline itself where
     * [filled]; rebuilt only when asked at a size other than the one it was last built for, or after [dropBand].
     */
    private fun band(
        width: Int,
        height: Int,
        line: Int,
        filled: Boolean,
    ): AwtShape {
        var built = band
        if (built == null || bandWidth != width || bandHeight != height) {
            val outline = shape.outline(width, height)
            built =
                if (filled) {
                    outline
                } else {
                    // A stroke is centered on its path, so one twice the line's width covers the line inside the
                    // outline, and the intersection drops the half outside it.
                    Area(BasicStroke(line * 2f).createStrokedShape(outline)).apply { intersect(Area(outline)) }
                }
            band = built
            bandWidth = width
            bandHeight = height
        }
        return built
    }
}

/**
 * The fill of the color overloads of [border], [background] and [BorderStroke]. [Brush.of] compares by identity,
 * which would repaint a decoration declared from a color literal on every recomposition.
 */
internal data class SolidColor(
    private val color: Color,
) : Brush {
    override fun paint(
        width: Int,
        height: Int,
    ) = color
}
