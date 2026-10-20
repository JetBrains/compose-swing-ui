package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.foundation.graphics.drawscope.scale
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.ConstrainedPanel
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Color
import java.awt.Component
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.Rectangle
import java.awt.Shape
import java.awt.geom.AffineTransform
import java.util.stream.Stream
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals

/** A container's repaint covers the pixels a child's steps paint, and grows by outsets alone where they allow. */
class PartialRepaintReachTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("steps")
    fun aContainerRepaintGrowsToAllOfAChildWhoseStepsMoveItsContent(
        name: String,
        decorate: (SwingModifier) -> SwingModifier,
    ) = runComposeSwingTest {
        val fixture = composeShadowedCardInOuterBox(decorate)
        // The steps mirror the 200 wide card, so the pixel the leaf draws at x = 10 lands at x = 189.
        val painted = SwingUtilities.convertRectangle(fixture.card, Rectangle(189, 20, 1, 1), fixture.outer)
        val image = onNodeWithTag("outer").captureToImage()
        assertEquals(Color.RED.rgb, image.getRGB(painted.x, painted.y), name)

        val grown = fixture.repaintOfDirtyPixel()

        assertEquals(Rectangle(0, 0, 201, 60), grown, "$name: the repaint grows to all of the mirrored card")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("stepsRepaintingWithinTheirOutsets")
    fun aContainerRepaintGrowsOnlyByTheOutsetsOfAChildWhoseStepsRepaintWithinThem(
        name: String,
        decorate: (SwingModifier) -> SwingModifier,
    ) = runComposeSwingTest {
        val fixture = composeShadowedCardInOuterBox(decorate)
        assertEquals(Insets(0, 0, 0, 1), fixture.card.paintOutsets, "$name: the card paints past its layout bounds")
        val image = onNodeWithTag("outer").captureToImage()
        assertEquals(Color.RED.rgb, image.getRGB(fixture.dirty.x, fixture.dirty.y), "$name: the leaf's pixel stays put")

        val grown = fixture.repaintOfDirtyPixel()

        assertEquals(Rectangle(10, 20, 2, 1), grown, "$name: the repaint grows by the card's outsets only")
    }

    /** [dirty] is the pixel the card's leaf draws, in [outer]'s coordinates. */
    private class Fixture(
        val outer: ConstrainedPanel,
        val card: ConstrainedPanel,
        val dirty: Rectangle,
    ) {
        fun repaintOfDirtyPixel(): Rectangle {
            var grown: Rectangle? = null
            outer.decoration.paintImmediately(
                outer,
                dirty.x,
                dirty.y,
                dirty.width,
                dirty.height,
            ) { x, y, width, height ->
                grown = Rectangle(x, y, width, height)
            }
            return checkNotNull(grown)
        }
    }

    private suspend fun ComposeSwingTest.composeShadowedCardInOuterBox(
        decorate: (SwingModifier) -> SwingModifier,
    ): Fixture {
        setContent {
            Row {
                Box(modifier = SwingModifier.size(240, 100).testTag("outer").background(Brush.of(Color.WHITE))) {
                    Box(
                        modifier =
                            decorate(
                                SwingModifier.size(200, 60).testTag("card").shadow(0, Color.BLACK, offsetX = 1),
                            ),
                    ) {
                        Canvas(modifier = SwingModifier.size(200, 60).testTag("leaf")) {
                            drawRect(Color.RED, x = 10f, y = 20f, width = 1f, height = 1f)
                        }
                    }
                }
            }
        }
        awaitIdle()
        val outer = onNodeWithTag("outer").fetch<ConstrainedPanel>()
        val leaf = onNodeWithTag("leaf").fetch<JComponent>()
        return Fixture(
            outer,
            onNodeWithTag("card").fetch<ConstrainedPanel>(),
            SwingUtilities.convertRectangle(leaf, Rectangle(10, 20, 1, 1), outer),
        )
    }

    @Test
    fun aRepaintOverAChildWithADefaultDecoratorAndNoOutsetsCoversTheChild() =
        runComposeSwingTest {
            setContent {
                Row {
                    Box(modifier = SwingModifier.size(240, 100).testTag("outer")) {
                        Box(modifier = SwingModifier.size(200, 60).testTag("card").decoration(Mirror)) {
                            Canvas(modifier = SwingModifier.size(200, 60)) { drawRect(Color.RED) }
                        }
                    }
                }
            }
            awaitIdle()
            val outer = onNodeWithTag("outer").fetch<ConstrainedPanel>()
            val fixture = Fixture(outer, onNodeWithTag("card").fetch<ConstrainedPanel>(), Rectangle(10, 20, 1, 1))

            assertEquals(Rectangle(0, 0, 200, 60), fixture.repaintOfDirtyPixel())
        }

    @Test
    fun aDrawBehindClipDoesNotClipLeafContent() =
        runComposeSwingTest {
            setContent {
                Box {
                    Canvas(
                        modifier =
                            SwingModifier
                                .size(20, 20)
                                .testTag("leaf")
                                .drawBehind { graphics.clipRect(0, 0, 1, 1) },
                    ) {
                        drawRect(Color.RED)
                    }
                }
            }

            val image = onNodeWithTag("leaf").captureToImage()
            assertEquals(Color.RED.rgb, image.getRGB(10, 10))
        }

    private object Mirror : Decorator {
        override fun paint(
            graphics: Graphics2D,
            width: Int,
            height: Int,
            content: (Graphics2D, Int, Int) -> Unit,
        ) {
            graphics.translate(width, 0)
            graphics.scale(-1.0, 1.0)
            content(graphics, width, height)
        }

        override fun paintBounds(
            content: Shape,
            width: Int,
            height: Int,
        ): Shape = AffineTransform(-1.0, 0.0, 0.0, 1.0, width.toDouble(), 0.0).createTransformedShape(content)
    }

    private data object Unmoving : Decorator {
        override val repaintsWithinOutsets: Boolean get() = true

        override fun paint(
            graphics: Graphics2D,
            width: Int,
            height: Int,
            content: (Graphics2D, Int, Int) -> Unit,
        ) = content(graphics, width, height)
    }

    private class UnmovingStep : SwingModifier.NodeElement<Component, UnmovingNode>() {
        override val additive: Boolean get() = true

        override val targetType: Class<Component> get() = Component::class.java

        override fun create(): UnmovingNode = UnmovingNode()

        override fun update(node: UnmovingNode) = Unit

        override fun equals(other: Any?): Boolean = other is UnmovingStep

        override fun hashCode(): Int = 0
    }

    private class UnmovingNode : DecorationModifierNode<Component>() {
        override val repaintsWithinOutsets: Boolean get() = true

        override fun paint(
            graphics: Graphics2D,
            width: Int,
            height: Int,
            content: (Graphics2D, Int, Int) -> Unit,
        ) = content(graphics, width, height)
    }

    companion object {
        @JvmStatic
        fun stepsRepaintingWithinTheirOutsets(): Stream<Arguments> =
            Stream.of(
                Arguments.of("decoration", { modifier: SwingModifier -> modifier.decoration(Unmoving) }),
                Arguments.of("node", { modifier: SwingModifier -> modifier.then(UnmovingStep()) }),
                Arguments.of("drawBehind", ::mirroredBehind),
            )

        private fun mirroredBehind(modifier: SwingModifier): SwingModifier =
            modifier.drawBehind {
                graphics.translate(width, 0)
                graphics.scale(-1.0, 1.0)
            }

        @JvmStatic
        fun steps(): Stream<Arguments> =
            Stream.of(
                Arguments.of("decoration", { modifier: SwingModifier -> modifier.decoration(Mirror) }),
                Arguments.of(
                    "drawWithContent",
                    { modifier: SwingModifier ->
                        modifier.drawWithContent {
                            scale(-1f, 1f) { this@drawWithContent.drawContent() }
                        }
                    },
                ),
            )
    }
}
