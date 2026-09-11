package org.jetbrains.compose.swing.foundation.graphics

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.foundation.graphics.drawscope.ContentDrawScope
import org.jetbrains.compose.swing.foundation.graphics.drawscope.DrawScope
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import javax.swing.JComponent
import kotlin.math.abs

/** Creates a transparent raster and gives its graphics to a test drawing directly without a Swing component. */
internal fun renderImage(
    width: Int,
    height: Int,
    type: Int = BufferedImage.TYPE_INT_ARGB,
    block: (Graphics2D) -> Unit,
): BufferedImage {
    val image = BufferedImage(width, height, type)
    val graphics = image.createGraphics()
    try {
        block(graphics)
    } finally {
        graphics.dispose()
    }
    return image
}

/**
 * Composes, in a column, [count] canvases of [width] by [height], each declaring the [decoration] built for its index
 * after its size and drawing [draw] for its index with the rendering hints of the graphics it is given. Returns the
 * canvases in order.
 */
internal fun ComposeSwingTest.decoratedCanvases(
    width: Int,
    height: Int,
    count: Int,
    decoration: (Int) -> SwingModifier,
    draw: DrawScope.(Int) -> Unit,
): List<JComponent> {
    setContent {
        Column {
            for (index in 0 until count) {
                SwingNode(
                    factory = { DecoratedPanel().apply { isOpaque = false } },
                    modifier =
                        SwingModifier
                            .testTag("canvas $index")
                            .preferredSize(width, height)
                            .then(decorated { decoration(index).drawBehind { draw(index) } }),
                )
            }
        }
    }
    return List(count) { onNodeWithTag("canvas $it").fetch<JComponent>() }
}

/** The test's [DecoratedPanel], its modifier built by [modifier], drawing through [draw]. */
@Composable
internal fun DecoratedCanvas(
    modifier: () -> SwingModifier,
    draw: DrawScope.() -> Unit = {},
) {
    SwingNode(
        factory = { DecoratedPanel().apply { isOpaque = false } },
        modifier = decorated { modifier().drawBehind(draw) },
    )
}

/**
 * What this component paints onto a transparent [width] by [height] image through [transform], clipped to [clip] in
 * its own coordinates, as Swing paints it.
 */
internal fun JComponent.paintOnto(
    width: Int,
    height: Int,
    transform: AffineTransform = AffineTransform(),
    clip: Rectangle = Rectangle(size),
): BufferedImage =
    renderImage(width, height) {
        it.transform(transform)
        it.clip(clip)
        paint(it)
    }

/** The sizes of the images a plain `drawImage(image, x, y, observer)` call draws while this paints [clip]. */
internal fun JComponent.imageSizesDrawnPainting(clip: Rectangle): List<Dimension> {
    val sizes = mutableListOf<Dimension>()
    renderImage(width, height) { graphics ->
        val spy =
            DelegatingGraphics2D(graphics) { image -> sizes += Dimension(image.getWidth(null), image.getHeight(null)) }
        spy.clip(clip)
        paint(spy)
    }
    return sizes
}

/** The largest difference of one channel between this image and [other] over [pixels], every pixel by default. */
internal fun BufferedImage.channelDifference(
    other: BufferedImage,
    pixels: List<Pair<Int, Int>> = (0 until height).flatMap { y -> (0 until width).map { x -> x to y } },
): Int =
    pixels.maxOf { (x, y) ->
        val pixel = getRGB(x, y)
        val otherPixel = other.getRGB(x, y)
        (0 until Int.SIZE_BITS step Byte.SIZE_BITS).maxOf { shift ->
            abs((pixel ushr shift and 0xFF) - (otherPixel ushr shift and 0xFF))
        }
    }

/** Test-only drawing leaf used where tests need a raw Swing view. */
@Composable
internal fun Canvas(
    modifier: SwingModifier = SwingModifier,
    onDraw: (Graphics2D, Int, Int) -> Unit,
) {
    SwingNode(
        factory = { DecoratedPanel().apply { isOpaque = false } },
        modifier = modifier then TestDrawElement(onDraw),
    )
}

private class TestDrawElement(
    val onDraw: (Graphics2D, Int, Int) -> Unit,
) : SwingModifier.NodeElement<JComponent, TestDrawNode>() {
    override val targetType: Class<JComponent> get() = JComponent::class.java

    override val additive: Boolean get() = true

    override fun create(): TestDrawNode = TestDrawNode(onDraw)

    override fun update(node: TestDrawNode) {
        if (node.onDraw === onDraw) return
        node.onDraw = onDraw
        node.invalidateDraw()
    }

    override fun equals(other: Any?): Boolean = other is TestDrawElement && onDraw === other.onDraw

    override fun hashCode(): Int = System.identityHashCode(onDraw)
}

private class TestDrawNode(
    var onDraw: (Graphics2D, Int, Int) -> Unit,
) : DrawModifierNode<JComponent>() {
    override fun ContentDrawScope.draw() {
        val border = component.border?.getBorderInsets(component)
        val left = border?.left ?: 0
        val top = border?.top ?: 0
        val drawWidth = (size.width - left - (border?.right ?: 0)).coerceAtLeast(0)
        val drawHeight = (size.height - top - (border?.bottom ?: 0)).coerceAtLeast(0)
        val surface = graphics.create(left, top, drawWidth, drawHeight) as Graphics2D
        try {
            onDraw(surface, drawWidth, drawHeight)
        } finally {
            surface.dispose()
        }
        drawContent()
    }
}
