@file:JvmMultifileClass
@file:JvmName("GraphicsKt")

package org.jetbrains.compose.swing.foundation.graphics

import androidx.annotation.FloatRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.annotation.RememberInComposition
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.foundation.util.fastForEach
import org.jetbrains.compose.swing.foundation.util.fastForEachIndexed
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Composite
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.GraphicsConfiguration
import java.awt.Image
import java.awt.Insets
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Transparency
import java.awt.geom.AffineTransform
import java.awt.geom.Point2D
import java.awt.image.BufferedImage
import java.awt.image.BufferedImageOp
import java.awt.image.ConvolveOp
import java.awt.image.VolatileImage
import java.util.Collections
import java.util.IdentityHashMap
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Creates an [ImageLayer] that is remembered across compositions and released when the composition
 * leaves.
 *
 * @see ImageLayer.release
 */
@Composable
public fun rememberImageLayer(): ImageLayer {
    val layer = remember { ImageLayer() }
    DisposableEffect(Unit) {
        onDispose { layer.release() }
    }
    return layer
}

/**
 * An offscreen raster that records Java2D drawing once and draws it again as often as you like,
 * through a transform the caller can change without re-recording.
 *
 * The graphics device may hold a recording in video memory, and may discard it, such as when the display
 * changes. A recording lost that way is dropped when it is next drawn, filtered, read back or asked for with
 * [hasContent]: [draw] draws nothing, [filter] does nothing, [toBufferedImage] fails and [hasContent] answers
 * `false`. Swing repaints what it lost along with it, so an owner that records on every paint needs nothing more,
 * and one that keeps a recording across paints checks [hasContent] when it paints and records again.
 *
 * Record a block of drawing (`record(width, height, scale, block)`), the pixels of a live component
 * (`record(component, scale)`), or drawing aligned to the pixels of a destination it is drawn back onto
 * (`record(destination, width, height, block)`), then draw the result with [draw] - typically from a draw block,
 * but any [Graphics2D] takes it. A layer holds one recording: a second [record] replaces it.
 *
 * The properties are plain fields, as those of androidx's `GraphicsLayer` are: writing one, or recording, changes
 * nothing already painted. The next [draw] uses them, so whatever paints the layer repaints for a change the way
 * it does for any other state it draws from.
 *
 * A layer is Java2D, so recording drawing, drawing it back and reading it back are as free of the event dispatch
 * thread as the images underneath. Recording a live component is not: that reads a Swing component tree, and
 * runs on the event dispatch thread like everything else that touches one. A layer is used from one thread at a
 * time: recording, filtering and releasing it reuse the surfaces and buffers of earlier recordings, so a draw or
 * read-back on another thread may see the pixels of a later recording.
 *
 * @see org.jetbrains.compose.swing.foundation.Canvas
 */
