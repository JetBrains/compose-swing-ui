package org.jetbrains.compose.swing.foundation.graphics

import androidx.compose.runtime.Immutable
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.awt.image.BufferedImageOp
import java.awt.image.ColorModel
import java.awt.image.ConvolveOp
import java.awt.image.Kernel
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Blurs the recording of the [ImageLayer] it is set on, by [radiusX] across and [radiusY] down.
 *
 * A radius is how far each pixel is spread, as in [SwingModifier.blur], in the recording's own units: a
 * recording made at a `scale` of `2.0`, or aligned to a destination scaled by two, is blurred across twice as many
 * pixels. A radius of `0f` or less leaves that direction sharp; any radius above that spreads it, however slightly. A
 * recording aligned to a destination is blurred along the device's axes, so under a rotated destination [radiusX] and
 * [radiusY] do not follow the destination's own axes.
 *
 * [edgeTreatment] decides what the blur reads past the recording's edges: [TileMode.Clamp] repeats the edge pixels,
 * so the blur stays within the recording and keeps it opaque to its edges; [TileMode.Decal] reads transparency, so
 * the recording fades out at its edges and the blur spreads past them.
 *
 * On a screen scaled by `d`, this blur is `0.5 * (d - 1)` device pixels wider than Compose Multiplatform's, which
 * adds its rounding offset directly in device pixels rather than scaling it with the recording.
 *
 * Effects are equal when their radii and edge treatment are.
 *
 * @param radiusX how far the blur spreads each pixel across.
 * @param radiusY how far the blur spreads each pixel down. Defaults to [radiusX].
 * @param edgeTreatment what the blur reads past the recording's edges. Defaults to [TileMode.Clamp].
 * @throws IllegalArgumentException if [radiusX] or [radiusY] is not finite.
 */
@Immutable
public class BlurEffect(
    private val radiusX: Float,
    private val radiusY: Float = radiusX,
    private val edgeTreatment: TileMode = TileMode.Clamp,
) : RenderEffect {
    init {
        require(radiusX.isFinite() && radiusY.isFinite()) {
            "A blur radius must be finite, but radiusX was $radiusX and radiusY was $radiusY."
        }
    }

    /**
     * The fewest pixels a blur reduces an axis's radius to: `2`, how wide a kernel is allowed to be before the
     * recording is reduced further along that axis, or a [MAX_REDUCTION]th of the axis's own radius at a scale of
     * `1.0` where that is more. A recording made at the scale this reduces to is not reduced again.
     */
    private val smallestReducedRadiusX: Int = maxOf(2, ceilDiv(radiusX.roundToInt(), MAX_REDUCTION))

    /** See [smallestReducedRadiusX]: the same, for [radiusY]. */
    private val smallestReducedRadiusY: Int = maxOf(2, ceilDiv(radiusY.roundToInt(), MAX_REDUCTION))

    /** The op for a recording at a scale of `1.0`. */
    private val unscaledOp: BlurOp by lazy(LazyThreadSafetyMode.PUBLICATION) { newOp(1.0) }

    /** The op last made for another scale, which a layer asks for its outsets and then applies. */
    @Volatile
    private var lastOp: BlurOp? = null

    /**
     * The scale to record at for this effect to blur the recording with the least resampling: the one the axis
     * reduced less reduces a recording at a scale of `1.0` to, so that axis is blurred without resampling and the
     * other is reduced further from there. A recording whose size is on the grid of [recordingBounds] is a whole
     * number of pixels at it.
     */
    internal val recordingScale: Double
        get() = 1.0 / minOf(unscaledOp.blur.reductionX, unscaledOp.blur.reductionY)

    override fun createOp(scale: Double): BufferedImageOp = op(scale)

    override fun outsets(scale: Double): Insets = op(scale).outsets

    /**
     * What to record, at [recordingScale], for this [TileMode.Decal] effect to draw [area] as it would draw it from
     * a recording of everything around it: [area] grown by the [outsets] at a scale of `1.0`, then snapped outward to
     * a grid of [MAX_REDUCTION] units counted from the origin. A cell of that grid is a whole number of pixels at
     * [recordingScale], and of the pixels this effect reduces a recording at any whole scale to, so a recording of
     * part of an area is made of the same pixels as a recording of the whole.
     *
     * A recording made over these bounds already holds this effect's reach around [area], so drawing it through a
     * [TileMode.Clamp] blur of the same radius reads that reach without growing the recording again, as this Decal
     * effect would.
     */
    internal fun recordingBounds(area: Rectangle): Rectangle {
        val outsets = unscaledOp.outsets
        val left = Math.floorDiv(area.x - outsets.left, MAX_REDUCTION) * MAX_REDUCTION
        val top = Math.floorDiv(area.y - outsets.top, MAX_REDUCTION) * MAX_REDUCTION
        val right = ceilDiv(area.x + area.width + outsets.right, MAX_REDUCTION) * MAX_REDUCTION
        val bottom = ceilDiv(area.y + area.height + outsets.bottom, MAX_REDUCTION) * MAX_REDUCTION
        return Rectangle(left, top, right - left, bottom - top)
    }

    private fun op(scale: Double): BlurOp =
        if (scale == 1.0) unscaledOp else lastOp?.takeIf { it.scale == scale } ?: newOp(scale).also { lastOp = it }

    private fun newOp(scale: Double): BlurOp =
        BlurOp(
            scale,
            GaussianBlur(
                (radiusX * scale).toFloat(),
                (radiusY * scale).toFloat(),
                smallestReducedRadiusX,
                smallestReducedRadiusY,
                scale,
            ),
            edgeTreatment == TileMode.Decal,
        )

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is BlurEffect &&
                    radiusX.toRawBits() == other.radiusX.toRawBits() &&
                    radiusY.toRawBits() == other.radiusY.toRawBits() &&
                    edgeTreatment == other.edgeTreatment
            )

    override fun hashCode(): Int = 31 * (31 * radiusX.hashCode() + radiusY.hashCode()) + edgeTreatment.hashCode()

    override fun toString(): String = "BlurEffect(radiusX=$radiusX, radiusY=$radiusY, edgeTreatment=$edgeTreatment)"
}

