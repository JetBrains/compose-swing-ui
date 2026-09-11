package org.jetbrains.compose.swing.foundation.graphics

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImageAgainstGoldenPixelPerfect
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import kotlin.test.Test

/**
 * Pins what each decoration paints against a committed golden image, pixel for pixel.
 *
 * A decoration rasterizes through Java2D alone - shapes, fills and image filters over an authored
 * figure - so every host produces the same pixels and an exact match is the right comparison. The
 * scenes carry no text and no look-and-feel chrome, the two things whose rendering a host decides for
 * itself, and each paints an opaque background so the capture holds no partial transparency for a PNG
 * round trip to round differently.
 *
 * Re-record after a deliberate change with
 * `./gradlew :swing-ui-foundation:test -DSCREENSHOT_TEST_UPDATE_GOLDENS=true`, and review the diff written to
 * `build/screenshot-test-results` before accepting it.
 */
class DecorationGoldenTest {
    @Test
    fun aClipCutsTheFigureToItsShape() = golden("decoration_clip", ::filled) { clip(CircleShape) }

    @Test
    fun anAntialiasedClipCutsTheSameShapeSoftly() =
        golden("decoration_clip_antialiased", ::filled) { clip(CircleShape, antialias = true) }

    @Test
    fun aBackgroundFillsWhatTheFigureLeavesBare() =
        golden("decoration_background", ::sparse) { background(Brush.of(Color(0x44, 0x4C, 0x56))) }

    @Test
    fun aGradientBackgroundFillsWhatTheFigureLeavesBare() =
        golden("decoration_background_gradient", ::gradientFigure) {
            background(Brush.horizontalGradient(0f to Color(0x18, 0x4E, 0x9C), 1f to Color(0xF4, 0xC4, 0x30)))
        }

    @Test
    fun aBorderRingsTheFigure() = golden("decoration_border", ::filled) { border(4, Color(0x44, 0x4C, 0x56)) }

    @Test
    fun anAlphaFadesTheFigure() = golden("decoration_alpha", ::filled) { alpha(0.4f) }

    /**
     * Captures a scene holding one decorated [figure] on an opaque ground and asserts it against the
     * golden image named [goldenIdentifier].
     */
    private fun golden(
        goldenIdentifier: String,
        figure: (Graphics2D, Int, Int) -> Unit,
        decorate: SwingModifier.() -> SwingModifier,
    ) = runComposeSwingTest {
        setContent { Scene(figure, decorate) }
        onNodeWithTag(SCENE).assertImageAgainstGoldenPixelPerfect(goldenIdentifier)
    }

    @Composable
    private fun Scene(
        figure: (Graphics2D, Int, Int) -> Unit,
        decorate: SwingModifier.() -> SwingModifier,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                decorated {
                    SwingModifier
                        .testTag(SCENE)
                        .preferredSize(96, 96)
                        .background(Color.WHITE, RectangleShape)
                },
        ) {
            Canvas(
                modifier = SwingModifier.preferredSize(48, 48).decorate(),
                onDraw = figure,
            )
        }
    }

    private companion object {
        const val SCENE = "decorated-scene"

        /** Fills the whole area, so a decoration that cuts, rings or moves the figure has an edge to act on. */
        fun filled(
            graphics: Graphics2D,
            width: Int,
            height: Int,
        ) {
            graphics.color = Color(0x1F, 0x3A, 0x93)
            graphics.fillRect(0, 0, width, height)
            graphics.drawAccent(width, height)
        }

        /** Leaves the corners bare, so a decoration painting under the figure shows through them. */
        fun sparse(
            graphics: Graphics2D,
            width: Int,
            height: Int,
        ) {
            graphics.drawAccent(width, height)
        }

        /** Keeps the gradient visible around a smaller, distinct figure. */
        fun gradientFigure(
            graphics: Graphics2D,
            width: Int,
            height: Int,
        ) {
            val figureWidth = width / 2
            val figureHeight = height / 2
            graphics.color = Color(0xE0, 0x3A, 0x3A)
            graphics.fillOval((width - figureWidth) / 2, (height - figureHeight) / 2, figureWidth, figureHeight)
        }

        private fun Graphics2D.drawAccent(
            width: Int,
            height: Int,
        ) {
            setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            color = Color(0xE0, 0x7A, 0x1F)
            fillOval(0, 0, width, height)
        }
    }
}
