package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.graphics.Brush
import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Color
import java.awt.ComponentOrientation
import java.awt.GraphicsEnvironment
import java.awt.Insets
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A [MeasurePolicy] placing a child with a layer, which wraps the child's whole decoration. */
class PolicyPlacementLayerTest {
    @Test
    fun aPolicyLayerFadesTheDecorationTheChildDeclares() =
        runComposeSwingTest {
            setContent { LayeredChild { { alpha = 0.5f } } }

            val pixel = onNodeWithTag(LAYERED_TAG).captureToImage().getRGB(20, 10)
            assertEquals(128.0, (pixel ushr 24).toDouble(), 1.0, "the background inside the layer fades")
            assertEquals(Color.RED.rgb and 0xFFFFFF, pixel and 0xFFFFFF)
        }

    /**
     * The layer turns what the child paints about the extent it measured to, the box of its outermost layout
     * modifier: a start padding of 20 leaves the child's own layout bounds the end half of that box, and the
     * background declared before the padding paints at the whole of it. A quarter turn about the box's center puts
     * the background 10 past the layout bounds above, below and before them, and a changed modifier keeps the layer
     * outside it.
     */
    @Test
    fun aPolicyLayerTurnsTheBoxOfTheChildsOutermostLayoutModifier() =
        runComposeSwingTest {
            var color by mutableStateOf(Color.RED)
            setContent { LayeredChild(paddingStart = 20, color = color) { QuarterTurn } }

            assertEquals(
                Insets(10, 10, 10, 0),
                layeredOutsets(),
                "the turned box paints past the layout bounds above, below and before them",
            )
            val image = onNodeWithTag(LAYERED_TAG).captureToImage()
            assertEquals(Color.RED.rgb, image.getRGB(5, 2), "the turned background paints above and before the child")
            assertEquals(0, image.getRGB(25, 5), "the turned background leaves the child's top end unpainted")

            color = Color.BLUE
            awaitIdle()

            assertEquals(Insets(10, 10, 10, 0), layeredOutsets(), "the changed background stays inside the layer")
            assertEquals(
                Color.BLUE.rgb,
                onNodeWithTag(LAYERED_TAG).captureToImage().getRGB(5, 2),
                "the changed background paints",
            )
        }

    /** A quarter turn of the 40 by 20 child paints 10 past its layout bounds above and below. */
    @Test
    fun aPolicyLayerTurnsTheChildAboutTheExtentItMeasuredAndTakesPaintOutsets() =
        runComposeSwingTest {
            setContent { LayeredChild { QuarterTurn } }

            assertEquals(Insets(10, 0, 10, 0), layeredOutsets(), "the quarter-turned child takes 10 px above and below")
            val image = onNodeWithTag(LAYERED_TAG).captureToImage()
            assertEquals(Color.RED.rgb, image.getRGB(20, 2), "the turned child paints above its layout bounds")
            assertEquals(0, image.getRGB(2, 20), "the turned child leaves the ends of its layout bounds unpainted")
        }

    /** An eighth turn of the 40 by 20 child takes the outsets of its turned corners, about the box's center. */
    @Test
    fun aPolicyLayerTurnedAnEighthTakesTheOutsetsOfItsTurnedCorners() =
        runComposeSwingTest {
            setContent { LayeredChild { EighthTurn } }

            assertEquals(Insets(12, 2, 12, 2), layeredOutsets(), "the eighth-turned corners take the outsets")
        }

    /**
     * A clipping, doubling layer takes the outsets of its doubled box, not of a shadow past that box: the clip cuts
     * the shadow away before the scale grows the box.
     */
    @Test
    fun aClippingScaledPolicyLayerTakesTheOutsetsOfItsScaledBoxOnly() =
        runComposeSwingTest {
            setContent { LayeredChild(shadowRadius = 8) { ClipAndDouble } }

            assertEquals(
                Insets(10, 20, 10, 20),
                layeredOutsets(),
                "the doubled box takes the outsets, and the shadow is clipped away",
            )
        }

