package org.jetbrains.compose.swing.foundation.graphics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertAskedForNoLayout
import org.jetbrains.compose.swing.assertAskedToRepaint
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.lastElement
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.withRecordedRepaints
import java.awt.Color
import java.awt.Dimension
import java.awt.Insets
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioral tests for the decoration shortcuts on [SwingModifier]: what each one declares reaches the component.
 *
 * Painting is forced against an off-screen raster, so what is asserted is what the component would show.
 */
class DecorationModifiersTest {
    @Test
    fun aClipCutsTheComponentsPainting() =
        runComposeSwingTest {
            setContent {
                Box {
                    DecoratedBox(
                        modifier =
                            SwingModifier
                                .testTag("decorated-panel")
                                .opaque(false)
                                .preferredSize(Dimension(64, 64))
                                .clip(CircleShape)
                                .background(Brush.of(Color.BLUE)),
                    ) {
                        Label(text = "child")
                    }
                }
            }

            val painted = onNodeWithTag("decorated-panel").captureToImage()

            assertEquals(Color.BLUE.rgb, painted.getRGB(32, 32), "The brush fills what the clip leaves.")
            assertEquals(0, painted.getRGB(0, 0), "A corner lies outside the circle and stays unpainted.")
        }

    @Test
    fun aBrushBackgroundIsADecorationAndAColorBackgroundIsTheProperty() =
        runComposeSwingTest {
            setContent {
                Box {
                    SwingNode(
                        factory = { DecoratedPanel() },
                        modifier = SwingModifier.testTag("decorated-panel").background(Color.RED),
                    )
                }
            }

            val panel = onNodeWithTag("decorated-panel").fetch<DecoratedPanel>()

            assertEquals(
                Color.RED,
                panel.background,
                "background(Color) writes the Swing property a look and feel honors; a Color is a Paint but " +
                    "not a Brush, so the call is not the decoration overload.",
            )
            assertFalse(panel.isPaintingOrigin(), "background(Color) declares no decoration.")
        }

    @Test
    fun theAlphaShortcutFadesTheComponentAndStopsItCoveringWhatIsBehind() =
        runComposeSwingTest {
            setContent {
                Box {
                    DecoratedBox(
                        modifier =
                            SwingModifier
                                .testTag("decorated-panel")
                                .preferredSize(Dimension(64, 64))
                                .alpha(0.5f)
                                .background(Brush.of(Color.BLUE)),
                    ) {
                        Label(text = "child")
                    }
                    DecoratedBox(
                        modifier =
                            SwingModifier
                                .testTag("reversed-panel")
                                .preferredSize(Dimension(64, 64))
                                .opaque(true)
                                .background(Brush.of(Color.BLUE)),
                    ) {
                        Label(text = "child")
                    }
                }
            }

            val faded = onNodeWithTag("decorated-panel").fetch<JComponent>()
            val painted = onNodeWithTag("decorated-panel").captureToImage()

            assertTrue(painted.getRGB(32, 32) ushr 24 in 127..128, "Half opacity should cover half.")
            assertFalse(faded.isOpaque, "A faded component lets what is behind it show through.")
            assertTrue(
                onNodeWithTag("reversed-panel").fetch<JComponent>().isOpaque,
                "A decoration that keeps covering the area leaves the component opaque.",
            )
        }

    @Test
    fun theBorderShortcutStrokesTheComponentsEdgeWithoutReservingIt() =
        runComposeSwingTest {
            setContent {
                Box {
                    DecoratedBox(
                        modifier =
                            SwingModifier
                                .testTag("decorated-panel")
                                .preferredSize(Dimension(64, 64))
                                .opaque(false)
                                .border(4, Color.RED),
                    ) {
                        Label(text = "child", modifier = SwingModifier.testTag("decorated-child"))
                    }
                }
            }

            val panel = onNodeWithTag("decorated-panel").fetch<JComponent>()
            val painted = onNodeWithTag("decorated-panel").captureToImage()

            assertEquals(Color.RED.rgb, painted.getRGB(0, 0), "The stroke runs inside the component's edge.")
            assertEquals(0, painted.getRGB(32, 32), "The stroke is an outline, not a fill.")
            assertEquals(
                Insets(0, 0, 0, 0),
                panel.insets,
                "A border decoration paints over the content rather than taking pixels off it, which is what " +
                    "the border slot does.",
            )
        }