/**
 * Applies [blur], made for a recording at [scale], to an image: halves it down to the blur's reduced scale along
 * each axis, blurs it there, reading past its edges transparency if [decal] or else its edge pixels, and resamples
 * it back up. An axis whose radius is zero or less is never reduced. A clamped blur stays within the image, so it
 * asks for no outsets; a decal one asks for the reach it spreads past the recording. The reduced pixels are counted
 * from the image's origin, and the outsets are whole reduced pixels, so a recording that starts on the grid of
 * [GaussianBlur.reductionX] x [GaussianBlur.reductionY] pixels is reduced onto the same grid wherever it starts.
 */
private class BlurOp(
    val scale: Double,
    val blur: GaussianBlur,
    private val decal: Boolean,
) : BufferedImageOp {
    /**
     * In pixels of the recording, along each blurred axis: the kernel's reach at the blur's reduced scale, the pixel
     * there a content edge falls inside, and the pixel an enlarging resample reads past it.
     */
    val outsets: Insets =
        if (decal) {
            val across = if (blur.reachX == 0) 0 else (blur.reachX + 2) * blur.reductionX
            val down = if (blur.reachY == 0) 0 else (blur.reachY + 2) * blur.reductionY
            Insets(down, across, down, across)
        } else {
            Insets(0, 0, 0, 0)
        }

    override fun filter(
        src: BufferedImage,
        dst: BufferedImage?,
    ): BufferedImage {
        if (blur.reachX == 0 && blur.reachY == 0) return src
        val width = ceilDiv(src.width, blur.reductionX)
        val height = ceilDiv(src.height, blur.reductionY)
        // The reach a clamped edge is repeated across and a decal fades into, and the pixel an enlarging resample
        // reads beyond it. The convolution clears the reach along the border of the image.
        val left = blur.reachX + 1
        val top = blur.reachY + 1
        // Premultiplied, so a transparent pixel does not darken the color beside it.
        val reduced = BufferedImage(width + left * 2, height + top * 2, BufferedImage.TYPE_INT_ARGB_PRE)
        // Each halving averages whole 2 x 2 blocks along the axis it halves. The image is first copied onto whole
        // blocks on both axes, repeating its edge pixels over the rest of the last ones if it is clamped.
        var shrunk = src
        // A reduction of 1 leaves the modulo 0, so an axis that is never reduced never triggers padding.
        if (src.width % blur.reductionX != 0 || src.height % blur.reductionY != 0) {
            shrunk = BufferedImage(width * blur.reductionX, height * blur.reductionY, reduced.type)
            shrunk.paint { graphics -> graphics.drawImage(src, 0, 0, null) }
            if (!decal) shrunk.clampAround(0, 0, src.width, src.height)
        }
        for (halving in maxOf(blur.halvingsX, blur.halvingsY) - 1 downTo 0) {
            val stepWidth = if (halving < blur.halvingsX) width shl halving else width
            val stepHeight = if (halving < blur.halvingsY) height shl halving else height
            val step = BufferedImage(stepWidth, stepHeight, reduced.type)
            step.paint { graphics -> graphics.drawImage(shrunk, 0, 0, step.width, step.height, null) }
            shrunk = step
        }
        reduced.paint { graphics -> graphics.drawImage(shrunk, left, top, null) }
        if (!decal) reduced.clampAround(left, top, width, height)
        val blurred = blur.filter(reduced)
        // A fresh destination is already transparent, and the caller clears a reused one before filtering.
        val result = dst ?: createCompatibleDestImage(src, null)
        result.paint { graphics ->
            graphics.scale(blur.reductionX.toDouble(), blur.reductionY.toDouble())
            graphics.drawImage(blurred, -left, -top, null)
        }
        return result
    }

    override fun getBounds2D(src: BufferedImage): Rectangle2D = src.raster.bounds

    override fun createCompatibleDestImage(
        src: BufferedImage,
        destCM: ColorModel?,
    ): BufferedImage =
        if (destCM == null) {
            BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_ARGB)
        } else {
            BufferedImage(
                destCM,
                destCM.createCompatibleWritableRaster(src.width, src.height),
                destCM.isAlphaPremultiplied,
                null,
            )
        }

    override fun getPoint2D(
        srcPt: Point2D,
        dstPt: Point2D?,
    ): Point2D = (dstPt ?: Point2D.Double()).apply { setLocation(srcPt) }

    override fun getRenderingHints(): RenderingHints? = null
}