public class ImageLayer
    @RememberInComposition
    public constructor() {
        private var recording: Recording? = null

        /** Made by the first recording or filter that takes a buffer. */
        private var spares: Spares? = null

        /** [spares], made where none are yet. */
        private val takenSpares: Spares
            get() = spares ?: Spares().also { spares = it }

        private val surfaces = Surfaces()

        /**
         * Whether recordings go into system memory, which a filter or a render effect reads anyway, rather than onto a
         * surface.
         */
        private var recordsInSystemMemory = false

        /** Whether the recording is aligned to a destination, and the device pixel its origin is drawn at. */
        private var aligned = false
        private var alignedLeft = 0
        private var alignedTop = 0

        /** The device pixels per unit of the destination an aligned recording was made for. */
        private var alignedScale = 1.0

        /** What [renderEffect] last made of a recording; `null` until an effect is first drawn. */
        private var applied: AppliedEffect? = null

        /**
         * The size of the recording in user-space units, whatever `scale` it was recorded at, or its size in device
         * pixels for a recording aligned to a destination; `0x0` until something is recorded, and `0x0` again after
         * [release].
         *
         * Each read answers with a fresh [Dimension], so writing to it changes nothing here.
         */
        public val size: Dimension
            get() {
                val recording = recording ?: return Dimension(0, 0)
                return Dimension(recording.width, recording.height)
            }

        /** Whether this layer holds a recording to draw, dropping one the graphics device discarded. */
        public val hasContent: Boolean
            get() {
                val recording = recording ?: return false
                return !dropIfLost(recording)
            }

        /**
         * The opacity [draw] draws at: `0f` is invisible and `1f` opaque. Defaults to `1f`.
         *
         * The value is kept as assigned; [draw] draws a value above `1f` as `1f`, and one below `0f` or `NaN` as `0f`.
         */
        @setparam:FloatRange(from = 0.0, to = 1.0)
        public var alpha: Float = 1f

        /** The horizontal scale [draw] draws at, about [pivotOffset]. Defaults to `1f`. */
        public var scaleX: Float = 1f

        /** The vertical scale [draw] draws at, about [pivotOffset]. Defaults to `1f`. */
        public var scaleY: Float = 1f

        /** The horizontal offset [draw] draws at, added to its `x`. Defaults to `0f`. */
        public var translationX: Float = 0f

        /** The vertical offset [draw] draws at, added to its `y`. Defaults to `0f`. */
        public var translationY: Float = 0f

        /**
         * The rotation [draw] draws at, in degrees about [pivotOffset]; a positive angle turns clockwise on screen.
         * Defaults to `0f`.
         */
        public var rotationZ: Float = 0f

        /**
         * The point [scaleX], [scaleY] and [rotationZ] apply around, relative to the recording's origin and in the
         * units of its [size], or `null` for the center of [size]. Defaults to `null`.
         *
         * [draw] reads the point each time, so changing the one assigned here moves the pivot of the next draw.
         */
        public var pivotOffset: Point2D? = null

        /**
         * The effect [draw] applies to the recording, or `null` to draw it as recorded. Defaults to `null`.
         *
         * As with androidx's `GraphicsLayer.renderEffect`, the effect is applied as the recording is drawn and the
         * recording is left as it was: setting this back to `null` draws it sharp again, and [toBufferedImage]
         * returns it unaffected. The result is kept for the recording and effect it was made from, so a later
         * [draw], under any transform or [alpha], applies nothing again until this layer records or is handed an
         * effect that is not equal.
         */
        public var renderEffect: RenderEffect? = null

        /**
         * Records [block]'s drawing into a transparent buffer of [width] x [height] user-space units,
         * replacing whatever this layer held.
         *
         * The buffer is sized in device pixels - [width] and [height] multiplied by [scale], rounded up -
         * and the [Graphics2D] handed to [block] is pre-scaled and clipped to the buffer, so [block] draws in
         * user-space coordinates and [size] reports the user-space size whatever the [scale]. A [block] that throws
         * leaves this layer holding what it held before.
         *
         * @param width the width of the recording in user-space units; must be positive.
         * @param height the height of the recording in user-space units; must be positive.
         * @param scale the device pixels per user-space unit; must be positive. Defaults to `1.0`.
         * @param block draws the recording; the [Graphics2D] it receives belongs to this call and must
         *   not be retained.
         * @throws IllegalArgumentException if [width], [height] or [scale] is not positive.
         */
        public fun record(
            width: Int,
            height: Int,
            scale: Double = 1.0,
            block: (Graphics2D) -> Unit,
        ) {
            require(width > 0) { "width must be positive, but was $width." }
            require(height > 0) { "height must be positive, but was $height." }
            require(scale > 0) { "scale must be positive, but was $scale." }

            val deviceWidth = ceil(width * scale).toInt()
            val deviceHeight = ceil(height * scale).toInt()
            val next: Recording =
                if (recordsInSystemMemory) {
                    BufferRecording(
                        takenSpares.take(deviceWidth, deviceHeight, BufferedImage.TYPE_INT_ARGB),
                        width,
                        height,
                        deviceWidth,
                        deviceHeight,
                    )
                } else {
                    val surface = surfaces.take(deviceWidth, deviceHeight)
                    SurfaceRecording(
                        surface,
                        width,
                        height,
                        deviceWidth,
                        deviceHeight,
                        surfaces.pixels(surface, deviceWidth, deviceHeight),
                    )
                }
            var recorded = false
            try {
                // The whole image, so no pixel of an earlier recording lingers past the edge of this one.
                val pixels = next.pixels
                next.image.paintOver(pixels?.width ?: deviceWidth, pixels?.height ?: deviceHeight) { graphics ->
                    graphics.scale(scale, scale)
                    block(graphics)
                }
                recorded = true
            } finally {
                if (!recorded) next.recycle(surfaces, spares)
            }
            recording?.recycle(surfaces, spares)
            recording = next
            aligned = false
        }

        /**
         * Records what [block] paints within [width] x [height] into device pixels aligned to the pixels it would
         * paint on [destination], under the destination's transform and clip, replacing whatever this layer held.
         *
         * [block] receives a [Graphics2D] carrying [destination]'s transform, clip, font, colors and rendering
         * hints, so the content keeps its sub-pixel position at any transform and lands on the same pixels here as
         * it would on [destination]. The recording covers the device pixels of the area the clip leaves. Where the
         * clip or the size leaves nothing to paint, [block] does not run and this layer is left empty. A [block]
         * that throws leaves this layer holding what it held before.
         *
         * [draw] draws such a recording back onto the device pixels it was recorded for, whatever transform the
         * graphics it is drawn onto carries.
         *
         * @param destination the graphics the recording is aligned to; left as it was found.
         * @param width the width of the area [block] paints, in [destination]'s coordinates.
         * @param height the height of the area [block] paints, in [destination]'s coordinates.
         * @param block paints the content; the [Graphics2D] it receives belongs to this call and must not be
         *   retained.
         */
        public fun record(
            destination: Graphics2D,
            width: Int,
            height: Int,
            block: (Graphics2D) -> Unit,
        ) {
            // Before the surface is taken, so the recording is made for the destination's configuration.
            surfaces.recordFor(destination)
            val whole = Rectangle(0, 0, width, height)
            val area = destination.clipBounds?.intersection(whole) ?: whole
            // Graphics2D answers only a copy of its transform.
            val transform = destination.transform
            val device = transform.devicePixels(area)
            if (device.isEmpty) {
                // Left holding an earlier recording, the next draw would draw it where this one paints nothing.
                recording?.let {
                    recording = null
                    it.recycle(surfaces, spares)
                }
                aligned = false
                return
            }
            record(device.width, device.height) { graphics ->
                graphics.setRenderingHints(destination.renderingHints)
                graphics.font = destination.font
                graphics.color = destination.color
                graphics.background = destination.background
                // One to one in device pixels, with the buffer's origin at the device area's top-left pixel.
                graphics.translate(-device.x, -device.y)
                graphics.transform(transform)
                graphics.clip(area)
                block(graphics)
            }
            aligned = true
            alignedLeft = device.x
            alignedTop = device.y
            // The scale of a destination that scales evenly, turned or not; the mean scale of one that does not.
            alignedScale = sqrt(abs(transform.determinant))
        }

        /**
         * The current recording held in system memory, moving it there first if it is held on a surface, and
         * keeping later recordings there. `null` while there is none, or once a lost surface is dropped.
         */
        private fun inSystemMemory(): BufferRecording? {
            val recording = recording
            if (recording != null) recordsInSystemMemory = true
            return when (recording) {
                null -> {
                    null
                }

                is BufferRecording -> {
                    recording
                }

                is SurfaceRecording -> {
                    val buffer =
                        takenSpares.take(recording.deviceWidth, recording.deviceHeight, BufferedImage.TYPE_INT_ARGB)
                    buffer.paintOver(recording.deviceWidth, recording.deviceHeight, recording::drawPixels)
                    val lost = dropIfLost(recording)
                    if (lost) spares?.recycle(buffer) else recording.recycle(surfaces, spares)
                    BufferRecording(buffer, recording.width, recording.height, buffer.width, buffer.height)
                        .takeUnless { lost }
                        ?.also { this.recording = it }
                }
            }
        }

        /** Whether [recording] is held on a surface that lost its contents, dropping it if so. */
        private fun dropIfLost(recording: Recording): Boolean {
            val lost = recording.dropIfLost()
            if (lost) {
                this.recording = null
                aligned = false
            }
            return lost
        }

        /**
         * Records [component] and its descendants at the bounds the component already carries, replacing
         * whatever this layer held.
         *
         * Nothing is laid out: a component the composition holds carries the bounds its declaration gave
         * it, which a layout pass here would overwrite. A component built by hand has none, so size it
         * first.
         *
         * A component that was never shown is captured with its pixels.
         *
         * The component is recorded as it prints: `isPaintingForPrint` answers `true` while it paints, so a
         * component that paints differently for print records that way, such as a `JTable`, which leaves out its
         * selection and focused cell.
         *
         * @param component the component to capture; must have a positive width and height.
         * @param scale the device pixels per user-space unit; must be positive. Defaults to `1.0`.
         * @throws IllegalArgumentException if [component] is empty or [scale] is not positive.
         * @throws IllegalStateException if called off the event dispatch thread.
         * @see javax.swing.JComponent.printAll
         */
        public fun record(
            component: JComponent,
            scale: Double = 1.0,
        ) {
            check(SwingUtilities.isEventDispatchThread()) {
                "Compose-Swing must be used on the Event Dispatch Thread, but was called on " +
                    "'${Thread.currentThread().name}'. Wrap the call in SwingUtilities.invokeLater { }."
            }
            require(component.width > 0 && component.height > 0) {
                "Cannot record a component sized ${component.width}x${component.height}. Recording lays " +
                    "nothing out, so size the component first."
            }
            record(component.width, component.height, scale) { component.printAll(it) }
        }

        /**
         * Replaces the recording with the result of applying [operation] to it. Does nothing while this
         * layer has no content.
         *
         * For example, a Gaussian blur is a pair of [java.awt.image.ConvolveOp]s, one across and one down, and a
         * tint is a [java.awt.image.RescaleOp].
         *
         * The pixels are premultiplied, so an operation that mixes neighboring pixels, such as a blur, mixes in
         * no color from transparent ones.
         *
         * The operation runs over the buffer at the resolution it was recorded at, so one measured in
         * pixels - a convolution kernel - reaches further across a recording made at a smaller `scale`.
         * An operation that changes the buffer's dimensions leaves [size] as it was: the recording still
         * stands for the same user-space area, and [draw] draws it into that area.
         *
         * @param operation the operation to apply.
         */
        public fun filter(operation: BufferedImageOp) {
            val recording = inSystemMemory() ?: return
            val source = recording.image
            val spares = takenSpares
            val premultiplied = spares.premultiplied(source)
            val destination = spares.destination(operation, premultiplied)
            val filtered = operation.filter(premultiplied, destination)
            // An operation that returns an image of its own may share pixels with its source, as a view
            // does, so the source stays out of the spares unless the result is the buffer handed in.
            if (destination != null && filtered === destination) {
                if (premultiplied !== source) spares.recycle(premultiplied)
                spares.recycle(source)
            } else {
                destination?.let(spares::recycle)
                spares.forget(premultiplied)
                spares.forget(source)
            }
            this.recording =
                BufferRecording(filtered, recording.width, recording.height, filtered.width, filtered.height)
        }

        /**
         * Draws the recording onto [destination] at [x], [y], under this layer's transform and [alpha].
         * Draws nothing while this layer has no content.
         *
         * A recording made aligned to a destination is drawn in device pixels: [destination]'s transform is
         * replaced by one that puts the recording's origin on the device pixel it was recorded at, and [x], [y]
         * and this layer's transform apply from there, in device pixels. At the defaults it lands on exactly the
         * pixels it was recorded for.
         *
         * The recording occupies its [size] whatever the `scale` it was recorded at. As androidx's
         * `GraphicsLayer` does, the recording is scaled by [scaleX] and [scaleY], then turned by [rotationZ], both
         * about [pivotOffset], then moved by [translationX] and [translationY]. The drawing runs on a copy of
         * [destination]: the destination's transform, composite and rendering hints are left exactly as they
         * were found.
         *
         * A layer holds pixels rather than the drawing that made them, so a transform here - this
         * layer's own or, for a recording not aligned to a destination, one [destination] already carries -
         * resamples those pixels. Drawing a recording larger than it was recorded softens it; to enlarge it
         * crisply, record it again at the [record] `scale` you want it drawn at.
         *
         * The recording is drawn with [renderEffect] applied, before this layer's transform, so the effect moves,
         * turns and fades with it.
         *
         * [alpha] multiplies into the translucency [destination] already carries rather than replacing
         * it, so a layer drawn into a graphics a caller is fading fades with everything else there.
         *
         * @param destination the graphics to draw onto.
         * @param x the horizontal position of the recording's origin, before [translationX]. Defaults to `0`.
         * @param y the vertical position of the recording's origin, before [translationY]. Defaults to `0`.
         */
        public fun draw(
            destination: Graphics2D,
            x: Int = 0,
            y: Int = 0,
        ) {
            // Before anything is drawn: a lost recording usually comes with a new configuration, which the next
            // recording needs. An aligned recording already set its configuration when it was made, for the same
            // destination it is drawn back onto, so it does not flip a spare surface between configurations here.
            if (!aligned) surfaces.recordFor(destination)
            val recording = recording ?: return
            if (dropIfLost(recording)) return

            val graphics = destination.create() as Graphics2D
            try {
                if (aligned) {
                    // The graphics clip is held in device space, so replacing the transform keeps it.
                    graphics.transform = IDENTITY
                    graphics.translate(alignedLeft, alignedTop)
                }
                graphics.translate(x + translationX.toDouble(), y + translationY.toDouble())
                val pivotX = pivotOffset?.x ?: (recording.width / 2.0)
                val pivotY = pivotOffset?.y ?: (recording.height / 2.0)
                graphics.translate(pivotX, pivotY)
                graphics.rotate(Math.toRadians(rotationZ.toDouble()))
                graphics.scale(scaleX.toDouble(), scaleY.toDouble())
                graphics.translate(-pivotX, -pivotY)
                // The composite the destination carries is what a caller fading everything it draws put
                // there, so this layer's own alpha multiplies into it rather than replacing it, and a
                // layer drawn at full alpha draws under the caller's composite untouched.
                if (alpha != 1f) {
                    graphics.composite = graphics.composite.withAlpha(alpha)
                }
                // Java2D resamples nearest-neighbor by default, which is visible wherever the buffer's
                // pixels do not land on the destination's one to one: under a transform of this layer or
                // of the destination, and for a recording whose buffer differs from its user-space size.
                val resampled =
                    !graphics.transform.isIdentity ||
                        recording.deviceWidth != recording.width ||
                        recording.deviceHeight != recording.height
                if (resampled) {
                    graphics.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR,
                    )
                }
                val effect = renderEffect
                if (effect == null) recording.draw(graphics) else drawApplying(effect, recording, graphics)
            } finally {
                graphics.dispose()
            }
            // The surface may also be lost while it is drawn, leaving the destination with garbage.
            dropIfLost(recording)
        }

        /**
         * Draws [recording] with [effect] applied across its user-space size on [graphics], applying the effect
         * only if it has not been applied to this recording already.
         */
        private fun drawApplying(
            effect: RenderEffect,
            recording: Recording,
            graphics: Graphics2D,
        ) {
            val current =
                applied?.takeIf { it.source === recording && it.effect == effect }
                    ?: applyEffect(effect, recording, applied).also { applied = it }
            graphics.scale(
                recording.width / recording.deviceWidth.toDouble(),
                recording.height / recording.deviceHeight.toDouble(),
            )
            graphics.drawImage(current.result, -current.outsets.left, -current.outsets.top, null)
        }

        /**
         * Applies [effect] to [recording], writing over [previous]'s result where it holds the same effect at the
         * size the effect makes now.
         */
        private fun applyEffect(
            effect: RenderEffect,
            recording: Recording,
            previous: AppliedEffect?,
        ): AppliedEffect {
            val width = recording.deviceWidth
            val height = recording.deviceHeight
            recordsInSystemMemory = true
            val scale = if (aligned) alignedScale else width / recording.width.toDouble()
            val outsets = effect.outsets(scale)
            require(outsets.left >= 0 && outsets.top >= 0 && outsets.right >= 0 && outsets.bottom >= 0) {
                "A render effect's outsets must not be negative, but $effect answered $outsets at scale $scale."
            }
            val spares = takenSpares
            val grown =
                spares.take(
                    width + outsets.left + outsets.right,
                    height + outsets.top + outsets.bottom,
                    BufferedImage.TYPE_INT_ARGB_PRE,
                )
            grown.paintOver(grown.width, grown.height) { pixels ->
                pixels.translate(outsets.left, outsets.top)
                pixels.clipRect(0, 0, width, height)
                recording.drawPixels(pixels)
            }
            val op = effect.createOp(scale)
            // An equal effect's op writes over what it made of the recording before, where that has the size it
            // makes now; until it succeeds, nothing is kept.
            val bounds = op.getBounds2D(grown)
            val reused =
                previous?.takeIf { it.effect == effect }?.result?.takeIf {
                    it.width.toDouble() == bounds.width && it.height.toDouble() == bounds.height
                }
            // An op is free to leave a destination pixel alone rather than write it, so reused is cleared
            // first, or an op that composites over dst would pick up whatever the previous result left there.
            reused?.paintOver(reused.width, reused.height) {}
            applied = null
            var result: BufferedImage? = null
            try {
                result = op.filter(grown, reused)
            } finally {
                // An op may hand back its input, which then stays out of the spares.
                if (result === grown) spares.forget(grown) else spares.recycle(grown)
            }
            if (previous?.result !== result) previous?.result?.flush()
            return AppliedEffect(recording, effect, outsets, checkNotNull(result))
        }

        /**
         * Returns a copy of the recorded pixels at device resolution - the user-space [size] multiplied by
         * the `scale` it was recorded at - untransformed and with no [alpha] or [renderEffect] applied.
         *
         * The copy is the caller's: a later [record] or [release] does not touch it.
         *
         * @throws IllegalStateException if this layer has no content.
         */
        public fun toBufferedImage(): BufferedImage =
            checkNotNull(recording?.let { recording -> recording.copy().takeUnless { dropIfLost(recording) } }) {
                "This ImageLayer has nothing recorded to return. Record drawing or a component first."
            }

        /**
         * Frees the recorded buffer and the buffers kept for reuse, leaving this layer empty: [hasContent]
         * is `false`, [size] is `0x0`, [draw] draws nothing and [toBufferedImage] fails.
         *
         * [record] makes the layer usable again, and calling this on an empty layer does nothing. The transforms
         * are left as they stand.
         */
        public fun release() {
            recording?.let {
                recording = null
                it.release(spares)
            }
            recordsInSystemMemory = false
            aligned = false
            surfaces.flush()
            spares?.flush()
            applied?.result?.flush()
            applied = null
        }

        /**
         * One recording: an image whose top-left [deviceWidth] x [deviceHeight] pixels hold it, and the user-space
         * size it stands for.
         */
        private sealed class Recording(
            val width: Int,
            val height: Int,
            val deviceWidth: Int,
            val deviceHeight: Int,
        ) {
            /** The image the recording is held on. */
            abstract val image: Image

            /** The image's own size in pixels, where it is larger than [deviceWidth] x [deviceHeight]. */
            open val pixels: Dimension? get() = null

            /** A copy of the recorded pixels, which is the caller's. */
            abstract fun copy(): BufferedImage

            /** Whether the image lost its contents; a lost one is flushed as it is found. */
            abstract fun dropIfLost(): Boolean

            /** Keeps the image for reuse: on [surfaces] for a surface, in [spares] for a buffer. */
            abstract fun recycle(
                surfaces: Surfaces,
                spares: Spares?,
            )

            /** Frees the image for good. */
            abstract fun release(spares: Spares?)

            /** Draws the recording across its user-space size on [graphics], which it scales and clips as it needs. */
            fun draw(graphics: Graphics2D) {
                if (pixels == null) {
                    graphics.drawImage(image, 0, 0, width, height, null)
                } else {
                    // Only the recorded pixels stand for the user-space size, not the rest of a larger surface.
                    graphics.scale(width / deviceWidth.toDouble(), height / deviceHeight.toDouble())
                    graphics.clipRect(0, 0, deviceWidth, deviceHeight)
                    drawPixels(graphics)
                }
            }

            /** Draws the whole image, one image pixel to one unit of [graphics]. */
            fun drawPixels(graphics: Graphics2D) {
                val pixels = pixels
                graphics.drawImage(image, 0, 0, pixels?.width ?: deviceWidth, pixels?.height ?: deviceHeight, null)
            }
        }

        /** A recording held on a GPU surface, which the graphics device may discard; see [dropIfLost]. */
        private class SurfaceRecording(
            override val image: VolatileImage,
            width: Int,
            height: Int,
            deviceWidth: Int,
            deviceHeight: Int,
            override val pixels: Dimension?,
        ) : Recording(width, height, deviceWidth, deviceHeight) {
            override fun copy(): BufferedImage =
                BufferedImage(deviceWidth, deviceHeight, BufferedImage.TYPE_INT_ARGB).also {
                    it.paintOver(deviceWidth, deviceHeight, ::drawPixels)
                }

            override fun dropIfLost(): Boolean {
                val lost = image.contentsLost()
                if (lost) image.flush()
                return lost
            }

            override fun recycle(
                surfaces: Surfaces,
                spares: Spares?,
            ) = surfaces.recycle(image, deviceWidth, deviceHeight, pixels)

            override fun release(spares: Spares?) = image.flush()
        }

        /** A recording held in a system-memory buffer: what a filter or a render effect reads. */
        private class BufferRecording(
            override val image: BufferedImage,
            width: Int,
            height: Int,
            deviceWidth: Int,
            deviceHeight: Int,
        ) : Recording(width, height, deviceWidth, deviceHeight) {
            override fun copy(): BufferedImage =
                // A raster copy reproduces the recorded pixels exactly, where drawing through a Graphics2D would put
                // them through the pipeline again.
                BufferedImage(image.colorModel, image.copyData(null), image.isAlphaPremultiplied, null)

            override fun dropIfLost(): Boolean = false

            override fun recycle(
                surfaces: Surfaces,
                spares: Spares?,
            ) {
                spares?.recycle(image)
            }

            override fun release(spares: Spares?) {
                spares?.forget(image)
                image.flush()
            }
        }

        /**
         * What [effect] made of the [source] recording: [result], whose top-left corner lies [outsets] above and
         * left of the source's.
         */
        private class AppliedEffect(
            val source: Recording,
            val effect: RenderEffect,
            val outsets: Insets,
            val result: BufferedImage,
        )
    }

