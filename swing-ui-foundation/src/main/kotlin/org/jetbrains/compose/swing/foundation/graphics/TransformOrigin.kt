package org.jetbrains.compose.swing.foundation.graphics

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import java.awt.geom.AffineTransform
import java.awt.geom.Rectangle2D

/**
 * The point scale and rotation are applied around, as fractions of the content's own painted box.
 *
 * Both fractions are measured from the box's left and top edge whatever the reading order: an origin is a
 * point on the content, and placement has already resolved right-to-left by the time anything paints.
 *
 * A fraction outside `0f..1f` names a point outside the box, which scales the content away from a pivot
 * it does not cover.
 *
 * @property pivotFractionX where the pivot sits across the width, `0f` at the left edge and `1f` at the
 *     right one.
 * @property pivotFractionY where the pivot sits down the height, `0f` at the top edge and `1f` at the
 *     bottom one.
 */
@Immutable
public class TransformOrigin(
    public val pivotFractionX: Float,
    public val pivotFractionY: Float,
) {
    /**
     * The Java2D matrix: scale first, then rotate in degrees around this origin in [box].
     * Positive [rotationZ] turns clockwise on screen. The returned transform belongs to the caller.
     */
    public fun createTransform(
        box: Rectangle2D,
        scaleX: Float = 1f,
        scaleY: Float = 1f,
        rotationZ: Float = 0f,
    ): AffineTransform =
        AffineTransform().apply {
            setToScaleAndRotation(
                box.x + pivotFractionX.toDouble() * box.width,
                box.y + pivotFractionY.toDouble() * box.height,
                scaleX,
                scaleY,
                rotationZ,
            )
        }

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is TransformOrigin &&
                    pivotFractionX.toRawBits() == other.pivotFractionX.toRawBits() &&
                    pivotFractionY.toRawBits() == other.pivotFractionY.toRawBits()
            )

    override fun hashCode(): Int = 31 * pivotFractionX.hashCode() + pivotFractionY.hashCode()

    override fun toString(): String = "TransformOrigin(pivotFractionX=$pivotFractionX, pivotFractionY=$pivotFractionY)"

    /** The [pivotFractionX] component, for destructuring. */
    @Stable
    public operator fun component1(): Float = pivotFractionX

    /** The [pivotFractionY] component, for destructuring. */
    @Stable
    public operator fun component2(): Float = pivotFractionY

    /** A copy of this origin, overriding [pivotFractionX] and/or [pivotFractionY]. */
    public fun copy(
        pivotFractionX: Float = this.pivotFractionX,
        pivotFractionY: Float = this.pivotFractionY,
    ): TransformOrigin = TransformOrigin(pivotFractionX, pivotFractionY)

    /** The origin scale and rotation are applied around where none is named. */
    public companion object {
        /** The middle of the content. */
        public val Center: TransformOrigin = TransformOrigin(0.5f, 0.5f)
    }
}

/**
 * Sets this transform to scale, then rotate by [rotationZ] degrees clockwise on screen, around ([pivotX], [pivotY]).
 */
internal fun AffineTransform.setToScaleAndRotation(
    pivotX: Double,
    pivotY: Double,
    scaleX: Float,
    scaleY: Float,
    rotationZ: Float,
) {
    setToTranslation(pivotX, pivotY)
    rotate(Math.toRadians(rotationZ.toDouble()))
    scale(scaleX.toDouble(), scaleY.toDouble())
    translate(-pivotX, -pivotY)
}
