package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.foundation.graphics.Brush
import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.graphics.DecoratedPanel
import org.jetbrains.compose.swing.foundation.graphics.Decoration
import org.jetbrains.compose.swing.foundation.graphics.RectangleShape
import org.jetbrains.compose.swing.foundation.graphics.Shape
import org.jetbrains.compose.swing.foundation.graphics.alpha
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.graphics.clip
import org.jetbrains.compose.swing.foundation.graphics.layoutBounds
import org.jetbrains.compose.swing.foundation.graphics.paintOutsets
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.foundation.graphics.spill
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.test.screenshot.differingPixelBounds
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Color
import java.awt.Graphics
import java.awt.Insets
import java.awt.Rectangle
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.util.stream.Stream
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.border.LineBorder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.compose.swing.modifier.appearance.background as componentBackground

/** The ground a container's paint outsets show, the opacity it reports, and what a clip or a fade keeps out of them. */
class PaintOutsetsPaintingInFoundationTest {
    @Test
    fun aPanelLeftUnsetLeavesItsOutsetsUnpaintedAndReportsNotOpaque() =
        runComposeSwingTest {
            setContent {
                Row {
                    SwingNode(
                        factory = { DecoratedPanel() },
                        modifier =
                            SwingModifier
                                .testTag("card")
                                .preferredSize(20, 20)
                                .spill(Insets(4, 4, 4, 4)),
                    )
                }
            }
            val card = onAllNodesOfType<JComponent>().filterToOne(SwingMatcher.hasTestTag("card"))
            card
                .assertProperty(true, "an opaque chain reserves outsets") { paintOutsets.left > 0 }
                .assertProperty(false, "a panel its look and feel made opaque shows what is behind its outsets") {
                    isOpaque
                }
            val panel = card.fetch()
            panel.updateUI()
            card.assertProperty(false, "and a look and feel update leaves it unset") { isOpaque }

            val image = onNodeWithTag("card").captureToImage()
            val outsets = panel.paintOutsets
            val layout = Rectangle(outsets.left, outsets.top, 20, 20)
            val overBlack = image.over(Color.BLACK)
            val overWhite = image.over(Color.WHITE)
            assertEquals(
                Rectangle(0, 0, image.width, image.height),
                differingPixelBounds(overBlack, overWhite),
                "the outsets shows the ground",
            )
            assertImagesPixelPerfect(
                overBlack.getSubimage(layout.x, layout.y, layout.width, layout.height),
                overWhite.getSubimage(layout.x, layout.y, layout.width, layout.height),
            )
        }

    @Test
    fun aDelegatePaintedLabelMatchesAPlainLabelMovedByTheOutsets() =
        runComposeSwingTest {
            setContent {
                Row {
                    SwingNode(
                        factory = { DecoratedLabel().apply { styled() } },
                        modifier = SwingModifier.testTag("card").shadow(6, Color.BLACK),
                    )
                }
            }
            val label = onNodeWithTag("card").fetch<JComponent>()
            val outsets = label.paintOutsets
            val layout = label.layoutBounds
            val reference = JLabel().apply { styled() }.apply { size = layout.size }.captureToImage()

            val image = onNodeWithTag("card").captureToImage()
            assertImagesPixelPerfect(
                reference,
                image.getSubimage(outsets.left, outsets.top, layout.width, layout.height),
            )
        }

    @Test
    fun anOpaqueContainerFillsOnlyItsLayoutBoundsAndLeavesAChildsShadowOverItsParent() =
        runComposeSwingTest {
            setContent {
                Row {
                    Box(modifier = SwingModifier.testTag("card").opaque(true).componentBackground(Color.WHITE)) {
                        Box(
                            modifier =
                                SwingModifier
                                    .size(20, 20)
                                    .shadow(6, Color.BLACK)
                                    .background(Brush.of(Color.BLUE)),
                        )
                    }
                }
            }
            val card = onAllNodesOfType<JComponent>().filterToOne(SwingMatcher.hasTestTag("card"))
            val box = card.fetch()
            val outsets = box.paintOutsets
            card.assertProperty(true, "the child's shadow spills into the box") { paintOutsets.left > 0 }
            card.assertProperty(false, "the parent shows under the shadow, so a box set opaque reports not opaque") {
                isOpaque
            }

            val image = card.captureToImage()
            val layout = box.layoutBounds
            assertImagesPixelPerfect(
                image.over(Color.BLACK).getSubimage(outsets.left, outsets.top, layout.width, layout.height),
                image.over(Color.WHITE).getSubimage(outsets.left, outsets.top, layout.width, layout.height),
            )
            assertTrue(image.getRGB(0, 0) ushr 24 < 0xFF, "the box leaves the outsets unfilled")
        }