    @Test
    fun placingTheChildWithoutALayerRemovesTheLayer() =
        runComposeSwingTest {
            var layered by mutableStateOf(true)
            setContent { LayeredChild { if (layered) QuarterTurn else null } }
            assertEquals(Insets(10, 0, 10, 0), layeredOutsets(), "the quarter-turned child takes 10 px above and below")

            layered = false
            awaitIdle()

            assertEquals(Insets(0, 0, 0, 0), layeredOutsets(), "the child placed plainly paints within its bounds")
            assertEquals(Color.RED.rgb, onNodeWithTag(LAYERED_TAG).captureToImage().getRGB(2, 10))
        }

    /**
     * A child moved, mid-composition, from a policy that places it with a layer to a plain container loses the
     * layer: placed without one there, its own decoration no longer carries it.
     */
    @Test
    fun aChildMovedFromALayeredPolicyToAPlainContainerLosesTheLayer() =
        runComposeSwingTest {
            var underLayeredPolicy by mutableStateOf(true)
            setContent {
                val leaf =
                    remember {
                        movableContentOf {
                            Box(
                                modifier =
                                    SwingModifier
                                        .testTag(LAYERED_TAG)
                                        .background(Brush.of(Color.RED))
                                        .preferredSize(40, 20),
                            )
                        }
                    }
                if (underLayeredPolicy) {
                    Layout(
                        content = { leaf() },
                        measurePolicy =
                            MeasurePolicy { measurables, _ ->
                                val placeable = measurables.single().measure(Constraints.fixed(40, 20))
                                layout(placeable.width, placeable.height) {
                                    placeable.placeWithLayer(0, 0) { rotationZ = 90f }
                                }
                            },
                    )
                } else {
                    Box { leaf() }
                }
            }
            assertEquals(
                Insets(10, 0, 10, 0),
                layeredOutsets(),
                "the policy's quarter turn takes 10 px above and below",
            )

            underLayeredPolicy = false
            awaitIdle()

            assertEquals(
                Insets(0, 0, 0, 0),
                layeredOutsets(),
                "the child moved to a plain container must lose the layer",
            )
            assertEquals(
                Color.RED.rgb,
                onNodeWithTag(LAYERED_TAG).captureToImage().getRGB(2, 10),
                "the child paints unturned in a plain container",
            )
        }

    /**
     * A right-to-left container's policy placing a narrower child with [Placeable.placeRelativeWithLayer] mirrors
     * the offset from the leading edge, as its plain [Placeable.placeRelative] does, and keeps the layer.
     */
    @Test
    fun aPolicyPlacesRelativelyWithALayerMirroredUnderARightToLeftParent() =
        runComposeSwingTest {
            setContent {
                Layout(
                    content = {
                        Box(
                            modifier =
                                SwingModifier
                                    .testTag(LAYERED_TAG)
                                    .background(Brush.of(Color.RED))
                                    .preferredSize(20, 20),
                        )
                    },
                    measurePolicy =
                        MeasurePolicy { measurables, _ ->
                            val placeable = measurables.single().measure(Constraints.fixed(20, 20))
                            layout(40, 20) {
                                placeable.placeRelativeWithLayer(0, 0) { alpha = 0.5f }
                            }
                        },
                    modifier = containerModifier(40, 20, ComponentOrientation.RIGHT_TO_LEFT),
                )
            }

            val layered = onNodeWithTag(LAYERED_TAG).fetch<JComponent>()
            assertEquals(
                40 - 20,
                layered.x,
                "an offset of 0 from the leading edge sits flush with the right edge under a right-to-left parent",
            )
            val pixel = onNodeWithTag(LAYERED_TAG).captureToImage().getRGB(10, 10)
            assertEquals(128.0, (pixel ushr 24).toDouble(), 1.0, "the layer's alpha still applies once mirrored")
        }

    @Test
    fun placingAChildThatIsNotDecoratableWithALayerFails() =
        runComposeSwingTest {
            var failure: IllegalStateException? = null
            setContent {
                Layout(
                    content = { Label("plain") },
                    measurePolicy =
                        MeasurePolicy { measurables, _ ->
                            val placeable = measurables.single().measure(Constraints.fixed(40, 20))
                            layout(placeable.width, placeable.height) {
                                try {
                                    placeable.placeWithLayer(0, 0, layerBlock = QuarterTurn)
                                } catch (thrown: IllegalStateException) {
                                    failure = thrown
                                    placeable.place(0, 0)
                                }
                            }
                        },
                )
            }

            val message = assertNotNull(failure, "placing a plain label with a layer must fail").message.orEmpty()
            assertTrue(Decoratable::class.java.name in message, message)
        }

