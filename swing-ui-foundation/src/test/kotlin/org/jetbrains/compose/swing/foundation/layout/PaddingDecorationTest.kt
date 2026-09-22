package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.foundation.graphics.Brush
import org.jetbrains.compose.swing.foundation.graphics.CircleShape
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.graphics.border
import org.jetbrains.compose.swing.foundation.graphics.clip
import org.jetbrains.compose.swing.foundation.graphics.layoutBounds
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.componentOrientation
import org.jetbrains.compose.swing.modifier.listener.mouseListener
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Color
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.Insets
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.compose.swing.modifier.appearance.background as componentBackground

/**
 * A decoration paints at the box of the first layout modifier declared after it, as androidx draws it with that
 * modifier's coordinator, and at the component's layout bounds when none follows. A padding always takes space in
 * the layout; what a decoration before it paints over that space is paint outsets.
 */
class PaddingDecorationTest {
    @Test
    fun aBackgroundAfterAPaddingPaintsInsideIt() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag("outer")) {
                    Box(modifier = SwingModifier.testTag("card").padding(8).background(Red)) { Content() }
                }
            }
            val card = onNodeWithTag("card").fetch<ConstrainedPanel>()

            assertEquals(
                Rectangle(8, 8, 20, 20),
                card.layoutBounds,
                "the padding places the component",
            )
            assertEquals(Insets(0, 0, 0, 0), card.insets, "the background paints at the component's box")
            val painted = onNodeWithTag("outer").captureToImage()
            assertEquals(0, painted.getRGB(1, 1), "the padding space is left unpainted")
            assertEquals(Color.RED.rgb, painted.getRGB(10, 10), "the background fills the component")
        }

    @Test
    fun aBackgroundBeforeAPaddingPaintsOverItsOutsets() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag("outer")) {
                    Box(modifier = SwingModifier.testTag("card").background(Red).padding(8)) { Content() }
                }
            }
            val card = onNodeWithTag("card").fetch<ConstrainedPanel>()

            assertEquals(
                Rectangle(8, 8, 20, 20),
                card.layoutBounds,
                "the padding still places the component",
            )
            assertEquals(Insets(8, 8, 8, 8), card.decoration.paintOutsets(), "the ring is paint outsets")
            assertEquals(card.decoration.paintOutsets(), card.insets, "the insets report only the paint outsets")
            assertEquals(
                Rectangle(0, 0, 20, 20),
                onNodeWithTag(CONTENT).fetch<ConstrainedPanel>().layoutBounds,
                "the content starts at the component's origin",
            )
            val painted = onNodeWithTag("outer").captureToImage()
            assertEquals(
                Dimension(36, 36),
                Dimension(painted.width, painted.height),
                "the capture holds the component and its padding ring",
            )
            assertEquals(Color.RED.rgb, painted.getRGB(1, 1), "the background paints over the padding outsets")
            assertEquals(Color.RED.rgb, painted.getRGB(34, 34), "on every side")
            assertEquals(Color.RED.rgb, painted.getRGB(18, 18), "and over the component")
        }

    @Test
    fun paddingsAndDecorationsNestInDeclarationOrder() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag("outer")) {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag("card")
                                .padding(4)
                                .background(Red)
                                .padding(8)
                                .background(Blue),
                    ) { Content() }
                }
            }

            assertEquals(
                Rectangle(12, 12, 20, 20),
                onNodeWithTag("card").fetch<ConstrainedPanel>().layoutBounds,
                "both paddings place the component",
            )
            val painted = onNodeWithTag("outer").captureToImage()
            assertEquals(0, painted.getRGB(2, 2), "the first padding is left clear")
            assertEquals(Color.RED.rgb, painted.getRGB(6, 6), "the red background paints over the second padding")
            assertEquals(Color.BLUE.rgb, painted.getRGB(14, 14), "the blue one paints at the component's box")
        }

    @Test
    fun aDecorationAfterThePaddingGetsTheComponentsBox() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag("outer")) {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag("card")
                                .background(Red)
                                .padding(8)
                                .clip(CircleShape)
                                .background(Blue),
                    ) { Content() }
                }
            }

            val painted = onNodeWithTag("outer").captureToImage()
            assertEquals(Color.RED.rgb, painted.getRGB(9, 9), "the clip cuts the component's corner, not the ring")
            assertEquals(Color.BLUE.rgb, painted.getRGB(18, 18), "the circle fills the component's box")
            assertEquals(Color.RED.rgb, painted.getRGB(1, 1), "the ring is outside the clip")
        }

    @Test
    fun aSizeAfterThePaddingSizesTheComponentAsInAndroidx() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag("outer")) {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag("card")
                                .background(Red)
                                .padding(8)
                                .size(100),
                    )
                }
            }

            assertEquals(
                Rectangle(8, 8, 100, 100),
                onNodeWithTag("card").fetch<ConstrainedPanel>().layoutBounds,
                "the size applies inside the padding",
            )
            val painted = onNodeWithTag("outer").captureToImage()
            assertEquals(Dimension(116, 116), Dimension(painted.width, painted.height), "the slot holds the padding")
            assertEquals(Color.RED.rgb, painted.getRGB(1, 1), "the background fills the whole slot")
        }

    @Test
    fun anUnsizedCanvasPaintsItsBackgroundOverThePadding() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag("outer")) {
                    Canvas(modifier = SwingModifier.background(Red).padding(8)) {}
                }
            }

            val painted = onNodeWithTag("outer").captureToImage()
            assertEquals(
                Dimension(16, 16),
                Dimension(painted.width, painted.height),
                "the canvas's slot is the padding alone",
            )
            assertEquals(Color.RED.rgb, painted.getRGB(1, 1), "the background paints over the padding")
        }

    @Test
    fun theRingIsOutsideTheHitArea() =
        runComposeSwingTest {
            setContent {
                Box {
                    Box(modifier = SwingModifier.testTag("card").background(Red).padding(8)) { Content() }
                }
            }
            val card = onNodeWithTag("card").fetch<ConstrainedPanel>()

            assertFalse(card.contains(1, 1), "a point on the ring is not the component's")
            assertTrue(card.contains(9, 9), "a point on the component's box is")
        }

    @Test
    fun declaringADecorationBeforeAPaddingOnlyChangesThePaintOutsets() =
        runComposeSwingTest {
            var decorated by mutableStateOf(false)
            val sizes = mutableListOf<Dimension>()
            setContent {
                Box(modifier = SwingModifier.testTag("outer")) {
                    val tagged = SwingModifier.testTag("card")
                    Box(
                        modifier =
                            (if (decorated) tagged.background(Red) else tagged)
                                .padding(8)
                                .onSizeChanged { sizes += it },
                    ) { Content() }
                }
            }
            val card = onNodeWithTag("card").fetch<ConstrainedPanel>()
            assertEquals(
                Insets(0, 0, 0, 0),
                card.decoration.paintOutsets(),
                "without a decoration there are no paint outsets",
            )

            decorated = true
            awaitIdle()
            assertEquals(Insets(8, 8, 8, 8), card.decoration.paintOutsets(), "the ring becomes paint outsets")
            assertEquals(
                Rectangle(8, 8, 20, 20),
                card.layoutBounds,
                "the component stays where it is",
            )
            assertEquals(
                Color.RED.rgb,
                onNodeWithTag("outer").captureToImage().getRGB(1, 1),
                "the background paints over the ring",
            )

            decorated = false
            awaitIdle()
            assertEquals(
                Insets(0, 0, 0, 0),
                card.decoration.paintOutsets(),
                "the paint outsets leave with the decoration",
            )
            assertEquals(
                0,
                onNodeWithTag("outer").captureToImage().getRGB(1, 1),
                "the ring is left clear once the decoration leaves",
            )
            assertEquals(listOf(Dimension(20, 20)), sizes, "the component is never resized")
        }

    @Test
    fun aDecorationDeclaredFromTheStartBeforeAPaddingLeavesWithItsPaintOutsets() =
        runComposeSwingTest {
            var decorated by mutableStateOf(true)
            setContent {
                Box {
                    val tagged = SwingModifier.testTag("card")
                    Box(modifier = (if (decorated) tagged.background(Red) else tagged).padding(8)) { Content() }
                }
            }
            val card = onNodeWithTag("card").fetch<ConstrainedPanel>()
            assertEquals(
                Insets(8, 8, 8, 8),
                card.decoration.paintOutsets(),
                "the decoration before the padding takes the ring as paint outsets",
            )

            decorated = false
            awaitIdle()

            assertEquals(
                Insets(0, 0, 0, 0),
                card.decoration.paintOutsets(),
                "the paint outsets leave with the decoration",
            )
        }

    @Test
    fun aChangedPaddingMovesThePaintOutsetsWithIt() =
        runComposeSwingTest {
            var outsets by mutableIntStateOf(4)
            setContent {
                Box(modifier = SwingModifier.testTag("outer")) {
                    Box(modifier = SwingModifier.testTag("card").background(Red).padding(outsets)) { Content() }
                }
            }
            val card = onNodeWithTag("card").fetch<ConstrainedPanel>()
            assertEquals(
                Insets(4, 4, 4, 4),
                card.decoration.paintOutsets(),
                "the ring of a 4 px padding is paint outsets",
            )

            outsets = 8
            awaitIdle()

            assertEquals(
                Insets(8, 8, 8, 8),
                card.decoration.paintOutsets(),
                "the paint outsets follow the changed padding",
            )
            assertEquals(
                Color.RED.rgb,
                onNodeWithTag("outer").captureToImage().getRGB(5, 5),
                "the background paints over the wider ring",
            )
        }

    @Test
    fun theRingFollowsTheReadingOrderThePaddingIsPlacedBy() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.componentOrientation(ComponentOrientation.RIGHT_TO_LEFT)) {
                    Box(modifier = SwingModifier.testTag("card").background(Red).padding(start = 4, end = 10)) {
                        Content()
                    }
                }
            }
            val card = onNodeWithTag("card").fetch<ConstrainedPanel>()

            assertEquals(10, card.layoutBounds.x, "the end outset is on the left")
            assertEquals(
                Insets(0, 10, 0, 4),
                card.decoration.paintOutsets(),
                "and so is the ring painted over it",
            )
        }

    @Test
    fun anOpaqueComponentLeavesTheRingToItsDecoration() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag("outer")) {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag("card")
                                .opaque(true)
                                .componentBackground(Color.GREEN)
                                .border(1, Color.BLUE)
                                .padding(8),
                    ) { Content() }
                }
            }

            assertFalse(onNodeWithTag("card").fetch<ConstrainedPanel>().isOpaque, "the ring is only partly covered")
            val painted = onNodeWithTag("outer").captureToImage()
            assertEquals(Color.BLUE.rgb, painted.getRGB(0, 0), "the border runs along the padding's box")
            assertEquals(0, painted.getRGB(4, 4), "the component's own background stays off the ring")
            assertEquals(Color.GREEN.rgb, painted.getRGB(18, 18), "and fills the layout bounds inside the ring")
        }

    @Test
    fun aRingDeclaredAgainLeavesTheInsetsWhereTheModifierFoundThem() =
        runComposeSwingTest {
            var ringed by mutableStateOf(true)
            setContent {
                Box(modifier = SwingModifier.testTag("outer")) {
                    val tagged = SwingModifier.testTag("card")
                    Box(
                        modifier =
                            (if (ringed) tagged.border(2, Color.BLUE) else tagged)
                                .padding(3)
                                .background(Red)
                                .mouseListener(onMouseClicked = {}),
                    ) { Content() }
                }
            }
            val card = onNodeWithTag("card").fetch<ConstrainedPanel>()
            val ring = Insets(3, 3, 3, 3)
            val none = Insets(0, 0, 0, 0)
            assertEquals(ring, card.decoration.paintOutsets(), "the ring is paint outsets")
            assertEquals(ring, card.insets, "and the insets carry it")

            ringed = false
            awaitIdle()
            assertEquals(none, card.decoration.paintOutsets(), "the paint outsets leave with the ring")
            assertEquals(none, card.insets, "and so do the insets")

            ringed = true
            awaitIdle()
            assertEquals(ring, card.decoration.paintOutsets(), "the ring declared again takes its outsets back")
            assertEquals(ring, card.insets, "and the insets carry it again")
            assertEquals(Color.BLUE.rgb, onNodeWithTag("outer").captureToImage().getRGB(0, 0), "the ring is painted")

            ringed = false
            awaitIdle()
            assertEquals(none, card.decoration.paintOutsets(), "the paint outsets leave with the ring again")
            assertEquals(none, card.insets, "and so do the insets")
            assertEquals(0, onNodeWithTag("outer").captureToImage().getRGB(0, 0), "no ring is painted")
        }
}

@Composable
private fun BoxScope.Content() {
    Box(modifier = SwingModifier.testTag(CONTENT).size(20))
}

private val Red = Brush.of(Color.RED)
private val Blue = Brush.of(Color.BLUE)

private const val CONTENT = "content"