    @Test
    fun aFadedPanelLinkedToItsBoxStaysTranslucent() =
        runComposeSwingTest {
            setContent {
                Box {
                    SwingNode(
                        factory = { DecoratedPanel() },
                        modifier = SwingModifier.testTag("faded").preferredSize(20, 20).alpha(0.5f),
                    )
                }
            }

            onNodeWithTag("faded").assertProperty(false, "the Box's link keeps the fade's opacity") { isOpaque }
        }

    // A clip cuts the spill to the clip's own outline, which a shape wider than the box and a layout modifier after
    // the clip both reach past. A fade keeps it out altogether.
    @ParameterizedTest
    @MethodSource("shadowedChildContainers")
    fun aContainersPaintOutsetsAroundAShadowedChildFollowWhatItsModifiersKeepOut(case: ShadowedChildCase) =
        runComposeSwingTest {
            setContent {
                Row {
                    Box(modifier = SwingModifier.testTag("reference")) {
                        Box(modifier = SwingModifier.size(20, 20).shadow(case.shadow, Color.BLACK))
                    }
                    Box(
                        modifier =
                            SwingModifier
                                .testTag("container")
                                .then(case.modifier)
                                .then(if (case.padding > 0) SwingModifier.padding(case.padding) else SwingModifier),
                    ) {
                        Box(modifier = SwingModifier.size(20, 20).shadow(case.shadow, Color.BLACK))
                    }
                }
            }

            onAllNodesOfType<JComponent>()
                .filterToOne(SwingMatcher.hasTestTag("reference"))
                .assertProperty(true, "the shadow spills into a container nothing clips") { paintOutsets.left > 0 }
            onAllNodesOfType<JComponent>()
                .filterToOne(SwingMatcher.hasTestTag("container"))
                .assertProperty(case.expected, case.name) { paintOutsets }
        }

    class ShadowedChildCase(
        val name: String,
        val modifier: SwingModifier,
        val padding: Int = 0,
        val shadow: Int,
        val expected: Insets,
    )

    private class DecoratedLabel :
        JLabel(),
        Decoratable {
        override var decoration: Decoration = Decoration.None

        override fun getGraphics(): Graphics? = decoration.getGraphics(this, super.getGraphics())

        override fun paint(g: Graphics) = decoration.paint(this, g) { super.paint(it) }

        override fun paintComponent(g: Graphics) {
            if (super.isOpaque()) {
                val box = decoration.localLayoutBounds(this)
                g.color = background
                g.fillRect(box.x, box.y, box.width, box.height)
            }
            val scratch = g.create()
            try {
                ui?.paint(scratch, this)
            } finally {
                scratch.dispose()
            }
        }

        override fun paintBorder(g: Graphics) {
            val box = decoration.localLayoutBounds(this)
            border?.paintBorder(this, g, box.x, box.y, box.width, box.height)
        }

        override fun getInsets(): Insets = decoration.insets(super.getInsets())

        override fun getInsets(insets: Insets?): Insets =
            decoration.insets(super.getInsets(insets), insets ?: Insets(0, 0, 0, 0))

        override fun contains(
            x: Int,
            y: Int,
        ): Boolean = decoration.contains(this, x, y)

        override fun isOpaque(): Boolean = super.isOpaque() && decoration.isOpaque(this)
    }

    private companion object {
        @JvmStatic
        fun shadowedChildContainers(): Stream<Arguments> {
            val wide = Shape { width, height -> Rectangle2D.Float(-10f, -10f, width + 20f, height + 20f) }
            return Stream
                .of(
                    ShadowedChildCase(
                        "a clip keeps the child's shadow out of the container's outsets",
                        SwingModifier.clip(RectangleShape),
                        shadow = 6,
                        expected = Insets(0, 0, 0, 0),
                    ),
                    ShadowedChildCase(
                        "a fade keeps the child's shadow out of the container's outsets",
                        SwingModifier.alpha(0.5f),
                        shadow = 6,
                        expected = Insets(0, 0, 0, 0),
                    ),
                    ShadowedChildCase(
                        "a clip to a shape wider than its box reserves what spills inside the shape",
                        SwingModifier.clip(wide),
                        shadow = 4,
                        expected = Insets(10, 10, 10, 10),
                    ),
                    ShadowedChildCase(
                        "a clip before a layout modifier reserves the outline it paints, not the padding box",
                        SwingModifier.clip(wide),
                        padding = 2,
                        shadow = 4,
                        expected = Insets(12, 12, 12, 12),
                    ),
                ).map { Arguments.of(Named.of(it.name, it)) }
        }

        fun JLabel.styled() {
            text = "Label"
            isOpaque = true
            background = Color.YELLOW
            border = LineBorder(Color.RED, 2)
        }

        fun BufferedImage.over(ground: Color): BufferedImage {
            val result = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            val graphics = result.createGraphics()
            try {
                graphics.color = ground
                graphics.fillRect(0, 0, width, height)
                graphics.drawImage(this, 0, 0, null)
            } finally {
                graphics.dispose()
            }
            return result
        }
    }
}