    @Test
    fun theShadowShortcutReservesTheOutsetsItsBlurNeeds() =
        runComposeSwingTest {
            setContent {
                Box {
                    DecoratedBox(
                        modifier =
                            SwingModifier
                                .testTag("decorated-panel")
                                .preferredSize(Dimension(64, 64))
                                .opaque(false)
                                .shadow(4, Color.BLACK)
                                .background(Brush.of(Color.BLUE)),
                    ) {
                        Label(text = "child", modifier = SwingModifier.testTag("decorated-child"))
                    }
                }
            }

            val panel = onNodeWithTag("decorated-panel").fetch<JComponent>()
            val child = onNodeWithTag("decorated-child").fetch<JComponent>()
            val painted = onNodeWithTag("decorated-panel").captureToImage()

            val reach = blurOutsets(4)
            assertEquals(
                Insets(reach, reach, reach, reach),
                panel.insets,
                "A shadow's paint outsets are reported in the insets, as a look and feel reports a focus ring.",
            )
            assertEquals(reach, child.x, "The layout origin stands the outsets inside the bounds.")
            assertTrue(
                painted.getRGB(reach - 1, 32) ushr 24 > 0,
                "The shadow is cast into what it reserved.",
            )
            assertFalse(panel.isOpaque, "What the shadow reserved is only partly covered.")
        }

    @Test
    fun aBorderAsksItsShapeAgainOnlyWhenTheSizeOrItsOwnValuesChange() =
        runComposeSwingTest {
            var calls = 0
            var width by mutableIntStateOf(64)
            var line by mutableIntStateOf(2)
            val countingShape =
                Shape { w, h ->
                    calls++
                    RoundRectangle2D.Float(0f, 0f, w.toFloat(), h.toFloat(), 8f, 8f)
                }
            val freshShape = Shape { w, h -> RoundRectangle2D.Float(0f, 0f, w.toFloat(), h.toFloat(), 8f, 8f) }
            setContent {
                Box {
                    DecoratedBox(
                        modifier =
                            SwingModifier
                                .testTag("decorated-panel")
                                .preferredSize(Dimension(width, 64))
                                .opaque(false)
                                .border(line, Color.RED, countingShape),
                    )
                    DecoratedBox(
                        modifier =
                            SwingModifier
                                .testTag("fresh-panel")
                                .preferredSize(Dimension(128, 64))
                                .opaque(false)
                                .border(2, Color.RED, freshShape),
                    )
                }
            }

            onNodeWithTag("decorated-panel").captureToImage()
            onNodeWithTag("decorated-panel").captureToImage()
            assertEquals(1, calls, "a repeat paint at the same size reuses the cached band")

            width = 128
            awaitIdle()
            val resized = onNodeWithTag("decorated-panel").captureToImage()
            assertEquals(2, calls, "a resized paint rebuilds the band once")
            assertImagesPixelPerfect(onNodeWithTag("fresh-panel").captureToImage(), resized)

            val panel = onNodeWithTag("decorated-panel").fetch<JComponent>()
            withRecordedRepaints { recorder ->
                line = 6
                awaitIdle()

                recorder.assertAskedToRepaint(panel, "a changed width")
                recorder.assertAskedForNoLayout(panel, "a changed width")
            }
            onNodeWithTag("decorated-panel").captureToImage()
            assertEquals(3, calls, "the new width drops the band and asks the shape again")
        }

    @Test
    fun theBrushBorderShortcutStrokesWithTheBrush() =
        runComposeSwingTest {
            setContent {
                Box {
                    DecoratedBox(
                        modifier =
                            SwingModifier
                                .testTag("decorated-panel")
                                .preferredSize(Dimension(64, 64))
                                .opaque(false)
                                .border(4, Brush.of(Color.RED)),
                    )
                }
            }

            val painted = onNodeWithTag("decorated-panel").captureToImage()

            assertEquals(Color.RED.rgb, painted.getRGB(0, 0), "The stroke is filled with the brush.")
            assertEquals(0, painted.getRGB(32, 32), "The stroke is an outline, not a fill.")
        }

    @Test
    fun aGradientSpansTheSizeTheComponentStandsAtWhenItPaints() =
        runComposeSwingTest {
            var width by mutableIntStateOf(64)
            setContent {
                Box {
                    DecoratedBox(
                        modifier =
                            SwingModifier
                                .testTag("decorated-panel")
                                .preferredSize(Dimension(width, 64))
                                .opaque(false)
                                .background(Brush.horizontalGradient(*arrayOf(0f to Color.BLACK, 1f to Color.WHITE))),
                    )
                }
            }

            val narrow = onNodeWithTag("decorated-panel").captureToImage()
            width = 128
            awaitIdle()
            val wide = onNodeWithTag("decorated-panel").captureToImage()

            assertEquals(
                narrow.getRGB(32, 32),
                wide.getRGB(64, 32),
                "A brush is asked for its paint at the size the component stands at, so a gradient is the " +
                    "same color halfway across whatever width the component was resized to.",
            )
            assertNotEquals(
                narrow.getRGB(32, 32),
                wide.getRGB(32, 32),
                "The same pixel is only a quarter of the way through the gradient once the component is twice as wide.",
            )
        }

