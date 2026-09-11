package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behavioral tests for a decoration: what the [Decorator] steps a component declares paint through
 * [Decoration.paint], and in what order.
 *
 * Each component is painted against an off-screen raster and read back pixel by pixel, so what is asserted is what it
 * would show.
 */
class DecorationTest {
    @Test
    fun noStepsPaintTheContentUnchanged() =
        runComposeSwingTest {
            val painted = paint { graphics, width, height -> graphics.fill(Color.RED, 0, 0, width, height) }

            assertEquals(
                Color.RED.rgb,
                painted.getRGB(0, 0),
                "Without steps the content is handed the graphics untouched.",
            )
        }

    @Test
    fun nestingOrderChangesWhatIsPainted() =
        runComposeSwingTest {
            val content: (Graphics2D, Int, Int) -> Unit =
                { graphics, width, height -> graphics.fill(Color.RED, 0, 0, width, height) }
            val (clipOutside, backgroundOutside) =
                paintEach(listOf(listOf(Cut(), Fill(Color.BLUE)), listOf(Fill(Color.BLUE), Cut())), content)

            assertEquals(
                0,
                clipOutside.getRGB(0, 0),
                "A clip declared first is outermost, so the background it wraps is cut with the content.",
            )
            assertEquals(
                Color.BLUE.rgb,
                backgroundOutside.getRGB(0, 0),
                "A background declared first is outermost, so it fills the corners the clip inside it cuts.",
            )
        }

    @Test
    fun aDecoratorMayPointTheContentAtARasterOfItsOwn() =
        runComposeSwingTest {
            val painted =
                paint(listOf(Inverting)) { graphics, width, height ->
                    graphics.fill(Color.RED, 0, 0, width, height)
                }

            assertEquals(
                Color.RED.rgb.inv() or (0xFF shl 24),
                painted.getRGB(SIZE / 2, SIZE / 2),
                "The content paints where the decorator sends it, so a decorator that alters the raster it " +
                    "handed over shows the altered pixels.",
            )
        }

    @Test
    fun whatOneDecoratorLeavesOnTheGraphicsReachesNoOther() =
        runComposeSwingTest {
            val painted =
                paint(listOf(LeftRule, Leaky)) { graphics, width, height ->
                    graphics.fill(Color.RED, width / 4, height / 4, width / 2, height / 2)
                }

            assertEquals(
                Color.BLUE.rgb,
                painted.getRGB(0, SIZE / 2),
                "A clip a decorator set on its own graphics does not reach the decorator that handed it over, even " +
                    "one painting after its content.",
            )
            assertEquals(
                Color.RED.rgb,
                painted.getRGB(SIZE / 2, SIZE / 2),
                "A decorator's graphics is its own and is disposed after it.",
            )
        }

    @Test
    fun stepsPaintedAgainFromInsideTheirOwnContentPaintBothCorrectly() =
        runComposeSwingTest {
            // One decorator declared on a component and on a child it paints is painted from inside its own content.
            val nested = BufferedImage(SIZE / 2, SIZE / 2, BufferedImage.TYPE_INT_ARGB)
            val outerSizes = mutableListOf<Pair<Int, Int>>()
            var nestedSize = 0 to 0
            var outer: (Graphics2D, Int, Int) -> Unit = { _, _, _ -> }
            setContent {
                SwingNode(
                    factory = { ContentPanel { graphics, width, height -> outer(graphics, width, height) } },
                    modifier = contentModifier("outer", SIZE, listOf(PaintsTwice)),
                )
                SwingNode(
                    factory = {
                        ContentPanel { graphics, width, height ->
                            nestedSize = width to height
                            graphics.fill(Color.BLUE, 0, 0, width, height)
                        }
                    },
                    modifier = contentModifier("inner", SIZE / 2, listOf(PaintsTwice)),
                )
            }
            val inner = onNodeWithTag("inner").fetch<JComponent>()
            outer = { graphics, width, height ->
                outerSizes += width to height
                if (outerSizes.size == 1) {
                    val into = nested.createGraphics()
                    try {
                        inner.paint(into)
                    } finally {
                        into.dispose()
                    }
                } else {
                    graphics.fill(Color.RED, 0, 0, width, height)
                }
            }
            val painted = onNodeWithTag("outer").fetch<JComponent>().paintOnto(SIZE, SIZE)

            assertEquals(SIZE / 2 to SIZE / 2, nestedSize, "The nested paint hands its content its own size.")
            assertEquals(Color.BLUE.rgb, nested.getRGB(0, 0), "The nested paint runs its own content.")
            assertEquals(
                listOf(SIZE to SIZE, SIZE to SIZE),
                outerSizes,
                "After the nested paint returns, the outer paint runs its own content at its own size again.",
            )
            assertEquals(Color.RED.rgb, painted.getRGB(4, 4), "The outer content paints over the outer raster.")
        }

