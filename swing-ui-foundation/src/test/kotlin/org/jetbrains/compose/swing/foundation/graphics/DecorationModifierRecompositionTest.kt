package org.jetbrains.compose.swing.foundation.graphics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.KeyReadingElement
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Color
import java.awt.Dimension
import java.awt.Insets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Behavioral tests for what a recomposed decoration shortcut does: reads the new values in place, or rebuilds. */
class DecorationModifierRecompositionTest {
    @Test
    fun aShadowRecomposedWithNewValuesCastsThem() =
        runComposeSwingTest {
            var radius by mutableIntStateOf(4)
            var color by mutableStateOf(Color.RED)
            var offset by mutableIntStateOf(0)
            setContent {
                Box {
                    DecoratedBox(
                        modifier =
                            SwingModifier
                                .testTag("decorated-panel")
                                .preferredSize(Dimension(64, 64))
                                .opaque(false)
                                .shadow(radius, color, offsetX = offset, offsetY = offset)
                                .background(Brush.of(Color.BLUE)),
                    )
                }
            }
            onNodeWithTag("decorated-panel").captureToImage()

            radius = 6
            color = Color.GREEN
            offset = 3
            awaitIdle()

            val reach = blurOutsets(6)
            assertEquals(
                Insets(reach - 3, reach - 3, reach + 3, reach + 3),
                (onNodeWithTag("decorated-panel").fetch() as Decoratable).decoration.paintOutsets(),
                "The outsets follows the new radius and offsets.",
            )
            val painted = onNodeWithTag("decorated-panel").captureToImage()
            val cast = Color(painted.getRGB(reach - 3 + 64 + 1, reach - 3 + 32), true)
            assertTrue(cast.alpha > 0 && cast.green > cast.red, "The shadow is cast in the new color: $cast")
        }

    @Test
    fun aBackgroundRecomposedWithANewBrushPaintsItWithoutDiffingTheModifier() =
        runComposeSwingTest {
            var color by mutableStateOf(Color.RED)
            val keyed = KeyReadingElement()
            setContent {
                Box {
                    DecoratedBox(
                        modifier =
                            SwingModifier
                                .testTag("decorated-panel")
                                .preferredSize(Dimension(64, 64))
                                .then(keyed)
                                .background(Brush.of(color)),
                    )
                }
            }
            onNodeWithTag("decorated-panel").captureToImage()
            val keyReads = keyed.keyReads

            color = Color.BLUE
            awaitIdle()

            assertEquals(
                Color.BLUE.rgb,
                onNodeWithTag("decorated-panel").captureToImage().getRGB(32, 32),
                "The new brush paints.",
            )
            assertEquals(keyReads, keyed.keyReads, "A new brush is written in place, leaving the modifier undiffed.")
        }

    @Test
    fun aShadowRecomposedWithANewRadiusReservesItsOutsetsWithoutDiffingTheModifier() =
        runComposeSwingTest {
            var radius by mutableIntStateOf(2)
            val keyed = KeyReadingElement()
            setContent {
                Box {
                    DecoratedBox(
                        modifier =
                            SwingModifier
                                .testTag("decorated-panel")
                                .preferredSize(Dimension(64, 64))
                                .then(keyed)
                                .shadow(radius, Color.RED)
                                .background(Brush.of(Color.BLUE)),
                    )
                }
            }
            onNodeWithTag("decorated-panel").captureToImage()
            val keyReads = keyed.keyReads

            radius = 6
            awaitIdle()

            val reach = blurOutsets(6)
            assertEquals(
                Insets(reach, reach, reach, reach),
                (onNodeWithTag("decorated-panel").fetch() as Decoratable).decoration.paintOutsets(),
                "The decoration takes the new radius's outsets.",
            )
            assertEquals(keyReads, keyed.keyReads, "A new radius is written in place, leaving the modifier undiffed.")
        }

    @Test
    fun aBlurRecomposedWithANewRadiusReservesItsOutsets() =
        runComposeSwingTest {
            var radius by mutableIntStateOf(2)
            setContent {
                Box {
                    DecoratedBox(
                        modifier =
                            SwingModifier
                                .testTag("decorated-panel")
                                .preferredSize(Dimension(64, 64))
                                .blur(radius)
                                .background(Brush.of(Color.BLUE)),
                    )
                }
            }
            onNodeWithTag("decorated-panel").captureToImage()

            radius = 6
            awaitIdle()

            val reach = blurOutsets(6)
            assertEquals(
                Insets(reach, reach, reach, reach),
                (onNodeWithTag("decorated-panel").fetch() as Decoratable).decoration.paintOutsets(),
                "The outsets follows the new radius.",
            )
        }

    @Test
    fun aClipRecomposedWithANewShapeAndAntialiasingCutsToThem() =
        runComposeSwingTest {
            var shape: Shape by mutableStateOf(RectangleShape)
            var antialias by mutableStateOf(false)
            setContent {
                Box {
                    DecoratedBox(
                        modifier =
                            SwingModifier
                                .testTag("decorated-panel")
                                .preferredSize(Dimension(64, 64))
                                .opaque(false)
                                .clip(shape, antialias)
                                .background(Brush.of(Color.BLUE)),
                    )
                }
            }
            onNodeWithTag("decorated-panel").captureToImage()

            shape = CircleShape
            antialias = true
            awaitIdle()

            val painted = onNodeWithTag("decorated-panel").captureToImage()
            assertEquals(0, painted.getRGB(0, 0), "The corner lies outside the new circle and is cut away.")
            assertTrue(
                (painted.getRGB(9, 9) ushr 24) in 1..254,
                "A pixel the circle's edge crosses is kept in part once the clip is antialiased.",
            )
        }

    @Test
    fun aShadowRecomposedWithNewValuesCastsWhatOneDeclaredWithThemCasts() =
        runComposeSwingTest {
            var radius by mutableIntStateOf(2)
            var color by mutableStateOf(Color.RED)
            var offset by mutableIntStateOf(0)
            setContent {
                Row {
                    DecoratedBox(
                        modifier =
                            SwingModifier
                                .testTag("recomposed")
                                .preferredSize(Dimension(64, 64))
                                .opaque(false)
                                .shadow(radius, color, offsetX = offset, offsetY = offset)
                                .clip(CircleShape)
                                .background(Brush.of(Color.BLUE)),
                    )
                    DecoratedBox(
                        modifier =
                            SwingModifier
                                .testTag("declared")
                                .preferredSize(Dimension(64, 64))
                                .opaque(false)
                                .shadow(6, Color.GREEN, offsetX = 3, offsetY = 3)
                                .clip(CircleShape)
                                .background(Brush.of(Color.BLUE)),
                    )
                }
            }
            onNodeWithTag("recomposed").captureToImage()

            radius = 6
            color = Color.GREEN
            offset = 3
            awaitIdle()

            assertImagesPixelPerfect(
                onNodeWithTag("declared").captureToImage(),
                onNodeWithTag("recomposed").captureToImage(),
            )
        }
}
