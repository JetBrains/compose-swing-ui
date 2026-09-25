package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.graphics.TransformOrigin
import org.jetbrains.compose.swing.foundation.graphics.channelDifference
import org.jetbrains.compose.swing.foundation.graphics.layoutBounds
import org.jetbrains.compose.swing.foundation.graphics.renderImage
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.componentOrientation
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.test.screenshot.differingPixelBounds
import org.jetbrains.compose.swing.withRecordedRepaints
import java.awt.Color
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.Insets
import java.awt.Rectangle
import java.awt.geom.AffineTransform
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import javax.swing.JComponent
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlacementRotationTest {
    @Test
    fun artworkAndClipFollowTheSameBoxAcrossRotationsScalesAndPivots() =
        runComposeSwingTest {
            var values by mutableStateOf(RotationValues())
            val placements = ArrayList<Rectangle>()
            val sizes = ArrayList<Dimension>()
            setContent {
                Row {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag("layer")
                                .size(160, 88)
                                .onPlaced { placements += it }
                                .onSizeChanged { sizes += it }
                                .placementLayer { values.applyTo(this) },
                    ) {
                        Canvas(modifier = SwingModifier.requiredSize(180, 104), renderingHints = null) {
                            drawRect(Color.RED)
                            drawRect(Color.BLUE, 20f, 12f, 30f, 24f)
                        }
                    }
                    Box(modifier = SwingModifier.testTag("sibling").size(20, 20))
                }
            }
            val layer = onNodeWithTag("layer").fetch<JComponent>()
            val sibling = onNodeWithTag("sibling").fetch<JComponent>()
            val bounds = layer.layoutBounds
            val siblingBounds = sibling.layoutBounds
            val angles = listOf(0f, -29f, 29f, -45f, 45f, -58f, 58f, -90f, 90f, 180f, -150f)
            val scales = listOf(0.8f to 0.8f, 1f to 1f, 1.4f to 1.4f, 0.8f to 1.4f, -1f to 0.8f)
            val pivots = listOf(0f, 0.14f, 0.2f, 0.5f, 1f, -0.25f, 1.25f)
            for (angle in angles) {
                for ((scaleX, scaleY) in scales) {
                    for (pivot in pivots) {
                        for (clipped in listOf(false, true)) {
                            values = RotationValues(angle, scaleX, scaleY, pivot, clipped)
                            awaitIdle()
                            val outsets = (layer as Decoratable).decoration.paintOutsets()
                            val paintBounds = Rectangle(-outsets.left, -outsets.top, layer.width, layer.height)
                            assertEquals(values.paintBounds(), paintBounds, "$values paint bounds")
                            assertNull(
                                differingPixelBounds(
                                    values.artwork(paintBounds),
                                    onNodeWithTag("layer").captureToImage(),
                                ),
                                "$values artwork",
                            )
                            assertEquals(bounds, layer.layoutBounds, "$values layout bounds")
                            assertEquals(siblingBounds, sibling.layoutBounds, "$values sibling layout bounds")
                            assertHitsTheTurnedBox(layer, values, paintBounds)
                        }
                    }
                }
            }
            assertTrue(
                placements.all { it == bounds },
                "paint outsets change must not report a layout change: $placements",
            )
            assertTrue(sizes.all { it == bounds.size }, "paint outsets change must not report a size change: $sizes")
        }

    @Test
    fun aTurningInnerBoxKeepsItsChildrenWhereItsLastPassPlacedThem() =
        runComposeSwingTest {
            var angle by mutableFloatStateOf(0f)
            setContent {
                Box {
                    Box(modifier = SwingModifier.testTag("inner").size(60, 40).placementLayer { rotationZ = angle }) {
                        Box(modifier = SwingModifier.testTag("first").size(20, 20))
                        Box(modifier = SwingModifier.testTag("second").size(10, 10).align(Alignment.BottomEnd))
                    }
                }
            }
            val inner = onNodeWithTag("inner").fetch<JComponent>()
            val children = listOf("first", "second").map { onNodeWithTag(it).fetch<JComponent>() }
            val placed = listOf(Rectangle(0, 0, 20, 20), Rectangle(50, 30, 10, 10))
            assertEquals(placed, children.map { it.layoutBounds })

            withRecordedRepaints { recorder ->
                for (next in listOf(15f, 30f, 45f, 90f, 0f)) {
                    angle = next
                    awaitIdle()
                    assertTrue(
                        next == 0f || (inner as Decoratable).decoration.paintOutsets() != Insets(0, 0, 0, 0),
                        "at $next the turn takes paint outsets",
                    )
                    assertEquals(placed, children.map { it.layoutBounds }, "at $next the children stay put")
                }
                assertEquals(0, recorder.relayoutsOver(inner), "a layer tick lays nothing out")
            }
        }

    @Test
    fun aRepaintWithNothingChangedRunsNoLayerBlock() =
        runComposeSwingTest {
            var runs = 0
            val block: PlacementLayerScope.() -> Unit = {
                runs++
                rotationZ = 30f
            }
            setContent {
                Row {
                    Box(modifier = SwingModifier.testTag("layered").size(40, 40).placementLayer(block))
                }
            }
            val node = onNodeWithTag("layered")
            node.captureToImage()
            val placedRuns = runs

            node.captureToImage()
            node.captureToImage()

            assertEquals(placedRuns, runs)
        }

    /** The layer is hit at the turned center of its box and on the unclipped overflow, and not past the child. */
    private fun assertHitsTheTurnedBox(
        layer: JComponent,
        values: RotationValues,
        paintBounds: Rectangle,
    ) {
        val center = values.point(80.0, 44.0)
        val overflow = values.point(-8.0, 44.0)
        val pastTheChild = values.point(-14.0, 44.0)
        assertTrue(
            layer.contains(center.x.toInt() - paintBounds.x, center.y.toInt() - paintBounds.y),
            "$values hit at the turned center of the box",
        )
        assertEquals(
            !values.clipped,
            layer.contains(overflow.x.toInt() - paintBounds.x, overflow.y.toInt() - paintBounds.y),
            "$values: past the turned edge of the box, the child's overflow is hit only while the layer leaves it " +
                "unclipped",
        )
        assertFalse(
            layer.contains(pastTheChild.x.toInt() - paintBounds.x, pastTheChild.y.toInt() - paintBounds.y),
            "$values hit past the turned edge of the child",
        )
    }

    /**
     * A layer read that grows the paint outsets grows the bounds of the component and of each decorated ancestor it
     * spills past, and leaves every one of them valid: the layout bounds are unchanged, so there is
     * nothing to lay out again.
     */
    @Test
    fun aLayerReadGrowingThePaintOutsetsInvalidatesNothing() =
        runComposeSwingTest {
            val scale = mutableFloatStateOf(1f)
            val block: PlacementLayerScope.() -> Unit = { scaleX = scale.floatValue }
            setContent {
                Column {
                    Row(modifier = SwingModifier.testTag("row")) {
                        Box(modifier = SwingModifier.testTag("layered").preferredSize(40, 40).placementLayer(block)) {
                            SizedChild(0)
                        }
                    }
                }
            }
            val row = onNodeWithTag("row").fetch<JComponent>()
            val layered = onNodeWithTag("layered").fetch<JComponent>()
            val inner = layered.getComponent(0)
            assertTrue(inner.isValidUpToTheValidateRoot(), "the realized tree must start valid")

            scale.floatValue = 2f
            Snapshot.sendApplyNotifications()

            assertEquals(Rectangle(0, 0, 80, 40), layered.bounds, "the layered box grows by its paint outsets")
            assertEquals(Rectangle(-20, 0, 80, 40), row.bounds, "the row grows to hold the child's paint outsets")
            assertTrue(inner.isValidUpToTheValidateRoot(), "growing the paint outsets must invalidate nothing")
        }

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
            assertEquals(
                Rectangle(-10, 0, 60, 40),
                onNodeWithTag("layered").fetch<JComponent>().bounds,
                "the scaled layer's bounds hold the child's scaled overflow",
            )
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
            assertEquals(
                Rectangle(-25, 0, 90, 40),
                onNodeWithTag("outer").fetch<JComponent>().bounds,
                "the outer layer holds the inner layer's scaled overflow, scaled again",
            )
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
                        modifier = SwingModifier.testTag("row").preferredSize(40, 40).placementLayer { scaleX = 0.75f },
                    ) {
                        Box(modifier = SwingModifier.requiredSize(80, 40))
                    }
                }
                Box {
                    Column(
                        modifier =
                            SwingModifier
                                .testTag("column")
                                .preferredSize(40, 40)
                                .placementLayer { scaleX = 0.75f },
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

            assertEquals(
                Insets(0, 10, 0, 10),
                paintOutsetsOf("row"),
                "the row must center the overflow of its single, constrained child",
            )
            assertEquals(Insets(0, 10, 0, 10), paintOutsetsOf("column"), "and the column must do the same")
            assertEquals(
                Insets(0, 0, 0, 25),
                paintOutsetsOf("custom"),
                "a policy placing an unconstrained child at its own offset must not center it",
            )
        }

    @Test
    fun aScaledLayerHoldsTheScaledShadowOfItsChild() =
        runComposeSwingTest {
            setContent {
                Row {
                    Box(modifier = SwingModifier.testTag("layered").size(40, 40).placementLayer { scaleX = 2f }) {
                        Canvas(
                            modifier = SwingModifier.testTag("child").preferredSize(40, 40).shadow(4, Color.BLACK),
                        ) {}
                    }
                }
            }

            assertEquals(Insets(14, 14, 14, 14), paintOutsetsOf("child"), "the shadow spreads 14 past each side")
            assertEquals(
                Insets(14, 48, 14, 48),
                paintOutsetsOf("layered"),
                "scaled by 2 about the box's center, the shadow's -14..54 spans -48..88; top and bottom stay unscaled",
            )
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
                                    .componentOrientation(orientation)
                                    .placementLayer { scaleX = 0.75f },
                            content = { Box(modifier = SwingModifier.requiredSize(80, 40)) },
                            measurePolicy = { measurables, _ ->
                                val placeable = measurables.single().measure(Constraints())
                                layout(40, 40) { placeable.placeRelative(0, 0) }
                            },
                        )
                    }
                }
            }

            assertEquals(
                Insets(0, 0, 0, 25),
                paintOutsetsOf("leftToRight"),
                "placed from the left, the child spans 0..80, scaled to 5..65",
            )
            assertEquals(
                Insets(0, 25, 0, 0),
                paintOutsetsOf("rightToLeft"),
                "mirrored, the child spans -40..40, scaled to -25..35",
            )
        }

    private fun ComposeSwingTest.paintOutsetsOf(tag: String): Insets =
        (onNodeWithTag(tag).fetch<JComponent>() as Decoratable).decoration.paintOutsets()

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
            assertEquals(
                Rectangle(-10, 0, 60, 40),
                onNodeWithTag("layered").fetch<JComponent>().bounds,
                "a fading layer still holds the scaled overflow",
            )

            val image = onNodeWithTag("layered").captureToImage()
            assertEquals(
                List(3) { Color(255, 0, 0, 128).rgb },
                listOf(2, 30, 57).map { image.getRGB(it, 20) },
                "the overflow left of the box, the box and the overflow right of it must paint at half alpha",
            )
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
            assertEquals(
                Rectangle(-10, 0, 60, 40),
                onNodeWithTag("layered").fetch<JComponent>().bounds,
                "a scale and a fade in separate layers hold the scaled overflow",
            )

            val image = onNodeWithTag("layered").captureToImage()
            assertEquals(
                List(3) { Color(255, 0, 0, 128).rgb },
                listOf(2, 30, 57).map { image.getRGB(it, 20) },
                "the overflow left of the box, the box and the overflow right of it must paint at half alpha",
            )
        }

    @Test
    fun aParentResizingAScaledChildLaysItOutAtTheNewSize() = assertResizedUnderALayer(clip = false)

    @Test
    fun aParentResizingAScaledAndClippedChildLaysItOutAtTheNewSize() = assertResizedUnderALayer(clip = true)

    /**
     * A parent that resizes a child placed with a layer lays that child out at its new layout size, whatever
     * the paint outsets the layer grows it by.
     */
    private fun assertResizedUnderALayer(clip: Boolean) =
        runComposeSwingTest {
            var width by mutableIntStateOf(40)
            setContent {
                Layout(
                    content = {
                        Box(
                            modifier =
                                SwingModifier.testTag("layered").placementLayer {
                                    scaleX = 2f
                                    this.clip = clip
                                },
                            propagateMinConstraints = true,
                        ) { SizedChild(0) }
                    },
                    measurePolicy = { measurables, _ ->
                        val placeable = measurables.single().measure(Constraints.fixed(width, CHILD_HEIGHT))
                        layout(width, CHILD_HEIGHT) { placeable.place(0, 0) }
                    },
                )
            }
            val inner = onNodeWithTag("layered").fetch<JComponent>().getComponent(0)
            assertEquals(40, inner.width)

            width = 60
            awaitIdle()

            assertEquals(60, inner.width, "the child's own layout must run at its new size")
            assertTrue(inner.isValidUpToTheValidateRoot(), "the laid out tree must be valid")
        }

    /**
     * Resizing a scaled and clipped child moves the paint outsets of everything inside it, and following those
     * outsets measures nothing again: the child's content is measured once for its new size.
     */
    @Test
    fun aParentResizingAScaledAndClippedChildMeasuresItsContentOnce() =
        runComposeSwingTest {
            var width by mutableIntStateOf(40)
            var contentMeasures = 0
            setContent {
                Layout(
                    content = {
                        Box(
                            modifier =
                                SwingModifier.placementLayer {
                                    scaleX = 2f
                                    clip = true
                                },
                            propagateMinConstraints = true,
                        ) {
                            Layout(
                                content = { SizedChild(0) },
                                measurePolicy = { measurables, constraints ->
                                    contentMeasures++
                                    val placeable = measurables.single().measure(constraints)
                                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                                },
                            )
                        }
                    },
                    measurePolicy = { measurables, _ ->
                        val placeable = measurables.single().measure(Constraints.fixed(width, CHILD_HEIGHT))
                        layout(width, CHILD_HEIGHT) { placeable.place(0, 0) }
                    },
                )
            }
            contentMeasures = 0

            width = 60
            awaitIdle()

            assertEquals(1, contentMeasures, "following the moved paint outsets must not measure the content again")
        }

    /** A fade and a turn set in one block paint what a fading layer holding a turning layer paints. */
    @Test
    fun aFadeInARotatingLayerFadesTheTurnedContent() =
        runComposeSwingTest {
            setContent {
                Row {
                    MarkedCanvas(
                        SwingModifier.testTag("one").placementLayer {
                            alpha = 0.5f
                            rotationZ = 30f
                            scaleX = 1.2f
                        },
                    )
                    MarkedCanvas(
                        SwingModifier
                            .testTag("nested")
                            .placementLayer { alpha = 0.5f }
                            .placementLayer {
                                rotationZ = 30f
                                scaleX = 1.2f
                            },
                    )
                }
            }

            // One block records the turned content, the nested layers record a layer that turns it: the mark's edge
            // rasterizes on two pixels differently.
            assertImagesPixelPerfect(
                onNodeWithTag("nested").captureToImage(),
                onNodeWithTag("one").captureToImage(),
                maxDifferentPixels = 2,
            )
        }

    /** A shadow declared inside a half-turned layer turns with the content, its offset included. */
    @Test
    fun aShadowInsideAHalfTurnedLayerTurnsWithIt() = assertShadowTurnsWithTheLayer(quarterTurns = 2, 40, 24)

    /** The same holds for a quarter turn, which carries each axis of the offset onto the other. */
    @Test
    fun aShadowInsideAQuarterTurnedLayerTurnsWithIt() = assertShadowTurnsWithTheLayer(quarterTurns = 1, 32, 32)

    /**
     * Compares a canvas of [width] by [height] turned clockwise by [quarterTurns] under a shadow with the upright
     * one turned alike. The blur runs along the device's axes and rounds toward the side it runs from, so a turned
     * falloff pixel may differ by one level.
     */
    private fun assertShadowTurnsWithTheLayer(
        quarterTurns: Int,
        width: Int,
        height: Int,
    ) = runComposeSwingTest {
        setContent {
            Row {
                MarkedCanvas(
                    SwingModifier.testTag("upright").shadow(3, Color.BLACK, offsetX = 6, offsetY = 2),
                    width,
                    height,
                )
                MarkedCanvas(
                    SwingModifier
                        .testTag("turned")
                        .placementLayer { rotationZ = 90f * quarterTurns }
                        .shadow(3, Color.BLACK, offsetX = 6, offsetY = 2),
                    width,
                    height,
                )
            }
        }
        val upright = onNodeWithTag("upright").captureToImage()
        val sideways = quarterTurns % 2 == 1
        val expectedWidth = if (sideways) upright.height else upright.width
        val expectedHeight = if (sideways) upright.width else upright.height
        val expected =
            renderImage(expectedWidth, expectedHeight) {
                it.translate(expectedWidth / 2.0, expectedHeight / 2.0)
                it.rotate(Math.PI / 2 * quarterTurns)
                it.translate(-upright.width / 2.0, -upright.height / 2.0)
                it.drawImage(upright, 0, 0, null)
            }
        val turned = onNodeWithTag("turned").captureToImage()

        assertEquals(Dimension(expected.width, expected.height), Dimension(turned.width, turned.height))
        assertTrue(turned.channelDifference(expected) <= 1, "a turned pixel differs by more than one level")
    }

    @Test
    fun sharedMatrixMatchesIndependentCornerMathForNonzeroBoxes() {
        val box = Rectangle2D.Double(-17.0, 23.0, 160.0, 88.0)
        for (angle in listOf(-150f, -90f, -58f, -45f, -29f, 0f, 29f, 45f, 58f, 90f, 180f)) {
            for (pivot in listOf(-0.25f, 0f, 0.14f, 0.2f, 0.5f, 1f, 1.25f)) {
                val values = RotationValues(angle, -0.8f, 1.4f, pivot)
                val matrix = TransformOrigin(pivot, 0.5f).createTransform(box, values.scaleX, values.scaleY, angle)
                for (point in listOf(Point2D.Double(-17.0, 23.0), Point2D.Double(143.0, 111.0))) {
                    val expected = values.point(point.x + 17.0, point.y - 23.0)
                    val actual = matrix.transform(point, null)
                    assertEquals(expected.x - 17.0, actual.x, 0.00001)
                    assertEquals(expected.y + 23.0, actual.y, 0.00001)
                }
            }
        }
    }
}