/**
 * This composite with [alpha] multiplied in, or plain [alpha] over one that carries no alpha. [alpha] is pinned to
 * `0f..1f` first, `NaN` to `0f`, as Skia pins a paint's alpha, where `AlphaComposite` would throw; an alpha pinned to
 * `1f` returns this composite itself.
 */
internal fun Composite.withAlpha(alpha: Float): Composite {
    val pinned = if (alpha > 0f) alpha.coerceAtMost(1f) else 0f
    if (pinned == 1f) return this
    return (this as? AlphaComposite)?.derive(this.alpha * pinned)
        ?: AlphaComposite.getInstance(AlphaComposite.SRC_OVER, pinned)
}

/** The device pixels [area] covers under this transform: what a recording aligned to it holds of the area. */
internal fun AffineTransform.devicePixels(area: Rectangle): Rectangle =
    if (area.isEmpty) area else createTransformedShape(area).bounds

/**
 * The surfaces one layer records onto: the configuration they are made for, and a surface no recording holds, kept
 * for the next recording.
 */
private class Surfaces {
    /** The destination configuration last seen, and the configuration a surface is made for. */
    private var seen: GraphicsConfiguration? = null
    private var configuration: GraphicsConfiguration = SystemMemory

    private var spare: VolatileImage? = null

