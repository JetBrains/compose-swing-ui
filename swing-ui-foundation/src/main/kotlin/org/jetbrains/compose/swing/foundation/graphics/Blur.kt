@file:JvmMultifileClass
@file:JvmName("GraphicsKt")

package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.Rectangle
import java.awt.RenderingHints
import kotlin.math.abs

/**
 * Softens everything inside it, by [radius]: the component's content, its border and its children.
 *
 * The blur fades into transparency around the content. What it spreads past the component's bounds is clipped,
 * and a clip declared before this can cut the halo.
 *
 * @param radius how far each pixel is spread, in the component's own units; `0` or less softens nothing.
 * @return this chain with the blur declared on it.
 */
public fun SwingModifier.blur(radius: Int): SwingModifier = decoration(BlurElement(radius))

/** The additive element behind [SwingModifier.blur]. */
private data class BlurElement(
    private val radius: Int,
) : SwingModifier.NodeElement<Component, BlurNode>() {
    override val name: String get() = "blur"

    override val additive: Boolean get() = true

    override val targetType: Class<Component> get() = Component::class.java

    override val declaredValues: Map<String, Any?> get() = mapOf("radius" to radius)

    override fun create(): BlurNode = BlurNode(radius)

    override fun update(node: BlurNode) {
        node.radius = radius
        node.component.repaint()
    }
}

/**
 * Records the decorated content into an off-screen [ImageLayer] and draws it through a [BlurEffect].
 *
 * @see blur
 */
private class BlurNode(
    radius: Int,
) : DecorationModifierNode<Component>() {
    /** What the content is recorded in, created by the first paint and released while the blur paints nothing. */
    private var layer: ImageLayer? = null

    var radius: Int = radius
        set(value) {
            field = value
            effect = BlurEffect(value.toFloat(), edgeTreatment = TileMode.Decal)
            drawEffect = BlurEffect(value.toFloat())
            if (value <= 0) {
                layer?.release()
                layer = null
            }
        }

    private var effect: BlurEffect = BlurEffect(radius.toFloat(), edgeTreatment = TileMode.Decal)

    /**
     * What [paint] draws through: a Clamp blur of the same radius, which reads the reach
     * [BlurEffect.recordingBounds] reserves.
     */
    private var drawEffect: BlurEffect = BlurEffect(radius.toFloat())

    override val isOpaque: Boolean get() = false

    override val paintsNothing: Boolean get() = radius <= 0

    /** How far the blur spreads the content, recorded at a scale of `1.0`. */
    override val outsets: Insets get() = effect.outsets(1.0)

    override fun paint(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    ) {
        if (width <= 0 || height <= 0) return
        val area = graphics.clipArea()
        if (area.isEmpty) return
        val bounds = effect.recordingBounds(area)
        val scale = effect.recordingScale
        val layer = layer ?: ImageLayer().also { layer = it }
        layer.record(bounds.width, bounds.height, scale) { recording ->
            // A thin line then covers only part of a reduced pixel, which antialiasing records rather than dropping it
            // or widening it to a whole pixel, depending on where it falls.
            if (scale < 1) {
                recording.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            }
            recording.translate(-bounds.x, -bounds.y)
            content(recording, width, height)
        }
        layer.renderEffect = drawEffect
        layer.draw(graphics, bounds.x, bounds.y)
    }

    override fun onRemovedFromDecoration() {
        layer?.release()
        layer = null
    }
}

/**
 * The area this graphics paints: its clip, which is what a decorator recording its content records, or an
 * empty area under a transform that flattens the plane, where Java2D answers no clip. A decorated component hands
 * every decorator a clipped graphics, and an [ImageLayer] recording is clipped to its buffer.
 */
internal fun Graphics2D.clipArea(): Rectangle {
    val clip =
        clip ?: run {
            check(abs(transform.determinant) <= Double.MIN_VALUE) {
                "A decorator that records its content records the area its graphics clips to, and this graphics " +
                    "has no clip; hand a decorator's content a clipped graphics, such as an ImageLayer recording"
            }
            return Rectangle()
        }
    return clip as? Rectangle ?: clip.bounds
}
