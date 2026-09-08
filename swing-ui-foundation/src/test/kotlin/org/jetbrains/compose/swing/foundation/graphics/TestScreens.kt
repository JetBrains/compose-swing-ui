package org.jetbrains.compose.swing.foundation.graphics

import java.awt.Color
import java.awt.Composite
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsConfiguration
import java.awt.GraphicsDevice
import java.awt.Image
import java.awt.ImageCapabilities
import java.awt.Paint
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.Stroke
import java.awt.font.FontRenderContext
import java.awt.font.GlyphVector
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.awt.image.BufferedImageOp
import java.awt.image.ColorModel
import java.awt.image.ImageObserver
import java.awt.image.RenderedImage
import java.awt.image.VolatileImage
import java.awt.image.renderable.RenderableImage
import java.text.AttributedCharacterIterator
import kotlin.math.ceil

/**
 * An accelerated [GraphicsConfiguration] at [scale] device pixels per unit, without a display: a recording made
 * for it behaves as one made for a real HiDPI screen, so a test reaches the same code path a real screen does.
 *
 * A [losable] screen makes its surfaces as [LosableSurface]s and lists them in [surfaces], so a test can have the
 * device discard one.
 */
internal class FakeScreen(
    private val scale: Double,
    private val losable: Boolean = false,
) : GraphicsConfiguration() {
    private val base = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics().deviceConfiguration

    /** The surfaces a [losable] screen made, oldest first. */
    val surfaces = ArrayList<LosableSurface>()

    /** A fresh [width] x [height] (logical units) surface for this screen, and a [Graphics2D] painting into it. */
    fun surface(
        width: Int,
        height: Int,
    ): FakeScreenSurface {
        val deviceWidth = ceil(width * scale).toInt()
        val deviceHeight = ceil(height * scale).toInt()
        val pixels = BufferedImage(deviceWidth, deviceHeight, BufferedImage.TYPE_INT_ARGB)
        val real = pixels.createGraphics()
        real.scale(scale, scale)
        return FakeScreenSurface(DelegatingGraphics2D(real, this), pixels)
    }

    /** A [Graphics2D] reporting this configuration, painting at [width] x [height] logical units. */
    fun graphics(
        width: Int,
        height: Int,
    ): Graphics2D = surface(width, height).graphics

    override fun getDevice(): GraphicsDevice = base.device

    override fun getColorModel(): ColorModel = base.colorModel

    override fun getColorModel(transparency: Int): ColorModel = base.getColorModel(transparency)

    override fun getDefaultTransform(): AffineTransform = AffineTransform.getScaleInstance(scale, scale)

    override fun getNormalizingTransform() = AffineTransform()

    override fun getBounds() = Rectangle(0, 0, 1000, 1000)

    override fun getImageCapabilities() = ImageCapabilities(true)

    override fun createCompatibleVolatileImage(
        width: Int,
        height: Int,
        transparency: Int,
    ): VolatileImage =
        if (losable) {
            LosableSurface(width, height).also { surfaces += it }
        } else {
            super.createCompatibleVolatileImage(width, height, transparency)
        }
}

/**
 * A surface a test can have the graphics device discard: it holds its drawing in [pixels], and reports its contents
 * lost once [lost] is set. Java2D draws only surfaces it made itself, so only [DelegatingGraphics2D.drawImage] with
 * a width and height draws this one.
 */
internal class LosableSurface(
    width: Int,
    height: Int,
) : VolatileImage() {
    val pixels = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    var lost = false

    override fun createGraphics(): Graphics2D = pixels.createGraphics()

    override fun getSnapshot() = BufferedImage(pixels.colorModel, pixels.copyData(null), false, null)

    override fun getWidth() = pixels.width

    override fun getHeight() = pixels.height

    override fun getWidth(observer: ImageObserver?) = pixels.width

    override fun getHeight(observer: ImageObserver?) = pixels.height

    override fun getProperty(
        name: String?,
        observer: ImageObserver?,
    ): Any = UndefinedProperty

    override fun validate(gc: GraphicsConfiguration?) = IMAGE_OK

    override fun contentsLost() = lost

    override fun getCapabilities() = ImageCapabilities(true)
}