/** Draws [block] on this image's graphics, resampling bilinear. */
private inline fun BufferedImage.paint(block: (Graphics2D) -> Unit) {
    val graphics = createGraphics()
    try {
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        block(graphics)
    } finally {
        graphics.dispose()
    }
}

/**
 * Repeats the pixels on the edges of the [width] x [height] area at [left], [top] outward across the rest of this
 * image.
 */
private fun BufferedImage.clampAround(
    left: Int,
    top: Int,
    width: Int,
    height: Int,
) {
    val row = IntArray(this.width)
    for (y in 0 until this.height) {
        raster.getDataElements(0, y.coerceIn(top, top + height - 1), this.width, 1, row)
        row.fill(row[left], 0, left)
        row.fill(row[left + width - 1], left + width, this.width)
        raster.setDataElements(0, y, this.width, 1, row)
    }
}

/**
 * A Gaussian blur of [radiusX] pixels across and [radiusY] down, each applied at its own reduced scale
 * ([reductionX], [reductionY]). The kernels depend on nothing else, so one is built per radius and filters every
 * image with it.
 *
 * [radiusX] and [radiusY] are [scale] times a [BlurEffect]'s own radii, so [sigma] grows linearly with [scale]: the
 * outsets a [BlurOp] reserves at a scale of `1.0` cover this blur at every scale.
 */
