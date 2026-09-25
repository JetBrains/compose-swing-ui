package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.foundation.graphics.Brush
import org.jetbrains.compose.swing.foundation.graphics.TransformOrigin
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.graphics.renderImage
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.RepaintManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Behavioral tests for a layout modifier placing its content with a layer. */
class PlacementLayerTest {
    @Test
    fun aFadePaintsTheContentAsOneImageAtTheDeclaredAlphaOnTheSamePixelsAt1x() = assertFadeIsOneImage(scale = 1.0)

    @Test
    fun aFadePaintsTheContentAsOneImageAtTheDeclaredAlphaOnTheSamePixelsAt2x() = assertFadeIsOneImage(scale = 2.0)

    @Test
    fun aFadeMultipliesIntoTheAlphaTheGraphicsAlreadyCarries() =
        runComposeSwingTest {
            setCanvases("layered" to SwingModifier.placementLayer { alpha = 0.5f })

            val faded =
                paintTagged(
                    "layered",
                    setUp = { composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f) },
                )

            assertEquals(64.0, faded.alphaAt(10, 10), 1.0)
        }

    @Test
    fun aFadeUnderAClipPaintsOnlyInsideIt() =
        runComposeSwingTest {
            setCanvases("layered" to SwingModifier.placementLayer { alpha = 0.5f })

            val faded = paintTagged("layered", setUp = { clipRect(0, 0, 10, 20) })

            assertEquals(128.0, faded.alphaAt(10 / 2, 10), 1.0)
            assertEquals(0, faded.getRGB(10 + 2, 10))
        }

    @Test
    fun aFadeOverNothingPaintsNothingAndSkipsTheContent() =
        runComposeSwingTest {
            var painted = false
            val boxOffset = mutableIntStateOf(20 * 2)
            setCanvases(
                "layered" to SwingModifier.placementLayer { alpha = 0.5f },
                "boxOutside" to
                    SwingModifier
                        .then(PlacementReadOffsetElement(boxOffset, 1))
                        .placementLayer {
                            alpha = 0.5f
                            clip = true
                        }.then(PlacementReadOffsetElement(boxOffset, -1)),
            ) { painted = true }
            painted = false

            val clippedAway = paintTagged("layered", setUp = { clipRect(20 * 2, 0, 1, 1) })
            val clippedAwayBelow = paintTagged("layered", setUp = { clipRect(0, 20 * 2, 1, 1) })
            val clippedByTheLayer = paintTagged("boxOutside")
            val flattened =
                paintTagged("layered", setUp = {
                    // No width on the device grid: the offset is undone before the area is flattened onto a pixel edge.
                    translate(-0.3, 0.0)
                    scale(0.0, 1.0)
                })
            val flattenedVertically =
                paintTagged("layered", setUp = {
                    translate(0.0, -0.6)
                    scale(1.0, 0.0)
                })

            assertFalse(painted, "clipped-away or flattened content never paints")
            assertEquals(0, clippedAway.getRGB(10, 10), "a clip rect right of the layer paints nothing")
            assertEquals(0, clippedAwayBelow.getRGB(10, 10), "a clip rect below the layer paints nothing")
            assertEquals(0, clippedByTheLayer.getRGB(10, 10), "the layer's own clip paints nothing outside its box")
            assertEquals(0, flattened.getRGB(10, 10), "an area flattened to zero width paints nothing")
            assertEquals(0, flattenedVertically.getRGB(10, 10), "an area flattened to zero height paints nothing")
        }

    @Test
    fun aLayerThatClipsNothingLetsItsContentPaintOutsideItsBox() =
        runComposeSwingTest {
            val boxOffset = mutableIntStateOf(10)
            setCanvases(
                "layered" to
                    SwingModifier
                        .then(PlacementReadOffsetElement(boxOffset, 1))
                        .placementLayer {}
                        .then(PlacementReadOffsetElement(boxOffset, -1)),
            )

            assertEquals(Color.RED.rgb, paintTagged("layered").getRGB(2, 2))
        }

    @Test
    fun anAlphaAboveOnePaintsUnfadedAndOneBelowZeroOrNaNPaintsNothing() =
        runComposeSwingTest {
            setLayeredSquares("above" to { alpha = 2f }, "below" to { alpha = -1f }, "nan" to { alpha = Float.NaN })

            assertEquals(
                Color.RED.rgb,
                onNodeWithTag("above").captureToImage().getRGB(20, 20),
                "an alpha above one paints unfaded",
            )
            assertEquals(0, onNodeWithTag("below").captureToImage().getRGB(20, 20), "a negative alpha paints nothing")
            assertEquals(0, onNodeWithTag("nan").captureToImage().getRGB(20, 20), "a NaN alpha paints nothing")
        }

    @Test
    fun aZeroScaleOnEitherAxisPaintsNothing() =
        runComposeSwingTest {
            setLayeredSquares("x" to { scaleX = 0f }, "y" to { scaleY = 0f })

            assertEquals(0, onNodeWithTag("x").captureToImage().getRGB(20, 20))
            assertEquals(0, onNodeWithTag("y").captureToImage().getRGB(20, 20))
        }

    @Test
    fun aScaleShrinksTheContentAroundTheTransformOriginOnItsOwnAxis() =
        runComposeSwingTest {
            setLayeredSquares(
                "centered" to {
                    scaleX = 0.5f
                    scaleY = 0.5f
                },
                "topLeft" to {
                    scaleX = 0.5f
                    scaleY = 0.5f
                    transformOrigin = TransformOrigin(0f, 0f)
                },
                "horizontal" to { scaleX = 0.5f },
                "vertical" to { scaleY = 0.5f },
            )

            val centered = onNodeWithTag("centered").captureToImage()
            assertEquals(0, centered.getRGB(1, 1), "a shrink around the center leaves the corner")
            assertEquals(Color.RED.rgb, centered.getRGB(20, 20))
            val topLeft = onNodeWithTag("topLeft").captureToImage()
            assertEquals(Color.RED.rgb, topLeft.getRGB(1, 1))
            assertEquals(0, topLeft.getRGB(40 - 2, 40 - 2))
            val horizontal = onNodeWithTag("horizontal").captureToImage()
            assertEquals(0, horizontal.getRGB(1, 20))
            assertEquals(Color.RED.rgb, horizontal.getRGB(20, 1))
            val vertical = onNodeWithTag("vertical").captureToImage()
            assertEquals(0, vertical.getRGB(20, 1))
            assertEquals(Color.RED.rgb, vertical.getRGB(1, 20))
        }

    @Test
    fun aLayoutModifierNodeWrittenAgainstThePublicApiFadesOverlappingChildrenAsOneImage() =
        runComposeSwingTest {
            setContent {
                Row {
                    Box(
                        modifier =
                            SwingModifier.testTag("layered").preferredSize(40, 40).placementLayer { alpha = 0.5f },
                    ) {
                        Box(modifier = SwingModifier.preferredSize(10, 10).background(Brush.of(Color.RED)))
                        Box(modifier = SwingModifier.preferredSize(10, 10).background(Brush.of(Color.BLUE)))
                    }
                }
            }

            val pixel = onNodeWithTag("layered").captureToImage().getRGB(10 / 2, 10 / 2)

            assertEquals(128.0, (pixel ushr 24).toDouble(), 1.0, "the subtree fades at the declared alpha")
            assertEquals(
                Color.BLUE.rgb and 0xFFFFFF,
                pixel and 0xFFFFFF,
                "the lower child is hidden under the upper one",
            )
        }

    @Test
    fun aDecorationDeclaredBeforeTheLayerIsNotFadedAndOneDeclaredAfterItIs() =
        runComposeSwingTest {
            setContent {
                Row {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag("before")
                                .preferredSize(40, 40)
                                .background(Brush.of(Color.RED))
                                .placementLayer { alpha = 0.5f },
                    )
                    Box(
                        modifier =
                            SwingModifier
                                .testTag("after")
                                .preferredSize(40, 40)
                                .placementLayer { alpha = 0.5f }
                                .background(Brush.of(Color.RED)),
                    )
                }
            }

            assertEquals(Color.RED.rgb, onNodeWithTag("before").captureToImage().getRGB(10, 10))
            val after = onNodeWithTag("after").captureToImage().getRGB(10, 10)
            assertEquals(128.0, (after ushr 24).toDouble(), 1.0)
        }

    @Test
    fun aModifierChangeKeepsTheLayerAndRemovingTheLayerPaintsPlainlyAgain() =
        runComposeSwingTest {
            var color by mutableStateOf(Color.RED)
            var layered by mutableStateOf(true)
            setContent {
                val sized = SwingModifier.testTag("layered").preferredSize(40, 40)
                Row {
                    Box(
                        modifier =
                            (if (layered) sized.placementLayer { alpha = 0.5f } else sized)
                                .background(Brush.of(color)),
                    )
                }
            }
            assertEquals(128.0, (onNodeWithTag("layered").captureToImage().getRGB(10, 10) ushr 24).toDouble(), 1.0)

            color = Color.BLUE
            awaitIdle()
            val changed = onNodeWithTag("layered").captureToImage().getRGB(10, 10)
            assertEquals(128.0, (changed ushr 24).toDouble(), 1.0, "the layer still fades")
            assertEquals(Color.BLUE.rgb and 0xFFFFFF, changed and 0xFFFFFF)

            layered = false
            awaitIdle()
            assertEquals(Color.BLUE.rgb, onNodeWithTag("layered").captureToImage().getRGB(10, 10))
        }

    /**
     * A state read in a layer block is a read of the component's paint: its change repaints the component,
     * invalidates nothing, and the next paint shows the new value.
     */
    @Test
    fun aReadInALayerBlockRepaintsWithoutLayingOutAgain() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val alpha = mutableFloatStateOf(1f)
            val block: PlacementLayerScope.() -> Unit = { this.alpha = alpha.floatValue }
            setWindowContent {
                Row {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag(CONTAINER_TAG)
                                .preferredSize(40, 40)
                                .placementLayer(block)
                                .background(Brush.of(Color.RED)),
                    )
                }
            }
            val box = windowContainer()
            val node = onWindowWithTitle(WINDOW_TITLE).onNodeWithTag(CONTAINER_TAG)
            assertEquals(Color.RED.rgb, node.captureToImage().getRGB(10, 10))
            box.paintImmediately(0, 0, box.width, box.height)
            assertTrue(box.isValidUpToTheValidateRoot(), "the realized box must start valid")

            alpha.floatValue = 0f
            Snapshot.sendApplyNotifications()

            assertEquals(Rectangle(0, 0, box.width, box.height), box.dirtyRegion(), "a changed read must repaint")
            assertTrue(box.isValidUpToTheValidateRoot(), "a layer read must not invalidate anything")
            awaitIdle()
            assertEquals(0, node.captureToImage().getRGB(10, 10), "the next paint skips the invisible content")
        }

    /** A layer on a component that is not a [Layout]'s panel observes its block's reads itself. */
    @Test
    fun aReadInALayerBlockOnACanvasRepaintsWithoutLayingOutAgain() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val alpha = mutableFloatStateOf(1f)
            val block: PlacementLayerScope.() -> Unit = { this.alpha = alpha.floatValue }
            setWindowContent {
                Row {
                    Canvas(
                        modifier = SwingModifier.testTag(CONTAINER_TAG).preferredSize(40, 40).placementLayer(block),
                    ) { drawRect(Color.RED) }
                }
            }
            val canvas = windowContainer()
            val node = onWindowWithTitle(WINDOW_TITLE).onNodeWithTag(CONTAINER_TAG)
            assertEquals(Color.RED.rgb, node.captureToImage().getRGB(10, 10))
            canvas.paintImmediately(0, 0, canvas.width, canvas.height)
            assertTrue(canvas.isValidUpToTheValidateRoot(), "the realized canvas must start valid")

            alpha.floatValue = 0f
            Snapshot.sendApplyNotifications()

            assertEquals(
                Rectangle(0, 0, canvas.width, canvas.height),
                canvas.dirtyRegion(),
                "a changed read must repaint",
            )
            assertTrue(canvas.isValidUpToTheValidateRoot(), "a layer read must not invalidate anything")
            awaitIdle()
            assertEquals(0, node.captureToImage().getRGB(10, 10), "the next paint skips the invisible content")
        }

    @Test
    fun aFadeReusesItsCompositeUntilTheAlphaOrTheGraphicsCompositeChanges() =
        runComposeSwingTest {
            val alpha = mutableFloatStateOf(0.5f)
            setCanvases("layered" to SwingModifier.placementLayer { this.alpha = alpha.floatValue })
            val halfOpaque: Graphics2D.() -> Unit = {
                composite =
                    AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f)
            }

            assertEquals(128.0, paintTagged("layered").alphaAt(10, 10), 1.0)
            assertEquals(128.0, paintTagged("layered").alphaAt(10, 10), 1.0, "a repeated fade")
            assertEquals(64.0, paintTagged("layered", setUp = halfOpaque).alphaAt(10, 10), 1.0, "a changed composite")
            alpha.floatValue = 0.25f
            awaitIdle()
            assertEquals(32.0, paintTagged("layered", setUp = halfOpaque).alphaAt(10, 10), 1.0, "a changed alpha")
        }

    @Test
    fun aFadeOverACompositeWithoutAlphaFadesAtItsOwnAlpha() =
        runComposeSwingTest {
            setCanvases("layered" to SwingModifier.placementLayer { alpha = 0.5f })

            assertEquals(128.0, paintTagged("layered", setUp = { setXORMode(Color.WHITE) }).alphaAt(10, 10), 1.0)
        }

    /** A fade moves nothing off the child's box, so content overflowing the box takes the paint outsets it paints. */
    @Test
    fun aFadeAloneTakesOnlyThePaintOutsetsOfTheOverflow() =
        runComposeSwingTest {
            setContent {
                Column {
                    Box(modifier = SwingModifier.testTag("layered").size(40, 40).placementLayer { alpha = 0.5f }) {
                        Box(modifier = SwingModifier.requiredSize(60, 60))
                    }
                }
            }

            onNodeWithTag("layered").assertProperty(Dimension(60, 60)) { size }
        }

    /** Placing again with the block and box the layer already has repaints nothing. */
    @Test
    fun aPlacementReplayedUnchangedRepaintsNothing() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            setWindowContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Box(
                        modifier =
                            SwingModifier.testTag("layered").preferredSize(40, 40).placementLayer { alpha = 0.5f },
                    )
                }
            }
            val row = windowContainer()
            val layered = onWindowWithTitle(WINDOW_TITLE).onNodeWithTag("layered").fetch<JComponent>()
            row.paintImmediately(0, 0, row.width, row.height)

            row.doLayout()

            assertTrue(layered.dirtyRegion().isEmpty, "an unchanged layer must not repaint: ${layered.dirtyRegion()}")
            assertTrue(row.dirtyRegion().isEmpty, "an unchanged layer must not repaint: ${row.dirtyRegion()}")
        }

    /**
     * A placement that moves the layer's box while its component stays put repaints the component, invalidates
     * nothing, and clips to the moved box.
     */
    @Test
    fun aLayerBoxMovedByAPlacementReadRepaintsOnlyItsComponentAndClipsToTheMovedBox() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val shift = mutableIntStateOf(0)
            setWindowContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag("layered")
                                .preferredSize(40, 40)
                                .then(PlacementReadOffsetElement(shift, 1))
                                .placementLayer {
                                    alpha = 0.5f
                                    clip = true
                                }.then(PlacementReadOffsetElement(shift, -1))
                                .background(Brush.of(Color.RED)),
                    )
                }
            }
            val row = windowContainer()
            val layered = onWindowWithTitle(WINDOW_TITLE).onNodeWithTag("layered").fetch<JComponent>()
            val placed = layered.bounds
            val repaintManager = RepaintManager.currentManager(layered)
            repaintManager.markCompletelyClean(row)
            repaintManager.markCompletelyClean(layered)

            shift.intValue = 20
            Snapshot.sendApplyNotifications()

            assertEquals(placed, layered.bounds, "the content cancels the layer's move")
            assertEquals(Rectangle(0, 0, layered.width, layered.height), layered.dirtyRegion(), "a moved box repaints")
            assertTrue(row.dirtyRegion().isEmpty, "only the component repaints: ${row.dirtyRegion()}")
            assertTrue(layered.isValidUpToTheValidateRoot(), "a moved box must not invalidate anything")
            val image = onWindowWithTitle(WINDOW_TITLE).onNodeWithTag("layered").captureToImage()
            assertEquals(0, image.getRGB(1, 10), "the clip follows the moved box")
            assertEquals(128.0, image.alphaAt(20 + 1, 10), 1.0, "the content fades inside the moved box")
        }

    @Test
    fun aNodeThatStopsPlacingWithALayerPaintsPlainlyAgain() =
        runComposeSwingTest {
            val layer = mutableStateOf<(PlacementLayerScope.() -> Unit)?>({ alpha = 0.5f })
            setContent {
                Row {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag("layered")
                                .preferredSize(40, 40)
                                .then(PlacementReadLayerElement(layer, relative = false))
                                .background(Brush.of(Color.RED)),
                    )
                }
            }
            assertEquals(128.0, onNodeWithTag("layered").captureToImage().alphaAt(10, 10), 1.0)

            layer.value = null
            awaitIdle()

            assertEquals(Color.RED.rgb, onNodeWithTag("layered").captureToImage().getRGB(10, 10))
        }

    /**
     * A node placed without a layer clears the layer's decorator, or the component stays a painting origin and
     * non-opaque. No other decoration is declared here, so the layer is the only thing either answer can come from.
     */
    @Test
    fun aNodeThatStopsPlacingWithALayerClearsTheLayerDecorator() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val layer = mutableStateOf<(PlacementLayerScope.() -> Unit)?>({ alpha = 0.5f })
            setWindowContent {
                Row {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag("layered")
                                .preferredSize(40, 40)
                                .opaque(true)
                                .then(PlacementReadLayerElement(layer, relative = false)),
                    )
                }
            }
            val component = onWindowWithTitle(WINDOW_TITLE).onNodeWithTag("layered").fetch<ConstrainedPanel>()
            assertTrue(
                component.isPaintingOrigin(),
                "a layer set as the node's decorator makes the component its origin",
            )
            assertFalse(component.isOpaque, "with the decorator set, the component reports non-opaque")

            layer.value = null
            awaitIdle()

            assertFalse(
                component.isPaintingOrigin(),
                "once placed without a layer, the decorator is cleared, or the component stays a painting origin",
            )
            assertTrue(component.isOpaque, "with the decorator cleared, the component is opaque again")
        }

    @Test
    fun aRelativePlacementWithALayerMirrorsUnderARightToLeftParent() =
        runComposeSwingTest {
            setContent {
                Row(modifier = containerModifier(40 * 2, 40, ComponentOrientation.RIGHT_TO_LEFT)) {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag("layered")
                                .then(ClipLayerElement(relative = true, width = 40))
                                .preferredSize(10, 10)
                                .background(Brush.of(Color.RED)),
                    )
                }
            }

            val layered = onNodeWithTag("layered").fetch<JComponent>()
            assertEquals(40 * 2 - 10, layered.x, "the content sits at the trailing edge of the row and of its layer")
            assertEquals(Color.RED.rgb, onNodeWithTag("layered").captureToImage().getRGB(10 / 2, 10 / 2))
        }

    @Test
    fun aPlacementWithALayerAnswersABaselineQuery() =
        runComposeSwingTest {
            setContent {
                Row(modifier = containerModifier(CHILD_WIDTH * CHILD_COUNT, CHILD_HEIGHT * 2)) {
                    for (relative in listOf(false, true)) {
                        DecoratedBaselineChild(
                            10,
                            SwingModifier.alignByBaseline().then(ClipLayerElement(relative)),
                        )
                    }
                    DecoratedBaselineChild(30, SwingModifier.alignByBaseline())
                }
            }

            assertEquals(
                listOf(30 - 10, 30 - 10, 0),
                childBounds().map { it.y },
                "a child placed with a layer, from the left or the leading edge, reports its content's baseline",
            )
        }

    @Test
    fun aPlacementThatTakesChangesOrDropsItsLayerRepaintsOnlyTheComponent() = assertLayerChangesRepaintOnly(false)

    @Test
    fun aRelativePlacementThatTakesChangesOrDropsItsLayerRepaintsOnlyTheComponent() =
        assertLayerChangesRepaintOnly(true)

    /**
     * A placement read that first places with a layer, hands it a new block instance, or places without it, at
     * unchanged bounds, repaints the component, invalidates nothing and leaves its container clean; the pixels
     * follow.
     */
    private fun assertLayerChangesRepaintOnly(relative: Boolean) =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val block = mutableStateOf<(PlacementLayerScope.() -> Unit)?>(null)
            setWindowContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag("layered")
                                .preferredSize(40, 40)
                                .then(PlacementReadLayerElement(block, relative))
                                .background(Brush.of(Color.RED)),
                    )
                }
            }
            val row = windowContainer()
            val node = onWindowWithTitle(WINDOW_TITLE).onNodeWithTag("layered")
            val layered = node.fetch<JComponent>()
            val placed = layered.bounds
            val repaintManager = RepaintManager.currentManager(layered)

            val halfAlpha: PlacementLayerScope.() -> Unit = { alpha = 0.5f }
            val sameAlphaNewInstance: PlacementLayerScope.() -> Unit = { alpha = 0.5f }
            val steps = listOf(halfAlpha to 128.0, sameAlphaNewInstance to 128.0, null to 255.0)
            for ((next, expectedAlpha) in steps) {
                repaintManager.markCompletelyClean(row)
                repaintManager.markCompletelyClean(layered)
                block.value = next
                Snapshot.sendApplyNotifications()

                assertEquals(placed, layered.bounds)
                assertEquals(Rectangle(0, 0, placed.width, placed.height), layered.dirtyRegion(), "repaints")
                assertTrue(row.dirtyRegion().isEmpty, "only the component repaints: ${row.dirtyRegion()}")
                assertTrue(layered.isValidUpToTheValidateRoot(), "a layer change must not invalidate anything")
                assertEquals(expectedAlpha, node.captureToImage().alphaAt(10, 10), 1.0)
            }
        }

    @Test
    fun aPropertyTheBlockNoLongerSetsPaintsAtItsDefault() =
        runComposeSwingTest {
            var faded by mutableStateOf(true)
            val block: PlacementLayerScope.() -> Unit = { if (faded) alpha = 0.5f }
            setContent {
                Row {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag("layered")
                                .preferredSize(40, 40)
                                .placementLayer(block)
                                .background(Brush.of(Color.RED)),
                    )
                }
            }
            assertEquals(128.0, onNodeWithTag("layered").captureToImage().alphaAt(10, 10), 1.0)

            faded = false
            awaitIdle()

            assertEquals(Color.RED.rgb, onNodeWithTag("layered").captureToImage().getRGB(10, 10))
        }

    /** Composes a red 40-pixel square for each pair, tagged by its first and placed with its second layer. */
    private fun ComposeSwingTest.setLayeredSquares(vararg squares: Pair<String, PlacementLayerScope.() -> Unit>) {
        setContent {
            Row {
                for ((tag, block) in squares) {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag(tag)
                                .preferredSize(40, 40)
                                .placementLayer(block)
                                .background(Brush.of(Color.RED)),
                    )
                }
            }
        }
    }

    private fun BufferedImage.alphaAt(
        x: Int,
        y: Int,
    ): Double = (getRGB(x, y) ushr 24).toDouble()

    /**
     * Two overlapping squares faded as one image hide the lower one where they overlap, and the whole is the
     * unfaded image drawn at the declared alpha - including the antialiased edges of squares placed at a
     * sub-pixel offset, which a buffer off the destination's pixel grid would move.
     */
    private fun assertFadeIsOneImage(scale: Double) =
        runComposeSwingTest {
            setCanvases("plain" to SwingModifier, "layered" to SwingModifier.placementLayer { alpha = 0.5f }) { g ->
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g.color = Color.RED
                g.fill(Rectangle2D.Double(2.25, 2.25, 10.5, 10.5))
                g.color = Color.BLUE
                g.fill(Rectangle2D.Double(7.75, 7.75, 10.5, 10.5))
            }
            val unfaded = paintTagged("plain", scale = scale)
            val faded = paintTagged("layered", scale = scale)

            assertImagesPixelPerfect(
                renderImage(unfaded.width, unfaded.height) {
                    it.composite = AlphaComposite.SrcOver.derive(0.5f)
                    it.drawImage(unfaded, 0, 0, null)
                },
                faded,
            )
            val overlap = (10 * scale).toInt()
            assertEquals(
                Color.BLUE.rgb and 0xFFFFFF,
                faded.getRGB(overlap, overlap) and 0xFFFFFF,
                "the lower square is hidden",
            )
        }

    /**
     * Composes a 20-pixel canvas for each pair, tagged by its first and modified by its second, drawing [content],
     * a red square filling the canvas by default.
     */
    private fun ComposeSwingTest.setCanvases(
        vararg canvases: Pair<String, SwingModifier>,
        content: (Graphics2D) -> Unit = {
            it.color = Color.RED
            it.fillRect(0, 0, 20, 20)
        },
    ) {
        setContent {
            Row {
                for ((tag, modifier) in canvases) {
                    Canvas(modifier = SwingModifier.testTag(tag).preferredSize(20, 20).then(modifier)) {
                        content(graphics)
                    }
                }
            }
        }
    }

    /**
     * Paints the component tagged [tag] onto an image of [scale] device pixels per pixel, at a sub-pixel offset
     * of the device grid, after [setUp] prepares the graphics.
     */
    private fun ComposeSwingTest.paintTagged(
        tag: String,
        scale: Double = 1.0,
        setUp: Graphics2D.() -> Unit = {},
    ): BufferedImage {
        val component = onNodeWithTag(tag).fetch<JComponent>()
        val image = BufferedImage((20 * scale).toInt(), (20 * scale).toInt(), BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            graphics.scale(scale, scale)
            graphics.translate(0.3, 0.6)
            graphics.setUp()
            component.printAll(graphics)
        } finally {
            graphics.dispose()
        }
        return image
    }
}