    /** A [DecoratedPanel] painting [content] at its own size, in place of its own painting. */
    private class ContentPanel(
        private val content: (Graphics2D, Int, Int) -> Unit,
    ) : DecoratedPanel() {
        override fun paintComponent(g: Graphics) {
            val graphics = g.create() as Graphics2D
            try {
                content(graphics, width, height)
            } finally {
                graphics.dispose()
            }
        }
    }

    /** Points the content at a raster of its own and draws it with the colors inverted. */
    private object Inverting : Decorator {
        override fun paint(
            graphics: Graphics2D,
            width: Int,
            height: Int,
            content: (Graphics2D, Int, Int) -> Unit,
        ) {
            val raster = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            val into = raster.createGraphics()
            try {
                content(into, width, height)
            } finally {
                into.dispose()
            }
            for (x in 0 until width) {
                for (y in 0 until height) {
                    raster.setRGB(x, y, raster.getRGB(x, y).inv() or (0xFF shl 24))
                }
            }
            graphics.drawImage(raster, 0, 0, null)
        }
    }

    /** Runs its content twice, as a decorator painting a silhouette of the content first does. */
    private object PaintsTwice : Decorator {
        override fun paint(
            graphics: Graphics2D,
            width: Int,
            height: Int,
            content: (Graphics2D, Int, Int) -> Unit,
        ) {
            content(graphics, width, height)
            content(graphics, width, height)
        }
    }

    /** Paints its content, then a line down its left edge over it. */
    private object LeftRule : Decorator {
        override fun paint(
            graphics: Graphics2D,
            width: Int,
            height: Int,
            content: (Graphics2D, Int, Int) -> Unit,
        ) {
            content(graphics, width, height)
            graphics.color = Color.BLUE
            graphics.fillRect(0, 0, 2, height)
        }
    }

    /** Leaves a clip and a paint behind on the graphics it was handed, to show they reach nothing else. */
    private object Leaky : Decorator {
        override fun paint(
            graphics: Graphics2D,
            width: Int,
            height: Int,
            content: (Graphics2D, Int, Int) -> Unit,
        ) {
            graphics.clipRect(width / 4, height / 4, width / 2, height / 2)
            graphics.paint = Color.GREEN
            content(graphics, width, height)
        }
    }

    /**
     * The modifier of a transparent [side] by [side] component tagged [tag] declaring [decorators], the first
     * outermost.
     */
    private fun contentModifier(
        tag: String,
        side: Int,
        decorators: List<Decorator>,
    ): SwingModifier =
        decorators.fold(SwingModifier.testTag(tag).opaque(false).preferredSize(side, side)) { modifier, decorator ->
            modifier.then(DecoratorElement(decorator))
        }

    /** What a [SIZE] by [SIZE] component declaring [decorators], the first outermost, paints with [content] inside. */
    private fun ComposeSwingTest.paint(
        decorators: List<Decorator> = emptyList(),
        content: (Graphics2D, Int, Int) -> Unit,
    ): BufferedImage = paintEach(listOf(decorators), content).single()

    /** What each of [SIZE] by [SIZE] components, one per list of [decorations], paints with [content] inside. */
    private fun ComposeSwingTest.paintEach(
        decorations: List<List<Decorator>>,
        content: (Graphics2D, Int, Int) -> Unit,
    ): List<BufferedImage> {
        setContent {
            decorations.forEachIndexed { index, decorators ->
                SwingNode(
                    factory = { ContentPanel(content) },
                    modifier = contentModifier("painted $index", SIZE, decorators),
                )
            }
        }
        return decorations.indices.map { onNodeWithTag("painted $it").fetch<JComponent>().paintOnto(SIZE, SIZE) }
    }

    private fun Graphics2D.fill(
        color: Color,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        paint = color
        fillRect(x, y, width, height)
    }

    private companion object {
        const val SIZE = 64
    }
}