    /** Makes the next surface for [destination]'s configuration. */
    fun recordFor(destination: Graphics2D) {
        val destinationConfiguration = destination.deviceConfiguration
        if (destinationConfiguration === seen) return
        seen = destinationConfiguration
        // A surface held in system memory or by XRender validates against a configuration of any scale.
        flush()
        // A destination held in system memory is recorded for there, where the pixels match drawing into it.
        configuration =
            if (destinationConfiguration.imageCapabilities.isAccelerated) destinationConfiguration else SystemMemory
    }

    /** A surface of at least [deviceWidth] x [deviceHeight] pixels: the spare if it fits, or else a new one. */
    fun take(
        deviceWidth: Int,
        deviceHeight: Int,
    ): VolatileImage {
        val configuration = configuration
        // A surface is sized in its configuration's units, which the configuration's default transform scales.
        val scale = configuration.defaultTransform
        val width = ceil(deviceWidth / scale.scaleX).toInt()
        val height = ceil(deviceHeight / scale.scaleY).toInt()
        val kept = spare
        spare = null
        if (kept != null && kept.width == width && kept.height == height) {
            if (kept.validate(configuration) != VolatileImage.IMAGE_INCOMPATIBLE) return kept
        }
        kept?.flush()
        return configuration.createCompatibleVolatileImage(width, height, Transparency.TRANSLUCENT)
    }