    @Test
    fun anAntialiasedClipFollowsTheSizeTheComponentStandsAtWhenItPaints() =
        runComposeSwingTest {
            var width by mutableIntStateOf(64)
            setContent {
                Box {
                    DecoratedBox(
                        modifier =
                            SwingModifier
                                .testTag("decorated-panel")
                                .preferredSize(Dimension(width, 64))
                                .opaque(false)
                                .clip(CircleShape, antialias = true)
                                .background(Brush.of(Color.BLUE)),
                    )
                }
            }

            // The first paint, at the narrow size.
            onNodeWithTag("decorated-panel").captureToImage()
            width = 128
            awaitIdle()
            val wide = onNodeWithTag("decorated-panel").captureToImage()

            assertEquals(0, wide.getRGB(126, 2), "The corner lies outside the ellipse and is cut away.")
            assertEquals(
                Color.BLUE.rgb,
                wide.getRGB(100, 32),
                "The outline is resolved at the size the component stands at, so the wide component is cut to the " +
                    "ellipse spanning its width.",
            )
        }

    @Test
    fun builtInDecorationsRebuiltFromEqualValuesAreTheSameDeclaration() {
        val gradient = { Brush.verticalGradient(0f to Color.RED, 1f to Color.BLUE) }
        val declarations: List<() -> SwingModifier> =
            listOf(
                { SwingModifier.clip(RoundedCornerShape(4f), antialias = true) },
                { SwingModifier.background(Color.BLUE, CircleShape) },
                { SwingModifier.background(gradient(), RoundedCornerShape(4f), alpha = 0.5f) },
                { SwingModifier.border(2, Color.BLUE, CircleShape) },
                { SwingModifier.border(2, gradient()) },
                { SwingModifier.border(BorderStroke(3, Color.BLUE)) },
                { SwingModifier.alpha(0.5f) },
                { SwingModifier.blur(4) },
                { SwingModifier.shadow(4, Color.BLACK, offsetX = 1, offsetY = 2) },
            )
        val rebuilt = declarations.map { it() to it() }

        for ((first, second) in rebuilt) {
            assertEquals(first, second, "A chain rebuilt from unchanged values is the same declaration.")
            assertEquals(first.hashCode(), second.hashCode(), "Equal declarations share a hash code.")
        }
        assertEquals(
            declarations.size,
            rebuilt.map { it.first }.toSet().size,
            "Chains declaring different values are different declarations.",
        )
    }

    @Test
    fun noDecorationChangesTheSizeOfWhatItDecoratesOrWhereItAnswers() =
        runComposeSwingTest {
            setContent {
                Box {
                    DecoratedBox(modifier = SwingModifier.testTag("decorated-panel")) { Label(text = "child") }
                    DecoratedBox(
                        modifier =
                            SwingModifier
                                .testTag("reversed-panel")
                                .clip(CircleShape)
                                .background(Brush.of(Color.BLUE))
                                .border(2, Color.BLUE)
                                .alpha(0.5f)
                                .blur(4)
                                .shadow(4, Color.BLACK),
                    ) { Label(text = "child") }
                }
            }

            val plain = onNodeWithTag("decorated-panel").fetch<JComponent>().getComponent(0)
            val decorated = onNodeWithTag("reversed-panel").fetch<JComponent>()
            val child = decorated.getComponent(0)

            assertEquals(
                plain.size,
                child.size,
                "A decoration decides how a component looks, not how big its content is.",
            )
            assertSame(
                child,
                SwingUtilities.getDeepestComponentAt(decorated, child.x + 1, child.y + 1),
                "The content answers the mouse where it is painted, whatever is declared around it.",
            )
        }

    @Test
    fun aShadowGrowsTheComponentRatherThanShrinkingItsContent() =
        runComposeSwingTest {
            var drawn: Dimension? = null
            setContent {
                Box {
                    Canvas(
                        modifier =
                            SwingModifier
                                .testTag("decorated-panel")
                                .preferredSize(Dimension(64, 64))
                                .shadow(4, Color.BLACK),
                    ) { _, width, height -> drawn = Dimension(width, height) }
                }
            }
            val canvas = onNodeWithTag("decorated-panel").fetch<JComponent>()
            onNodeWithTag("decorated-panel").captureToImage()

            assertEquals(Dimension(64, 64), drawn, "A size asked of the component is the size its content still gets.")
            assertEquals(
                Dimension(64, 64),
                canvas.preferredSize,
                "A preferred size set on the component answers as set.",
            )
            assertEquals(
                Dimension(64 + blurOutsets(4) * 2, 64 + blurOutsets(4) * 2),
                canvas.size,
                "The outsets the shadow needs are added around that size.",
            )
        }