/** A [width] by [height] canvas, red with a blue mark in its top left corner, so a turn or a mirror shows. */
@Composable
private fun MarkedCanvas(
    modifier: SwingModifier,
    width: Int = 40,
    height: Int = 24,
) {
    Canvas(modifier = modifier.preferredSize(width, height), renderingHints = null) {
        drawRect(Color.RED)
        drawRect(Color.BLUE, 4f, 4f, 10f, 6f)
    }
}

private data class RotationValues(
    val rotation: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val pivot: Float = 0.5f,
    val clipped: Boolean = false,
) {
    fun applyTo(scope: PlacementLayerScope) {
        scope.rotationZ = rotation
        scope.scaleX = scaleX
        scope.scaleY = scaleY
        scope.transformOrigin = TransformOrigin(pivot, 0.5f)
        scope.clip = clipped
    }

    fun point(
        x: Double,
        y: Double,
    ): Point2D.Double {
        val radians = Math.toRadians(rotation.toDouble())
        val pivotX = pivot.toDouble() * 160
        val dx = (x - pivotX) * scaleX
        val dy = (y - 44) * scaleY
        val sine = sin(radians).let { if (abs(it) < 0.000001) 0.0 else it }
        val cosine = cos(radians).let { if (abs(it) < 0.000001) 0.0 else it }
        return Point2D.Double(pivotX + dx * cosine - dy * sine, 44 + dx * sine + dy * cosine)
    }
}