    /**
     * The size in pixels of [surface], just taken, where it is larger than the [deviceWidth] x [deviceHeight] asked
     * for, or else `null`. Java2D rounds a scaled size as `sun.java2d.pipe.Region.clipRound` does.
     */
    fun pixels(
        surface: VolatileImage,
        deviceWidth: Int,
        deviceHeight: Int,
    ): Dimension? {
        val scale = configuration.defaultTransform
        // 0.5: what Java2D subtracts from a scaled size before rounding it up.
        val width = ceil(surface.width * scale.scaleX - 0.5).toInt()
        val height = ceil(surface.height * scale.scaleY - 0.5).toInt()
        return if (width == deviceWidth && height == deviceHeight) null else Dimension(width, height)
    }

    /**
     * Keeps [surface] as the spare, flushing the one kept before. [pixels] is what [pixels] answered when the surface
     * was taken for [deviceWidth] x [deviceHeight]: a surface the configuration now scales to other pixels is flushed
     * instead.
     */
    fun recycle(
        surface: VolatileImage,
        deviceWidth: Int,
        deviceHeight: Int,
        pixels: Dimension?,
    ) {
        if (pixels(surface, deviceWidth, deviceHeight) != pixels) {
            surface.flush()
            return
        }
        spare?.flush()
        spare = surface
    }