    @Test
    fun eachBuiltInDecorationDescribesItselfByTheValuesItDeclares() {
        val brush = Brush.of(Color.BLUE)

        val clip = decorated { SwingModifier.clip(CircleShape, antialias = true) }.lastElement()
        val blur = decorated { SwingModifier.blur(4) }.lastElement()
        val shadow = decorated { SwingModifier.shadow(4, Color.BLACK, offsetX = 1, offsetY = 2) }.lastElement()
        val border = decorated { SwingModifier.border(2, brush, CircleShape) }.lastElement()

        assertEquals(
            "clip" to mapOf("shape" to CircleShape, "antialias" to true),
            clip.name to clip.declaredValues,
            "clip must report its shape and antialiasing",
        )
        assertEquals(
            "blur" to mapOf("radius" to 4),
            blur.name to blur.declaredValues,
            "blur must report its radius",
        )
        assertEquals(
            "shadow" to mapOf("radius" to 4, "color" to Color.BLACK, "offsetX" to 1, "offsetY" to 2),
            shadow.name to shadow.declaredValues,
            "shadow must report its radius, color and offset",
        )
        assertEquals(
            "border" to mapOf("width" to 2, "brush" to brush, "shape" to CircleShape),
            border.name to border.declaredValues,
            "border must report its width, brush and shape",
        )
    }

    @Test
    fun anAntialiasedClipRecordedOffThePixelGridLeavesNothingOutsideItsOutline() =
        runComposeSwingTest {
            setContent {
                Box {
                    DecoratedBox(
                        modifier =
                            SwingModifier
                                .testTag("decorated-panel")
                                .preferredSize(Dimension(128, 128))
                                .opaque(false)
                                // The blur reduces its recording to a quarter scale, which puts the indented
                                // content's edges a quarter of a reduced pixel off its grid on the left and top,
                                // and three quarters on the right and bottom.
                                .blur(8)
                                .indent(1)
                                .clip(CircleShape, antialias = true)
                                .background(Brush.of(Color.BLUE)),
                    )
                }
            }

            val image = onNodeWithTag("decorated-panel").captureToImage()
            val circle = (image.width - 2 * (blurOutsets(8) + 1)) / 2.0
            val reach = circle + blurOutsets(8)
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    val distance = hypot(x + 0.5 - image.width / 2.0, y + 0.5 - image.height / 2.0)
                    if (distance > reach) {
                        assertEquals(
                            0,
                            image.getRGB(x, y) ushr 24,
                            "($x, $y) lies beyond the blur's reach of the circle, so no content edge shows there.",
                        )
                    }
                }
            }
        }

    @Test
    fun aBlurFadesOutWithoutASharpLineOnAnyEdge() =
        runComposeSwingTest {
            var radius by mutableIntStateOf(1)
            setContent {
                Box {
                    DecoratedBox(
                        modifier =
                            SwingModifier
                                .testTag("decorated-panel")
                                .preferredSize(Dimension(48, 48))
                                .opaque(false)
                                // Off the grid the blur reduces its recording onto, for every radius it reduces at.
                                .blur(radius)
                                .indent(1)
                                .background(Brush.of(Color.BLUE)),
                    )
                }
            }

            for (value in 1..16) {
                radius = value
                awaitIdle()
                val image = onNodeWithTag("decorated-panel").captureToImage()

                fun alpha(
                    x: Int,
                    y: Int,
                ): Int = if (x in 0 until image.width && y in 0 until image.height) image.getRGB(x, y) ushr 24 else 0

                // A blurred edge rises no steeper than its Gaussian, whose steepest slope is about 0.8 / radius of
                // full coverage per pixel; a line left sharp, or a halo cut short, is a steeper step.
                val steepest = 255 * 1.2 / value
                for (y in -1..image.height) {
                    for (x in -1..image.width) {
                        val across = abs(alpha(x + 1, y) - alpha(x, y))
                        val down = abs(alpha(x, y + 1) - alpha(x, y))
                        assertTrue(
                            maxOf(across, down) <= steepest,
                            "Blur $value steps by ${maxOf(across, down)} at ($x, $y), steeper than $steepest.",
                        )
                    }
                }
                val perimeter =
                    (0 until image.width).flatMap { x -> listOf(x to 0, x to image.height - 1) } +
                        (0 until image.height).flatMap { y -> listOf(0 to y, image.width - 1 to y) }
                for ((x, y) in perimeter) {
                    assertEquals(0, alpha(x, y), "Blur $value fades out before the edge, but not at ($x, $y).")
                }
            }
        }
}