/** Where [RotationValues.applyTo] turns the 160 by 88 box, from its own origin, as [RotationValues.point] maps it. */
private fun RotationValues.transform(): AffineTransform {
    val origin = point(0.0, 0.0)
    val x = point(1.0, 0.0)
    val y = point(0.0, 1.0)
    return AffineTransform(x.x - origin.x, x.y - origin.y, y.x - origin.x, y.y - origin.y, origin.x, origin.y)
}

/** The paint bounds of the turned artwork, around the box they never shrink below. */
private fun RotationValues.paintBounds(): Rectangle {
    val source = if (clipped) Rectangle(0, 0, 160, 88) else Rectangle(-10, -8, 180, 104)
    return transform().createTransformedShape(source).bounds.union(Rectangle(0, 0, 160, 88))
}

/**
 * The artwork drawn straight through [transform], on an image covering [paintBounds] from the box's origin: the
 * canvas, clipped to its own bounds as Swing clips a child, with a marker off its center.
 */
private fun RotationValues.artwork(paintBounds: Rectangle): BufferedImage =
    renderImage(paintBounds.width, paintBounds.height) { graphics ->
        graphics.translate(-paintBounds.x, -paintBounds.y)
        graphics.transform(transform())
        if (clipped) graphics.clipRect(0, 0, 160, 88)
        graphics.clipRect(-10, -8, 180, 104)
        graphics.color = Color.RED
        graphics.fillRect(-10, -8, 180, 104)
        graphics.color = Color.BLUE
        graphics.fillRect(10, 4, 30, 24)
    }
