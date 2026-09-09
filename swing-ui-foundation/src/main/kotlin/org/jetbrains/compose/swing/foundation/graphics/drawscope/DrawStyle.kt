package org.jetbrains.compose.swing.foundation.graphics.drawscope

import java.awt.BasicStroke
import java.awt.Stroke as AwtStroke

/** androidx's stroke default: the join bevels once the miter would extend this many times the stroke width. */
private const val DEFAULT_MITER_LIMIT = 4.0f

/**
 * Defines how geometric shapes are rendered: whether filled or stroked.
 */
public sealed interface DrawStyle {
    /** Fills the interior of the shape. */
    public data object Fill : DrawStyle

    /**
     * Outlines the boundary of the shape using a [stroke].
     *
     * @property stroke the [AwtStroke] used to outline the shape.
     */
    public class Stroke(
        public val stroke: AwtStroke,
    ) : DrawStyle {
        /**
         * Creates a [Stroke] style with the specified parameters, wrapping a [BasicStroke].
         *
         * @param width the stroke line width, where `0` is a hairline one device pixel wide.
         * @param cap the decoration of the ends of unclosed subpaths, e.g. [BasicStroke.CAP_BUTT].
         * @param join the decoration applied where path segments meet, e.g. [BasicStroke.JOIN_MITER].
         * @param miterLimit the limit to trim the miter join.
         * @param dash the array representing the dashing pattern, or null for solid lines.
         * @param dashPhase the offset to start the dashing pattern.
         */
        public constructor(
            width: Float = 0f,
            cap: Int = BasicStroke.CAP_BUTT,
            join: Int = BasicStroke.JOIN_MITER,
            miterLimit: Float = DEFAULT_MITER_LIMIT,
            dash: FloatArray? = null,
            dashPhase: Float = 0.0f,
        ) : this(BasicStroke(width, cap, join, miterLimit, dash, dashPhase))

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Stroke) return false
            return stroke == other.stroke
        }

        override fun hashCode(): Int = stroke.hashCode()

        override fun toString(): String = "Stroke(stroke=$stroke)"
    }

    /** Predefined stroke style instances. */
    public companion object {
        /** The default stroke style: a hairline solid line with a butt cap and a miter limit of `4`. */
        public val DefaultStroke: Stroke = Stroke()
    }
}
