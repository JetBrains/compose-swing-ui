// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.graphics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.KeyReadingElement
import org.jetbrains.compose.swing.assertPixelColor
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.style.opaque
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Color
import java.awt.Dimension
import java.awt.Insets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
                                .shadow(radius, color, offsetX = offset, offsetY = offset)
                                .background(Brush.of(Color.BLUE)),
                        style = {
                            opaque(false)
                            preferredSize(Dimension(64, 64))
                        },
                    )
                }
            }
            onNodeWithTag("decorated-panel").captureToImage()

            radius = 6
            color = Color.GREEN
            offset = 3
            awaitIdle()

            val reach = blurOutsets(6)
            onNodeWithTag("decorated-panel").assertProperty(
                Insets(reach - 3, reach - 3, reach + 3, reach + 3),
                "The outsets follows the new radius and offsets.",
            ) { assertIs<Decoratable>(this).decoration.paintOutsets() }
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
                                .then(keyed)
                                .background(Brush.of(color)),
                        style = { preferredSize(Dimension(64, 64)) },
                    )
                }
            }
            onNodeWithTag("decorated-panel").captureToImage()
            val keyReads = keyed.keyReads

            color = Color.BLUE
            awaitIdle()

            onNodeWithTag(
                "decorated-panel",
            ).captureToImage().assertPixelColor(Color.BLUE, 32, 32, "The new brush paints.")
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
                                .then(keyed)
                                .shadow(radius, Color.RED)
                                .background(Brush.of(Color.BLUE)),
                        style = { preferredSize(Dimension(64, 64)) },
                    )
                }
            }
            onNodeWithTag("decorated-panel").captureToImage()
            val keyReads = keyed.keyReads

            radius = 6
            awaitIdle()

            val reach = blurOutsets(6)
            onNodeWithTag("decorated-panel").assertProperty(
                Insets(reach, reach, reach, reach),
                "The decoration takes the new radius's outsets.",
            ) { assertIs<Decoratable>(this).decoration.paintOutsets() }
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
                                .blur(radius)
                                .background(Brush.of(Color.BLUE)),
                        style = { preferredSize(Dimension(64, 64)) },
                    )
                }
            }
            onNodeWithTag("decorated-panel").captureToImage()

            radius = 6
            awaitIdle()

            val reach = blurOutsets(6)
            onNodeWithTag("decorated-panel").assertProperty(
                Insets(reach, reach, reach, reach),
                "The outsets follows the new radius.",
            ) { assertIs<Decoratable>(this).decoration.paintOutsets() }
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
                                .clip(shape, antialias)
                                .background(Brush.of(Color.BLUE)),
                        style = {
                            opaque(false)
                            preferredSize(Dimension(64, 64))
                        },
                    )
                }
            }
            onNodeWithTag("decorated-panel").captureToImage()

            shape = CircleShape
            antialias = true
            awaitIdle()

            val painted = onNodeWithTag("decorated-panel").captureToImage()
            painted.assertPixelColor(Color(0, true), 0, 0, "The corner lies outside the new circle and is cut away.")
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
                                .shadow(radius, color, offsetX = offset, offsetY = offset)
                                .clip(CircleShape)
                                .background(Brush.of(Color.BLUE)),
                        style = {
                            opaque(false)
                            preferredSize(Dimension(64, 64))
                        },
                    )
                    DecoratedBox(
                        modifier =
                            SwingModifier
                                .testTag("declared")
                                .shadow(6, Color.GREEN, offsetX = 3, offsetY = 3)
                                .clip(CircleShape)
                                .background(Brush.of(Color.BLUE)),
                        style = {
                            opaque(false)
                            preferredSize(Dimension(64, 64))
                        },
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
