@file:JvmMultifileClass
@file:JvmName("GraphicsKt")

package org.jetbrains.compose.swing.foundation.graphics

import androidx.compose.runtime.Immutable
import java.awt.Insets
import java.awt.image.BufferedImage
import java.awt.image.BufferedImageOp

/**
 * A per-pixel effect an [ImageLayer] applies to its recording each time it draws it, set as its
 * [renderEffect][ImageLayer.renderEffect].
 *
 * The layer hands the op [createOp] makes a [BufferedImage.TYPE_INT_ARGB_PRE] copy of the recording, grown on each
 * side by [outsets] of transparent pixels, and draws the image the op returns in place of the recording: one pixel
 * of it per pixel of the recording, with its top-left corner [outsets] above and left of the recording's.
 *
 * The pixels are premultiplied, so an op that mixes neighboring pixels, such as a blur, mixes in no color from
 * transparent ones.
 *
 * An effect is immutable, and equal to another only when both make the same op at every scale: a layer draws what
 * it kept from an equal effect instead of applying this one again.
 */
@Immutable
public interface RenderEffect {
    /**
     * The op to apply to a recording of [scale] device pixels per user-space unit: its width in pixels over its
     * width in user space, or for a recording aligned to a destination, the destination's scale.
     */
    public fun createOp(scale: Double): BufferedImageOp

    /**
     * How far the output of [createOp] reaches past the recording on each side, in device pixels at [scale]; the
     * op's input is grown by as much. Defaults to none.
     *
     * Callers read the value and never modify it, so it may be shared.
     */
    public fun outsets(scale: Double): Insets = Insets(0, 0, 0, 0)
}

/**
 * This op as a [RenderEffect] that applies it to the recording at any scale, with no [outsets][RenderEffect.outsets].
 *
 * Effects made from the same op are equal.
 */
public fun BufferedImageOp.asRenderEffect(): RenderEffect = OpEffect(this)

private class OpEffect(
    private val op: BufferedImageOp,
) : RenderEffect {
    override fun createOp(scale: Double): BufferedImageOp = op

    override fun equals(other: Any?): Boolean = other is OpEffect && op == other.op

    override fun hashCode(): Int = op.hashCode()

    override fun toString(): String = "RenderEffect($op)"
}