/** Places its content behind a clip, from the leading edge where [relative], reporting [width] or its own width. */
private data class ClipLayerElement(
    private val relative: Boolean,
    private val width: Int? = null,
) : LayoutModifierNodeElement<ClipLayerNode>() {
    override fun create(): ClipLayerNode = ClipLayerNode(relative, width)

    override fun update(node: ClipLayerNode) = Unit
}

private class ClipLayerNode(
    private val relative: Boolean,
    private val width: Int?,
) : LayoutModifierNode() {
    override val name: String get() = "clipLayer"

    override val declaredValues: Map<String, Any?> get() = emptyMap()

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        val block: PlacementLayerScope.() -> Unit = { clip = true }
        return layout(width ?: placeable.width, placeable.height) {
            if (relative) {
                placeable.placeRelativeWithLayer(0, 0, layerBlock = block)
            } else {
                placeable.placeWithLayer(0, 0, layerBlock = block)
            }
        }
    }
}

/** Places its content [sign] times the offset [offset] holds, read while placing. */
private data class PlacementReadOffsetElement(
    private val offset: State<Int>,
    private val sign: Int,
) : LayoutModifierNodeElement<PlacementReadOffsetNode>() {
    override fun create(): PlacementReadOffsetNode = PlacementReadOffsetNode(offset, sign)

    override fun update(node: PlacementReadOffsetNode) = Unit
}

private class PlacementReadOffsetNode(
    private val offset: State<Int>,
    private val sign: Int,
) : LayoutModifierNode() {
    override val name: String get() = "placementReadOffset"

    override val declaredValues: Map<String, Any?> get() = emptyMap()

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.place(sign * offset.value, 0) }
    }
}
