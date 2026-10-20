package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.Rectangle
import java.awt.image.BufferedImage
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Behavioral tests for a decoration: what the [Decorator] steps a component declares paint through
 * [Decoration.paint], and in what order.
 *
 * Each component is painted against an off-screen raster and read back pixel by pixel, so what is asserted is what it
 * would show.
 */
class DecorationTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("componentMemberRejectionCases")
    fun componentMembersRejectADecoratableThatIsNotAComponent(
        name: String,
        operation: (Decoratable, Graphics) -> Unit,
    ) {
        val decoratable =
            object : Decoratable {
                override var decoration: Decoration = Decoration.None
            }
        val graphics = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics()
        try {
            val failure =
                assertFailsWith<IllegalArgumentException>("$name must require a Component") {
                    operation(decoratable, graphics)
                }
            assertEquals(
                "A ${Decoratable::class.java.name} paints as a component, and " +
                    "${decoratable.javaClass.name} is not one",
                failure.message,
                "$name must identify the invalid target",
            )
        } finally {
            graphics.dispose()
        }
    }

    @Test
    fun repaintMembersTakeADecoratableThatIsAComponent() =
        runComposeSwingTest {
            val target =
                object : Component(), Decoratable {
                    override var decoration: Decoration = Decoration.None
                }
            target.setSize(10, 10)
            val handed = mutableListOf<Rectangle>()

            Decoration.None.repaint(target, 0, 1, 2, 3, 4) { _, x, y, width, height ->
                handed += Rectangle(x, y, width, height)
            }
            Decoration.None.paintImmediately(target, 1, 2, 3, 4) { x, y, width, height ->
                handed += Rectangle(x, y, width, height)
            }

            assertEquals(listOf(Rectangle(1, 2, 3, 4), Rectangle(1, 2, 3, 4)), handed)
        }

    @Test
    fun getGraphicsFailsOffTheEventDispatchThread() {
        val failure = assertFailsWith<IllegalStateException> { DecoratedPanel().graphics }
        assertEquals(
            "Compose-Swing must be used on the Event Dispatch Thread, but was called on " +
                "'${Thread.currentThread().name}'. Wrap the call in SwingUtilities.invokeLater { }.",
            failure.message,
        )
    }

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
    fun outsetsArePaintOutsetsReportedInTheInsets() =
        runComposeSwingTest {
            setContent {
                Box {
                    SwingNode(
                        factory = { DecoratedPanel() },
                        modifier =
                            SwingModifier
                                .testTag("panel")
                                .preferredSize(SIZE, SIZE)
                                .spill(Insets(1, 2, 3, 4))
                                .spill(Insets(10, 20, 30, 40)),
                    )
                }
            }
            val panel = onNodeWithTag("panel").fetch<JComponent>()

            assertEquals(Insets(11, 22, 33, 44), panel.paintOutsets, "The steps add around the content what each adds.")
            assertEquals(
                Insets(11, 22, 33, 44),
                panel.insets,
                "The insets carry the paint outsets, as a look and feel reports a focus ring.",
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
                val box = decoration.localLayoutBounds(this)
                graphics.translate(box.x, box.y)
                content(graphics, box.width, box.height)
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

        @JvmStatic
        fun componentMemberRejectionCases(): List<Arguments> {
            val operations: List<Pair<String, (Decoratable, Graphics) -> Unit>> =
                listOf(
                    "getGraphics" to { target, graphics -> Decoration.None.getGraphics(target, graphics) },
                    "isOpaque" to { target, _ -> Decoration.None.isOpaque(target) },
                    "localLayoutBounds" to { target, _ -> Decoration.None.localLayoutBounds(target) },
                    "paint" to { target, graphics ->
                        Decoration.None.paint(target, graphics) { error("Invalid target painted") }
                    },
                    "contains" to { target, _ -> Decoration.None.contains(target, 0, 0) },
                    "repaint" to { target, _ ->
                        Decoration.None.repaint(target, 0, 0, 0, 1, 1) { _, _, _, _, _ ->
                            error("Invalid target repainted")
                        }
                    },
                    "paintImmediately" to { target, _ ->
                        Decoration.None.paintImmediately(target, 0, 0, 1, 1) { _, _, _, _ ->
                            error("Invalid target painted")
                        }
                    },
                )
            return operations.map { (name, operation) ->
                Arguments.of(Named.of("$name rejects a Decoratable that is not a Component", name), operation)
            }
        }
    }
}