    /** A state read in the block repaints the child, and lays nothing out. */
    @Test
    fun aReadInAPolicyLayerBlockRepaintsTheChildWithoutInvalidating() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val alpha = mutableFloatStateOf(1f)
            val block: PlacementLayerScope.() -> Unit = { this.alpha = alpha.floatValue }
            setWindowContent { LayeredChild(SwingModifier.testTag(CONTAINER_TAG)) { block } }
            val child = onWindowWithTitle(WINDOW_TITLE).onNodeWithTag(LAYERED_TAG).fetch<JComponent>()
            val container = windowContainer()
            container.paintImmediately(0, 0, container.width, container.height)
            assertTrue(child.dirtyRegion().isEmpty, "the realized child must start painted")

            alpha.floatValue = 0f
            Snapshot.sendApplyNotifications()

            assertTrue(!child.dirtyRegion().isEmpty, "a changed read must repaint the child")
            assertTrue(container.isValidUpToTheValidateRoot(), "a layer read must not invalidate anything")
            awaitIdle()
            assertEquals(0, onWindowWithTitle(WINDOW_TITLE).onNodeWithTag(LAYERED_TAG).captureToImage().getRGB(20, 10))
        }

    /**
     * A read turning the child from a quarter turn one way to a quarter turn the other repaints it: the turned box
     * stays the same, so only the changed rotation tells the child to repaint.
     */
    @Test
    fun aReadTurningAPolicyLayerTheOtherWayRepaintsTheChild() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val rotation = mutableFloatStateOf(90f)
            val block: PlacementLayerScope.() -> Unit = { rotationZ = rotation.floatValue }
            setWindowContent { LayeredChild(SwingModifier.testTag(CONTAINER_TAG)) { block } }
            val child = onWindowWithTitle(WINDOW_TITLE).onNodeWithTag(LAYERED_TAG).fetch<JComponent>()
            val container = windowContainer()
            container.paintImmediately(0, 0, container.width, container.height)
            assertTrue(child.dirtyRegion().isEmpty, "the realized child must start painted")

            rotation.floatValue = -90f
            Snapshot.sendApplyNotifications()

            assertTrue(!child.dirtyRegion().isEmpty, "a changed rotation must repaint the child")
        }
}

/**
 * A container placing one child of 40 by 20, tagged [LAYERED_TAG], painting [color] behind its [paddingStart], with
 * the layer [layerOf] names in its placement, or plainly where it names none.
 */
@Composable
private fun LayeredChild(
    modifier: SwingModifier = SwingModifier,
    paddingStart: Int = 0,
    color: Color = Color.RED,
    shadowRadius: Int = 0,
    layerOf: () -> (PlacementLayerScope.() -> Unit)?,
) {
    Layout(
        content = {
            var declared = SwingModifier.testTag(LAYERED_TAG).background(Brush.of(color))
            if (shadowRadius > 0) declared = declared.shadow(shadowRadius, Color.BLACK)
            Box(modifier = if (paddingStart == 0) declared else declared.padding(start = paddingStart))
        },
        measurePolicy =
            MeasurePolicy { measurables, _ ->
                val placeable = measurables.single().measure(Constraints.fixed(40, 20))
                layout(placeable.width, placeable.height) {
                    val block = layerOf()
                    if (block == null) placeable.place(0, 0) else placeable.placeWithLayer(0, 0, layerBlock = block)
                }
            },
        modifier = modifier,
    )
}

private fun ComposeSwingTest.layeredOutsets(): Insets =
    (onNodeWithTag(LAYERED_TAG).fetch() as Decoratable).decoration.paintOutsets()

private val QuarterTurn: PlacementLayerScope.() -> Unit = { rotationZ = 90f }

private val EighthTurn: PlacementLayerScope.() -> Unit = { rotationZ = 45f }

private val ClipAndDouble: PlacementLayerScope.() -> Unit = {
    scaleX = 2f
    scaleY = 2f
    clip = true
}

private const val LAYERED_TAG = "layered"