    fun flush() {
        spare?.flush()
        spare = null
    }
}

/**
 * The buffers one layer no longer shows, kept for its next recording or filter of the same size and type.
 * It keeps two, which is what recording and then filtering on every paint take without allocating.
 */
private class Spares {
    private val buffers = ArrayList<BufferedImage>(MAX_SPARES)

    /** The buffers [take] allocated that are still alive: only these are ever reused. */
    private val allocated: MutableSet<BufferedImage> = Collections.newSetFromMap(IdentityHashMap())

    /** A kept buffer of [width] x [height] and [type], or else a new one. */
    fun take(
        width: Int,
        height: Int,
        type: Int,
    ): BufferedImage {
        buffers.fastForEachIndexed { index, kept ->
            if (kept.width == width && kept.height == height && kept.type == type) return buffers.removeAt(index)
        }
        return BufferedImage(width, height, type).also { allocated += it }
    }

    /**
     * Keeps [buffer] for reuse, flushing the oldest past the limit. A buffer [take] did not allocate, such
     * as one an operation returned, is dropped: its pixels may be shared with an image someone else holds.
     */
    fun recycle(buffer: BufferedImage) {
        if (buffer !in allocated) return
        if (buffers.size == MAX_SPARES) discard(buffers.removeAt(0))
        buffers.add(buffer)
    }