private class GaussianBlur(
    radiusX: Float,
    radiusY: Float,
    smallestReducedRadiusX: Int,
    smallestReducedRadiusY: Int,
    private val scale: Double,
) {
    /**
     * How many times the image is halved across before it is blurred: as often as that keeps [radiusX] at least
     * `smallestReducedRadiusX`, and for a reduction by no more than [MAX_REDUCTION], past which the kernel grows
     * with the radius instead. `0` for a [radiusX] of `0` or less, which is never reduced.
     */
    val halvingsX: Int

    /** How many times the image is halved down before it is blurred; see [halvingsX], for [radiusY]. */
    val halvingsY: Int

    init {
        var x = 0
        while (2 shl x <= MAX_REDUCTION && radiusX >= smallestReducedRadiusX shl (x + 1)) x++
        halvingsX = x
        var y = 0
        while (2 shl y <= MAX_REDUCTION && radiusY >= smallestReducedRadiusY shl (y + 1)) y++
        halvingsY = y
    }

    /** How many pixels across make one pixel at the reduced scale. */
    val reductionX: Int = 1 shl halvingsX

    /** How many pixels down make one pixel at the reduced scale. */
    val reductionY: Int = 1 shl halvingsY

    /** [radiusX]'s Gaussian standard deviation, before reduction; see [sigma]. */
    private val sigmaX: Float = sigma(radiusX)

    /** [radiusY]'s Gaussian standard deviation, before reduction; see [sigma]. */
    private val sigmaY: Float = sigma(radiusY)

    /** How many pixels at the reduced scale the kernel reaches across either side of a pixel; `0` for none. */
    val reachX: Int = reach(sigmaX, reductionX)

    /** How many pixels at the reduced scale the kernel reaches down either side of a pixel; `0` for none. */
    val reachY: Int = reach(sigmaY, reductionY)

    // A Gaussian is separable, so one pass across and one down cost what a single row of a square kernel would.
    private val across: ConvolveOp? = gaussianPass(reachX, sigmaX / reductionX) { Kernel(it.size, 1, it) }
    private val down: ConvolveOp? = gaussianPass(reachY, sigmaY / reductionY) { Kernel(1, it.size, it) }

    /**
     * [radius]'s Gaussian standard deviation, matching Skia's mapping from blur radius to sigma
     * (`SkBlurMask::ConvertRadiusToSigma`, about 1/√3); `0f` for a radius of `0` or less.
     */
    private fun sigma(radius: Float): Float {
        if (radius <= 0f) return 0f
        val roundingOffset = 0.5f
        return BLUR_SIGMA_SCALE * radius + (roundingOffset * scale).toFloat()
    }

    /**
     * [sigma] at the [reduction] scale, rounded up to the pixel spanning as many standard deviations either side as
     * Skia cuts its kernel off at; `0` for a sigma of `0`.
     */
    private fun reach(
        sigma: Float,
        reduction: Int,
    ): Int {
        if (sigma <= 0f) return 0
        val kernelSigmas = 3
        return ceil(kernelSigmas * sigma / reduction).toInt()
    }

    /** [image], at the reduced scale, blurred; an axis whose radius is zero or less is left as it is. */
    fun filter(image: BufferedImage): BufferedImage {
        val across = across?.filter(image, null) ?: return down?.filter(image, null) ?: image
        // The input is this blur's own and no longer read, so the second pass writes over it.
        return down?.filter(across, image) ?: across
    }
}

/** [dividend] over [divisor], rounded up, for a positive [divisor]. */
private fun ceilDiv(
    dividend: Int,
    divisor: Int,
): Int = -Math.floorDiv(-dividend, divisor)

/** The most a blur reduces a recording by along each axis; a power of two. */
private const val MAX_REDUCTION = 4

/** Skia's scale from a blur radius to its Gaussian standard deviation; see [GaussianBlur.sigma]. */
private const val BLUR_SIGMA_SCALE = 0.57735f

/**
 * A convolution by a Gaussian of standard deviation [sigma], cut off [reach] pixels either side, laid out by
 * [kernel]; `null` for no reach.
 */
private inline fun gaussianPass(
    reach: Int,
    sigma: Float,
    kernel: (FloatArray) -> Kernel,
): ConvolveOp? =
    if (reach == 0) null else ConvolveOp(kernel(gaussianWeights(reach, sigma)), ConvolveOp.EDGE_ZERO_FILL, null)

/**
 * A Gaussian of standard deviation [sigma], spanning [reach] either side of its center. Its weights add up to half
 * a level over one: [ConvolveOp] rounds each result down, so a flat area then keeps its level.
 */
private fun gaussianWeights(
    reach: Int,
    sigma: Float,
): FloatArray {
    val weights = FloatArray(reach * 2 + 1)
    var total = 0f
    for (index in weights.indices) {
        val distance = (index - reach).toFloat()
        weights[index] = exp(-(distance * distance) / (2 * sigma * sigma))
        total += weights[index]
    }
    val levels = 0xFF
    for (index in weights.indices) weights[index] *= (levels * 2 + 1) / (levels * 2 * total)
    return weights
}
