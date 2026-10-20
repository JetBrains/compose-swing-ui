package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertPixels
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.graphics.blurOutsets
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.style.componentOrientation
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Color
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.Insets
import java.awt.Rectangle
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PlacementScaleOverflowTest {
    /** The paint outsets of a scaled layer hold what a child overflowing its box paints, scaled with it. */
    @Test
    fun aScaledLayerHoldsTheScaledOverflowOfItsChild() =
        runComposeSwingTest {
            setContent {
                Row {
                    Box(modifier = SwingModifier.testTag("layered").size(40, 40).placementLayer { scaleX = 0.75f }) {
                        Box(modifier = SwingModifier.requiredSize(80, 40))
                    }
                }
            }
            onNodeWithTag("layered").assertProperty(
                Rectangle(-10, 0, 60, 40),
                "the scaled layer's bounds hold the child's scaled overflow",
            ) { bounds }
        }

    @Test
    fun aScaledLayerGrowsForAChildThatStartsOverflowingWhileScaled() =
        runComposeSwingTest {
            var width by mutableIntStateOf(40)
            setContent {
                Row {
                    Box(modifier = SwingModifier.testTag("layered").size(40, 40).placementLayer { scaleX = 0.75f }) {
                        Box(modifier = SwingModifier.requiredSize(width, 40))
                    }
                }
            }
            val layered = onNodeWithTag("layered").fetch<JComponent>()
            assertEquals(Rectangle(0, 0, 40, 40), layered.bounds, "no overflow before the child grows")

            width = 80
            awaitIdle()

            assertEquals(
                Rectangle(-10, 0, 60, 40),
                layered.bounds,
                "the layer must grow once its child starts overflowing",
            )
        }

    @Test
    fun aScaledLayerShrinksBackOnceItsOverflowingChildIsRemoved() =
        runComposeSwingTest {
            var present by mutableStateOf(true)
            setContent {
                Row {
                    Box(modifier = SwingModifier.testTag("layered").size(40, 40).placementLayer { scaleX = 0.75f }) {
                        if (present) Box(modifier = SwingModifier.requiredSize(80, 40))
                    }
                }
            }
            val layered = onNodeWithTag("layered").fetch<JComponent>()
            assertEquals(Rectangle(-10, 0, 60, 40), layered.bounds, "the overflowing child grows the layer")

            present = false
            awaitIdle()
            assertEquals(Rectangle(0, 0, 40, 40), layered.bounds, "removing the child must shrink the layer back")
        }

    @Test
    fun aScaledLayerLeavesOutAChildItsPolicyLeavesUnplaced() =
        runComposeSwingTest {
            var place by mutableStateOf(true)
            setContent {
                Box {
                    Layout(
                        modifier = SwingModifier.testTag("layered").placementLayer { scaleX = 0.75f },
                        content = { Box(modifier = SwingModifier.requiredSize(80, 40)) },
                        measurePolicy = { measurables, _ ->
                            val placeable = measurables.single().measure(Constraints())
                            layout(40, 40) {
                                if (place) placeable.place(-20, 0)
                            }
                        },
                    )
                }
            }
            val layered = onNodeWithTag("layered").fetch<JComponent>()
            assertEquals(Rectangle(-10, 0, 60, 40), layered.bounds, "the placed overflowing child grows the layer")

            place = false
            awaitIdle()
            assertEquals(
                Rectangle(0, 0, 40, 40),
                layered.bounds,
                "a child the policy leaves unplaced must not grow the layer",
            )

            place = true
            awaitIdle()
            assertEquals(Rectangle(-10, 0, 60, 40), layered.bounds, "placing the child again must grow the layer again")
        }

    @Test
    fun aScaledLayerHoldsTheOverflowOfAChildInsideAnotherScaledLayer() =
        runComposeSwingTest {
            setContent {
                Row {
                    Box(modifier = SwingModifier.testTag("outer").size(40, 40).placementLayer { scaleX = 1.5f }) {
                        Box(modifier = SwingModifier.size(40, 40).placementLayer { scaleX = 0.75f }) {
                            Box(modifier = SwingModifier.requiredSize(80, 40))
                        }
                    }
                }
            }
            onNodeWithTag("outer").assertProperty(
                Rectangle(-25, 0, 90, 40),
                "the outer layer holds the inner layer's scaled overflow, scaled again",
            ) { bounds }
        }

    /**
     * A Row, a Column and a custom layout each hold the scaled overflow of their child. Whether the child is centered
     * under the container's constraints decides whether the overflow spreads evenly on both sides.
     */
    @Test
    fun aScaledRowColumnAndCustomLayoutHoldTheScaledOverflowOfTheirChild() =
        runComposeSwingTest {
            setContent {
                Box {
                    Row(
                        modifier =
                            SwingModifier
                                .testTag("row")
                                .placementLayer { scaleX = 0.75f }
                                .styleable { preferredSize(40, 40) },
                    ) {
                        Box(modifier = SwingModifier.requiredSize(80, 40))
                    }
                }
                Box {
                    Column(
                        modifier =
                            SwingModifier
                                .testTag("column")
                                .placementLayer { scaleX = 0.75f }
                                .styleable { preferredSize(40, 40) },
                    ) {
                        Box(modifier = SwingModifier.requiredSize(80, 40))
                    }
                }
                Box {
                    Layout(
                        modifier = SwingModifier.testTag("custom").placementLayer { scaleX = 0.75f },
                        content = { Box(modifier = SwingModifier.requiredSize(80, 40)) },
                        measurePolicy = { measurables, _ ->
                            val placeable = measurables.single().measure(Constraints())
                            layout(40, 40) { placeable.place(0, 0) }
                        },
                    )
                }
            }

            onNodeWithTag("row").assertProperty(
                Insets(0, 10, 0, 10),
                "the row must center the overflow of its single, constrained child",
            ) { assertIs<Decoratable>(this).decoration.paintOutsets() }
            onNodeWithTag("column").assertProperty(Insets(0, 10, 0, 10), "and the column must do the same") {
                assertIs<Decoratable>(this).decoration.paintOutsets()
            }
            onNodeWithTag("custom").assertProperty(
                Insets(0, 0, 0, 25),
                "a policy placing an unconstrained child at its own offset must not center it",
            ) { assertIs<Decoratable>(this).decoration.paintOutsets() }
        }

    @Test
    fun aScaledLayerHoldsTheScaledShadowOfItsChild() =
        runComposeSwingTest {
            setContent {
                Row {
                    Box(modifier = SwingModifier.testTag("layered").size(40, 40).placementLayer { scaleX = 2f }) {
                        Canvas(
                            modifier =
                                SwingModifier
                                    .testTag("child")
                                    .shadow(4, Color.BLACK)
                                    .styleable { preferredSize(40, 40) },
                        ) {}
                    }
                }
            }

            val reach = blurOutsets(4)
            onNodeWithTag("child").assertProperty(Insets(reach, reach, reach, reach)) {
                assertIs<Decoratable>(this).decoration.paintOutsets()
            }
            onNodeWithTag("layered").assertProperty(
                Insets(reach, 2 * reach + 20, reach, 2 * reach + 20),
                "the shadow scales horizontally about the center",
            ) { assertIs<Decoratable>(this).decoration.paintOutsets() }
        }

    @Test
    fun aRightToLeftScaledLayerHoldsTheMirroredOverflowOfItsChild() =
        runComposeSwingTest {
            setContent {
                for (orientation in listOf(ComponentOrientation.LEFT_TO_RIGHT, ComponentOrientation.RIGHT_TO_LEFT)) {
                    Box {
                        Layout(
                            modifier =
                                SwingModifier
                                    .testTag(if (orientation.isLeftToRight) "leftToRight" else "rightToLeft")
                                    .placementLayer { scaleX = 0.75f }
                                    .styleable { componentOrientation(orientation) },
                            content = { Box(modifier = SwingModifier.requiredSize(80, 40)) },
                            measurePolicy = { measurables, _ ->
                                val placeable = measurables.single().measure(Constraints())
                                layout(40, 40) { placeable.placeRelative(0, 0) }
                            },
                        )
                    }
                }
            }

            onNodeWithTag("leftToRight").assertProperty(
                Insets(0, 0, 0, 25),
                "placed from the left, the child spans 0..80, scaled to 5..65",
            ) { assertIs<Decoratable>(this).decoration.paintOutsets() }
            onNodeWithTag("rightToLeft").assertProperty(
                Insets(0, 25, 0, 0),
                "mirrored, the child spans -40..40, scaled to -25..35",
            ) { assertIs<Decoratable>(this).decoration.paintOutsets() }
        }

    /** A layer that both fades and scales its content paints the scaled overflow at the faded alpha. */
    @Test
    fun aFadingScaledLayerPaintsTheScaledOverflowOfItsChild() =
        runComposeSwingTest {
            setContent {
                Row {
                    Box(
                        modifier =
                            SwingModifier.testTag("layered").size(40, 40).placementLayer {
                                scaleX = 0.75f
                                alpha = 0.5f
                            },
                    ) {
                        Canvas(modifier = SwingModifier.requiredSize(80, 40)) { drawRect(Color.RED) }
                    }
                }
            }
            onNodeWithTag("layered").assertProperty(
                Rectangle(-10, 0, 60, 40),
                "a fading layer still holds the scaled overflow",
            ) { bounds }

            val image = onNodeWithTag("layered").captureToImage()
            image.assertPixels(
                expectedSize = Dimension(60, 40),
                message = "the overflow left of the box, the box and the overflow right of it must paint at half alpha",
            ) { position ->
                if (position.y == 20 && position.x in listOf(2, 30, 57)) Color(255, 0, 0, 128) else null
            }
        }

    /**
     * A fade layer nested inside a scale layer paints the scaled overflow the outer layer takes paint outsets for,
     * not only what falls inside its own box.
     */
    @Test
    fun aFadeLayerNestedInAScaleLayerPaintsTheScaledOverflowOfItsChild() =
        runComposeSwingTest {
            setContent {
                Row {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag("layered")
                                .size(40, 40)
                                .placementLayer { scaleX = 0.75f }
                                .placementLayer { alpha = 0.5f },
                    ) {
                        Canvas(modifier = SwingModifier.requiredSize(80, 40)) { drawRect(Color.RED) }
                    }
                }
            }
            onNodeWithTag("layered").assertProperty(
                Rectangle(-10, 0, 60, 40),
                "a scale and a fade in separate layers hold the scaled overflow",
            ) { bounds }

            val image = onNodeWithTag("layered").captureToImage()
            image.assertPixels(
                expectedSize = Dimension(60, 40),
                message = "the overflow left of the box, the box and the overflow right of it must paint at half alpha",
            ) { position ->
                if (position.y == 20 && position.x in listOf(2, 30, 57)) Color(255, 0, 0, 128) else null
            }
        }
}
