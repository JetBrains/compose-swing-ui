package org.jetbrains.compose.swing.foundation.graphics

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.foundation.graphics.drawscope.ContentDrawScope
import org.jetbrains.compose.swing.foundation.graphics.drawscope.DrawScope
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.ComponentStyleScope
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import java.awt.AlphaComposite
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.Rectangle
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import javax.swing.JComponent
import kotlin.test.assertIs

/** Creates a transparent raster and gives its graphics to a test drawing directly without a Swing component. */
internal inline fun renderImage(
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
 * after its size and drawing [draw] for its index in `drawBehind`, on the default rendering hints of `Canvas`. Returns
 * the canvases in order.
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
                            .then(decoration(index))
                            .drawBehind { draw(index) }
                            .styleable { preferredSize(width, height) },
                )
            }
        }
    }
    return List(count) { onNodeWithTag("canvas $it").fetch<JComponent>() }
}

/** The test's transparent [DecoratedPanel] declaring [modifier] and [style], drawing through [draw]. */
@Composable
internal fun DecoratedCanvas(
    modifier: SwingModifier,
    style: SwingStyle<ComponentStyleScope> = SwingStyle,
    draw: DrawScope.() -> Unit = {},
) {
    SwingNode(
        factory = { DecoratedPanel().apply { isOpaque = false } },
        modifier = modifier.drawBehind(draw).styleable(style),
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

/** Repainting [areas] over [before] matches a whole repaint pixel for pixel. */
internal fun JComponent.assertPartialRepaintMatchesWhole(
    before: BufferedImage,
    areas: List<Rectangle>,
) {
    val partial =
        renderImage(width, height) { graphics ->
            graphics.drawImage(before, 0, 0, null)
            areas.forEach { area ->
                graphics.composite = AlphaComposite.Clear
                graphics.fill(area)
                graphics.composite = AlphaComposite.SrcOver
                graphics.drawImage(paintOnto(width, height, clip = area), 0, 0, null)
            }
        }
    val full = paintOnto(width, height)
    assertImagesPixelPerfect(full, partial)
}

/** The paint outsets of this decorated component. */
internal val JComponent.paintOutsets: Insets get() = assertIs<Decoratable>(this).decoration.paintOutsets()

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

/** Test-only drawing leaf used where tests need a raw Swing view. */
@Composable
internal fun Canvas(
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<ComponentStyleScope> = SwingStyle,
    onDraw: (Graphics2D, Int, Int) -> Unit,
) {
    SwingNode(
        factory = { DecoratedPanel().apply { isOpaque = false } },
        modifier = (modifier then TestDrawElement(onDraw)).styleable(style),
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
        val surface =
            assertIs<Graphics2D>(graphics.create(0, 0, size.width, size.height), "expected a Graphics2D copy")
        try {
            onDraw(surface, size.width, size.height)
        } finally {
            surface.dispose()
        }
        drawContent()
    }
}