/** A [FakeScreen.surface]'s [graphics] and the [pixels] it paints into. */
internal class FakeScreenSurface(
    val graphics: Graphics2D,
    val pixels: BufferedImage,
)

/**
 * A [Graphics2D] that paints through [g] but reports [config] as its device, as a real screen's graphics would, and
 * hands [onDrawImage] every image a plain `drawImage(image, x, y, observer)` call draws. It draws a [LosableSurface]
 * scaled to a width and height from the surface's [LosableSurface.pixels].
 */
internal class DelegatingGraphics2D(
    private val g: Graphics2D,
    private val config: GraphicsConfiguration = g.deviceConfiguration,
    private val onDrawImage: (Image) -> Unit = {},
) : Graphics2D() {
    override fun getDeviceConfiguration() = config

    override fun create(): Graphics = DelegatingGraphics2D(g.create() as Graphics2D, config, onDrawImage)

    override fun draw(s: Shape) = g.draw(s)

    override fun drawImage(
        img: Image,
        xform: AffineTransform,
        obs: ImageObserver?,
    ) = g.drawImage(img, xform, obs)

    override fun drawImage(
        img: BufferedImage,
        op: BufferedImageOp?,
        x: Int,
        y: Int,
    ) = g.drawImage(img, op, x, y)

    override fun drawRenderedImage(
        img: RenderedImage,
        xform: AffineTransform,
    ) = g.drawRenderedImage(img, xform)

    override fun drawRenderableImage(
        img: RenderableImage,
        xform: AffineTransform,
    ) = g.drawRenderableImage(img, xform)

    override fun drawString(
        str: String,
        x: Int,
        y: Int,
    ) = g.drawString(str, x, y)

    override fun drawString(
        str: String,
        x: Float,
        y: Float,
    ) = g.drawString(str, x, y)

    override fun drawString(
        iterator: AttributedCharacterIterator,
        x: Int,
        y: Int,
    ) = g.drawString(iterator, x, y)

    override fun drawString(
        iterator: AttributedCharacterIterator,
        x: Float,
        y: Float,
    ) = g.drawString(iterator, x, y)

    override fun drawGlyphVector(
        gv: GlyphVector,
        x: Float,
        y: Float,
    ) = g.drawGlyphVector(gv, x, y)

    override fun fill(s: Shape) = g.fill(s)

    override fun hit(
        rect: Rectangle,
        s: Shape,
        onStroke: Boolean,
    ) = g.hit(rect, s, onStroke)

    override fun setComposite(comp: Composite) {
        g.composite = comp
    }

    override fun setPaint(paint: Paint?) {
        g.paint = paint
    }

    override fun setStroke(s: Stroke) {
        g.stroke = s
    }

    override fun setRenderingHint(
        hintKey: RenderingHints.Key,
        hintValue: Any?,
    ) = g.setRenderingHint(hintKey, hintValue)

    override fun getRenderingHint(hintKey: RenderingHints.Key): Any? = g.getRenderingHint(hintKey)

    override fun setRenderingHints(hints: Map<*, *>) = g.setRenderingHints(hints)

    override fun addRenderingHints(hints: Map<*, *>) = g.addRenderingHints(hints)

    override fun getRenderingHints(): RenderingHints = g.renderingHints

    override fun translate(
        x: Int,
        y: Int,
    ) = g.translate(x, y)

    override fun translate(
        tx: Double,
        ty: Double,
    ) = g.translate(tx, ty)

    override fun rotate(theta: Double) = g.rotate(theta)

    override fun rotate(
        theta: Double,
        x: Double,
        y: Double,
    ) = g.rotate(theta, x, y)

    override fun scale(
        sx: Double,
        sy: Double,
    ) = g.scale(sx, sy)

    override fun shear(
        shx: Double,
        shy: Double,
    ) = g.shear(shx, shy)

    override fun transform(tx: AffineTransform) = g.transform(tx)

    override fun setTransform(tx: AffineTransform) {
        g.transform = tx
    }

    override fun getTransform(): AffineTransform = g.transform

    override fun getPaint(): Paint = g.paint

    override fun getComposite(): Composite = g.composite

    override fun setBackground(color: Color?) {
        g.background = color
    }

    override fun getBackground(): Color = g.background

    override fun getStroke(): Stroke = g.stroke

    override fun clip(s: Shape?) = g.clip(s)

    override fun getFontRenderContext(): FontRenderContext = g.fontRenderContext

    override fun getColor(): Color = g.color

    override fun setColor(c: Color?) {
        g.color = c
    }

    override fun setPaintMode() = g.setPaintMode()

    override fun setXORMode(c1: Color) = g.setXORMode(c1)

    override fun getFont(): Font = g.font

    override fun setFont(font: Font?) {
        g.font = font
    }

    override fun getFontMetrics(f: Font): FontMetrics = g.getFontMetrics(f)

    override fun getClipBounds(): Rectangle? = g.clipBounds

    override fun clipRect(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) = g.clipRect(x, y, width, height)

    override fun setClip(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) = g.setClip(x, y, width, height)

    override fun getClip(): Shape? = g.clip

    override fun setClip(clip: Shape?) {
        g.clip = clip
    }

    override fun copyArea(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        dx: Int,
        dy: Int,
    ) = g.copyArea(x, y, width, height, dx, dy)

    override fun drawLine(
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
    ) = g.drawLine(x1, y1, x2, y2)

    override fun fillRect(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) = g.fillRect(x, y, width, height)

    override fun clearRect(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) = g.clearRect(x, y, width, height)

    override fun drawRoundRect(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        arcWidth: Int,
        arcHeight: Int,
    ) = g.drawRoundRect(x, y, width, height, arcWidth, arcHeight)

    override fun fillRoundRect(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        arcWidth: Int,
        arcHeight: Int,
    ) = g.fillRoundRect(x, y, width, height, arcWidth, arcHeight)

    override fun drawOval(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) = g.drawOval(x, y, width, height)

    override fun fillOval(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) = g.fillOval(x, y, width, height)

    override fun drawArc(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        startAngle: Int,
        arcAngle: Int,
    ) = g.drawArc(x, y, width, height, startAngle, arcAngle)

    override fun fillArc(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        startAngle: Int,
        arcAngle: Int,
    ) = g.fillArc(x, y, width, height, startAngle, arcAngle)

    override fun drawPolyline(
        xPoints: IntArray,
        yPoints: IntArray,
        nPoints: Int,
    ) = g.drawPolyline(xPoints, yPoints, nPoints)

    override fun drawPolygon(
        xPoints: IntArray,
        yPoints: IntArray,
        nPoints: Int,
    ) = g.drawPolygon(xPoints, yPoints, nPoints)

    override fun fillPolygon(
        xPoints: IntArray,
        yPoints: IntArray,
        nPoints: Int,
    ) = g.fillPolygon(xPoints, yPoints, nPoints)

    override fun drawImage(
        img: Image,
        x: Int,
        y: Int,
        observer: ImageObserver?,
    ): Boolean {
        onDrawImage(img)
        return g.drawImage(img, x, y, observer)
    }

    override fun drawImage(
        img: Image,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        observer: ImageObserver?,
    ) = g.drawImage((img as? LosableSurface)?.pixels ?: img, x, y, width, height, observer)

    override fun drawImage(
        img: Image,
        x: Int,
        y: Int,
        bgcolor: Color?,
        observer: ImageObserver?,
    ) = g.drawImage(img, x, y, bgcolor, observer)

    override fun drawImage(
        img: Image,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        bgcolor: Color?,
        observer: ImageObserver?,
    ) = g.drawImage(img, x, y, width, height, bgcolor, observer)

    override fun drawImage(
        img: Image,
        dx1: Int,
        dy1: Int,
        dx2: Int,
        dy2: Int,
        sx1: Int,
        sy1: Int,
        sx2: Int,
        sy2: Int,
        observer: ImageObserver?,
    ) = g.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, observer)

    override fun drawImage(
        img: Image,
        dx1: Int,
        dy1: Int,
        dx2: Int,
        dy2: Int,
        sx1: Int,
        sy1: Int,
        sx2: Int,
        sy2: Int,
        bgcolor: Color?,
        observer: ImageObserver?,
    ) = g.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, bgcolor, observer)

    override fun dispose() = g.dispose()
}
