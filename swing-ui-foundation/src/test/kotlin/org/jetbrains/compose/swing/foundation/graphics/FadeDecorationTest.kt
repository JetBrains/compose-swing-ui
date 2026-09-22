package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Insets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Behavioral tests for [SwingModifier.alpha]: what a fade paints and how it composes. */
class FadeDecorationTest {
    @Test
    fun aFadePaintsTheContentTranslucently() =
        runComposeSwingTest {
            val painted = stage().paint({ SwingModifier.alpha(0.5f) }, fill(Color.RED))

            assertEquals(
                Color.RED.rgb,
                painted.getRGB(5, 5) or 0xFF000000.toInt(),
                "A fade changes the coverage, not the color.",
            )
            assertTrue(painted.opacityAt(5, 5) in 127..128, "Half opacity should cover half.")
        }

    @Test
    fun aFadeReachesEachDrawingRatherThanTheAreaAsOne() =
        runComposeSwingTest {
            val painted =
                stage().paint({ SwingModifier.alpha(0.5f) }) { graphics, _, _ ->
                    graphics.fill(Color.RED, 0, 0, 20, 20)
                    graphics.fill(Color.BLUE, 10, 10, 20, 20)
                }

            assertTrue(
                painted.opacityAt(15, 15) > painted.opacityAt(5, 5),
                "Overlapping shapes are each faded as they are drawn, so the second covers more where it " +
                    "lands on the first. Fading the area as one image would have covered both the same.",
            )
        }

    @Test
    fun nestedFadesMultiply() =
        runComposeSwingTest {
            val painted = stage().paint({ SwingModifier.alpha(0.5f).alpha(0.5f) }, fill(Color.RED))

            assertTrue(painted.opacityAt(5, 5) in 63..64, "A fade inside a fade paints at the product of the two.")
        }

    @Test
    fun aFadeKeepsTheCompositeRuleItIsPaintedUnder() =
        runComposeSwingTest {
            val painted =
                stage().print(
                    { SwingModifier.alpha(0.5f) },
                    fill(Color.RED),
                    setUp = { graphics ->
                        graphics.fill(Color.BLACK, 0, 0, STAGE_SIZE, STAGE_SIZE)
                        graphics.composite = AlphaComposite.DstOut
                    },
                )

            assertTrue(
                painted.opacityAt(5, 5) in 127..128,
                "A half fade under DstOut clears half of what is there, rather than painting over it.",
            )
        }

    @Test
    fun aFadeToNothingPaintsNothing() =
        runComposeSwingTest {
            val stage = stage()
            val empty = stage.paint(content = PaintsNothing)

            assertImagesPixelPerfect(empty, stage.paint({ SwingModifier.alpha(0f) }, fill(Color.RED)))
            val faded = stage.declare({ SwingModifier.alpha(0f) })
            assertFalse(
                faded.decoration.isOpaque(faded),
                "What is behind an invisible component shows through it.",
            )
        }

    @Test
    fun aFadeRaisedToFullAlphaPaintsAndCoversAsNoFadeDoes() =
        runComposeSwingTest {
            val stage = stage()
            val unfaded = stage.paint(content = fill(Color.RED))
            stage.paint({ SwingModifier.alpha(0.5f) }, fill(Color.RED))

            assertImagesPixelPerfect(unfaded, stage.paint({ SwingModifier.alpha(1f) }, fill(Color.RED)))
            val full = stage.declare({ SwingModifier.alpha(1f).background(Brush.of(Color.BLUE)) })
            assertTrue(full.decoration.isOpaque(full), "A full alpha hides what is behind as the background does.")
        }

    @Test
    fun aFadeOutsideTheRangePaintsAtTheNearerEnd() =
        runComposeSwingTest {
            val stage = stage()

            assertImagesPixelPerfect(
                stage.paint(content = fill(Color.RED)),
                stage.paint({
                    SwingModifier.alpha(1.5f)
                }, fill(Color.RED)),
            )
            assertImagesPixelPerfect(
                stage.paint(content = PaintsNothing),
                stage.paint({
                    SwingModifier.alpha(-1f)
                }, fill(Color.RED)),
            )
            assertImagesPixelPerfect(
                stage.paint(content = PaintsNothing),
                stage.paint({
                    SwingModifier.alpha(Float.NaN)
                }, fill(Color.RED)),
            )
        }

    @Test
    fun aFadeCutsWhatItsContentPaintsPastTheArea() =
        runComposeSwingTest {
            val stage = stage()
            val outsets = ReservingElement(Insets(STAGE_OUTSETS, STAGE_OUTSETS, STAGE_OUTSETS, STAGE_OUTSETS))
            val overflowing: (Graphics2D, Int, Int) -> Unit = { graphics, width, height ->
                graphics.fill(
                    Color.RED,
                    -STAGE_OUTSETS,
                    -STAGE_OUTSETS,
                    width + 2 * STAGE_OUTSETS,
                    height + 2 * STAGE_OUTSETS,
                )
            }
            val unfaded = stage.paint({ SwingModifier.decoration(outsets) }, overflowing)
            val faded = stage.paint({ SwingModifier.decoration(outsets).alpha(1.5f) }, overflowing)

            assertEquals(
                0xFF,
                unfaded.opacityAt(STAGE_OUTSETS / 2, STAGE_SIZE / 2),
                "Without a fade the content paints into the outsets.",
            )
            assertEquals(
                0,
                faded.opacityAt(STAGE_OUTSETS / 2, STAGE_SIZE / 2),
                "A fade cuts the content to the area, as androidx's does.",
            )
            assertEquals(
                0xFF,
                faded.opacityAt(STAGE_SIZE / 2, STAGE_SIZE / 2),
                "Inside the area the content still paints.",
            )
        }

    @Test
    fun aFadeAndAClipStopCoveringTheArea() =
        runComposeSwingTest {
            val stage = stage()

            suspend fun isOpaque(chain: () -> SwingModifier): Boolean {
                val panel = stage.declare(chain)
                return panel.decoration.isOpaque(panel)
            }

            assertTrue(isOpaque { SwingModifier }, "No steps paint nothing and hide nothing.")
            assertTrue(
                isOpaque { SwingModifier.background(Brush.of(Color.BLUE)) },
                "A background fills the whole area.",
            )
            assertFalse(isOpaque { SwingModifier.alpha(0.5f) }, "What is behind a faded component shows through it.")
            assertFalse(
                isOpaque { SwingModifier.clip(CircleShape) },
                "A clip leaves the corners of the area unpainted.",
            )
            assertFalse(isOpaque { SwingModifier.blur(8) }, "A blur fades out at the edges of the area.")
            assertFalse(
                isOpaque { SwingModifier.clip(CircleShape).background(Brush.of(Color.BLUE)) },
                "One step that stops covering the area is enough for them all.",
            )
        }
}