    /**
     * [source] with its pixels premultiplied: [source] itself if they already are, or else a copy made
     * with the arithmetic `BufferedImage.coerceData` uses, which leaves [source] reusable.
     */
    fun premultiplied(source: BufferedImage): BufferedImage =
        when {
            source.isAlphaPremultiplied -> {
                source
            }

            source.type == BufferedImage.TYPE_INT_ARGB -> {
                take(source.width, source.height, BufferedImage.TYPE_INT_ARGB_PRE).also {
                    it.raster.setRect(source.raster)
                    source.colorModel.coerceData(it.raster, true)
                }
            }

            else -> {
                source.apply { coerceData(true) }
            }
        }

    /** A kept buffer for [operation] to filter [source] into, or `null` for it to allocate its own. */
    fun destination(
        operation: BufferedImageOp,
        source: BufferedImage,
    ): BufferedImage? {
        // A convolution writes every pixel of an image laid out exactly as its source.
        return if (operation is ConvolveOp && source.type == BufferedImage.TYPE_INT_ARGB_PRE) {
            take(source.width, source.height, source.type)
        } else {
            null
        }
    }

    /** Stops tracking [buffer] without reusing or flushing it, for a buffer this layer lets go of. */
    fun forget(buffer: BufferedImage) {
        allocated -= buffer
    }

