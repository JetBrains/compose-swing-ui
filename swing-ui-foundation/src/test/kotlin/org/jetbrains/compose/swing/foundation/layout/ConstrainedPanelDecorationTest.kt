package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.foundation.graphics.Brush
import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.graphics.DecoratedBox
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.graphics.blur
import org.jetbrains.compose.swing.foundation.graphics.blurOutsets
import org.jetbrains.compose.swing.foundation.graphics.drawBehind
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.foundation.graphics.spill
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Color
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Insets
import java.awt.Rectangle
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.jetbrains.compose.swing.modifier.appearance.background as swingBackground

/**
 * [Row], [Column] and [Box] are decorated components: each paints the decoration its modifier declares under the
 * same snapshot observer its policy measures and places under.
 */
class ConstrainedPanelDecorationTest {
    @Test
    fun anOpaqueBoxPaintsItsSwingBackgroundOnTheInitialPass() =
        runComposeSwingTest {
            setContent {
                Box(
                    modifier =
                        SwingModifier
                            .testTag("box")
                            .preferredSize(Dimension(64, 48))
                            .opaque(true)
                            .swingBackground(Color.RED),
                )
            }

            val painted = onNodeWithTag("box").captureToImage()

            assertEquals(
                Color.RED.rgb,
                painted.getRGB(32, 24),
                "an opaque constrained panel must paint its Swing background on the initial pass",
            )
        }

    /** A box set opaque fills its layout bounds with its Swing background under a decoration that is not opaque. */
    @Test
    fun anOpaqueBoxPaintsItsSwingBackgroundInsideAClip() =
        runComposeSwingTest {
            setContent {
                Box {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag("box")
                                .preferredSize(Dimension(64, 48))
                                .opaque(true)
                                .swingBackground(Color.RED)
                                .clipToBounds(),
                    )
                }
            }

            val painted = onNodeWithTag("box").captureToImage()

            assertEquals(Color.RED.rgb, painted.getRGB(32, 24), "the clip keeps the background inside the box")
        }

    @Test
    fun aBoxPaintsThroughTheDecorationItsModifierDeclares() =
        runComposeSwingTest {
            setContent {
                DecoratedBox {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag("box")
                                .preferredSize(Dimension(64, 48))
                                .background(Brush.of(Color.BLUE)),
                    )
                }
            }

            val painted = onNodeWithTag("box").captureToImage()

            assertEquals(
                Color.BLUE.rgb,
                painted.getRGB(32, 24),
                "the background the modifier declares must fill the box, the same way it fills any other " +
                    "Decoratable",
            )
        }

    /**
     * A decoration needs no `Row`, `Column` or `Box` in reach to resolve: it is a plain `SwingModifier`
     * extension, so it paints on a `Box` composed directly in a swing-ui `Panel`, which offers none of those.
     */
    @Test
    fun aBoxInsideAPanelPaintsThroughTheDecorationItsModifierDeclares() =
        runComposeSwingTest {
            setContent {
                Panel {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag("box")
                                .preferredSize(Dimension(64, 48))
                                .background(Brush.of(Color.BLUE)),
                    )
                }
            }

            val painted = onNodeWithTag("box").captureToImage()

            assertEquals(
                Color.BLUE.rgb,
                painted.getRGB(32, 24),
                "the background the modifier declares must fill the box even though the Panel around it offers " +
                    "no Row, Column or Box scope",
            )
        }

    /** A child repainting itself under a blurred box repaints what the blur spreads its change into. */
    @Test
    fun aChildRepaintingItselfRepaintsTheBlurAroundIt() = assertChildRepaintCoversTheOutsets { it.blur(6) }

    /** A child repainting itself under a shadowed box repaints the shadow its change casts. */
    @Test
    fun aChildRepaintingItselfRepaintsTheShadowItCasts() =
        assertChildRepaintCoversTheOutsets { it.shadow(8, Color(0, 0, 0, 160), offsetX = 4, offsetY = 4) }

    /**
     * Composes a box decorated by [decorate] around a centered child, repaints the child alone, and checks that the
     * box repainted the child's area grown by its decoration's outsets, in the box's layout coordinates, where the
     * decoration paints. The box is large enough that the grown area stays inside it.
     */
    private fun assertChildRepaintCoversTheOutsets(decorate: (SwingModifier) -> SwingModifier) =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val repainted = ArrayList<Rectangle>()
            setWindowContent {
                Box {
                    Box(
                        modifier =
                            decorate(
                                SwingModifier
                                    .testTag("box")
                                    .size(160, 160)
                                    .drawBehind { graphics.clipBounds?.let { repainted += it } },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(modifier = SwingModifier.testTag("child").size(40, 40)) {}
                    }
                }
            }
            val window = onWindowWithTitle(WINDOW_TITLE)
            val box = window.onNodeWithTag("box").fetch<JComponent>()
            val decoration = (box as Decoratable).decoration
            val outsets = decoration.steps.outsets
            val layoutBounds = decoration.localLayoutBounds(box)
            val child = window.onNodeWithTag("child").fetch<JComponent>()
            assertTrue(outsets.left > 0 && outsets.bottom > 0, "the decoration must spread past the child: $outsets")
            awaitIdle()
            repainted.clear()

            child.repaint()
            awaitIdle()

            val reach =
                Rectangle(
                    child.x - layoutBounds.x - outsets.left,
                    child.y - layoutBounds.y - outsets.top,
                    child.width + outsets.left + outsets.right,
                    child.height + outsets.top + outsets.bottom,
                )
            assertTrue(repainted.any { it.contains(reach) }, "the child's repaint must reach $reach: $repainted")
        }

    @Test
    fun aSizeSetOnABoxAnswersAsSetWhileTheBoundsItIsGivenGrowByTheDecorationsOutsets() =
        runComposeSwingTest {
            setContent {
                Box {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag("box")
                                .preferredSize(Dimension(64, 48))
                                .shadow(radius = 8, color = Color.BLACK),
                    )
                }
            }

            val box = onNodeWithTag("box").fetch<JComponent>()

            assertEquals(Dimension(64, 48), box.preferredSize, "a size set on the box answers as set")
            assertEquals(
                Dimension(64 + 2 * blurOutsets(8), 48 + 2 * blurOutsets(8)),
                box.size,
                "the bounds its Foundation parent gives it grow by the outsets the shadow needs around the content",
            )
        }

    @Test
    fun aBoxFillsTheInsetsItIsHandedWithItsPaintOutsets() =
        runComposeSwingTest {
            setContent {
                Box {
                    Box(
                        modifier =
                            SwingModifier.testTag("box").preferredSize(Dimension(64, 48)).spill(Insets(8, 8, 8, 8)),
                    )
                }
            }
            val reused = Insets(1, 1, 1, 1)

            val answered = onNodeWithTag("box").fetch<JComponent>().getInsets(reused)

            assertSame(reused, answered, "the box must answer with the insets it is handed")
            assertEquals(Insets(8, 8, 8, 8), reused, "the paint outsets must be filled in")
        }
}