    fun flush() {
        buffers.fastForEach { discard(it) }
        buffers.clear()
    }

    private fun discard(buffer: BufferedImage) {
        forget(buffer)
        buffer.flush()
    }
}

private const val MAX_SPARES = 2

/** Never modified: `Graphics2D.setTransform` copies what it is handed. */
internal val IDENTITY = AffineTransform()

/**
 * A configuration held in system memory: recording for it matches drawing into a [BufferedImage] pixel for pixel.
 */
private val SystemMemory: GraphicsConfiguration =
    BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics().run {
        try {
            deviceConfiguration
        } finally {
            dispose()
        }
    }

/**
 * Clears this image's top-left [width] x [height] pixels to transparent, then draws [block] over them, in pixels
 * whatever the image's configuration scales by.
 */
private inline fun Image.paintOver(
    width: Int,
    height: Int,
    block: (Graphics2D) -> Unit,
) {
    val graphics = graphics as Graphics2D
    try {
        graphics.transform = IDENTITY
        graphics.clipRect(0, 0, width, height)
        // What a BufferedImage's graphics starts with, which a surface's does not.
        graphics.color = Color.WHITE
        graphics.background = Color.BLACK
        graphics.composite = AlphaComposite.Clear
        graphics.fillRect(0, 0, width, height)
        graphics.composite = AlphaComposite.SrcOver
        block(graphics)
    } finally {
        graphics.dispose()
    }
}
