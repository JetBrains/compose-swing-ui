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
import org.jetbrains.compose.swing.foundation.graphics.decorated
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Color
import java.awt.ComponentOrientation
import java.awt.GraphicsEnvironment
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

    @Test
    fun placingTheChildWithoutALayerRemovesTheLayer() =
        runComposeSwingTest {
            var layered by mutableStateOf(true)
            setContent { LayeredChild { if (layered) QuarterTurn else null } }
            assertEquals(
                0,
                onNodeWithTag(LAYERED_TAG).captureToImage().getRGB(2, 10),
                "the turned child leaves the ends of its bounds unpainted",
            )

            layered = false
            awaitIdle()

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
                                    decorated { SwingModifier.testTag(LAYERED_TAG).background(Brush.of(Color.RED)) }
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
                0,
                onNodeWithTag(LAYERED_TAG).captureToImage().getRGB(2, 10),
                "the turned child leaves the ends of its bounds unpainted",
            )

            underLayeredPolicy = false
            awaitIdle()

            assertEquals(
                Color.RED.rgb,
                onNodeWithTag(LAYERED_TAG).captureToImage().getRGB(2, 10),
                "the child moved to a plain container must lose the layer",
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
                                decorated { SwingModifier.testTag(LAYERED_TAG).background(Brush.of(Color.RED)) }
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
 * A container placing one child of 40 by 20, tagged [LAYERED_TAG], painting red, with the layer [layerOf] names in its
 * placement, or plainly where it names none.
 */
@Composable
private fun LayeredChild(
    modifier: SwingModifier = SwingModifier,
    layerOf: () -> (PlacementLayerScope.() -> Unit)?,
) {
    Layout(
        content = { Box(modifier = decorated { SwingModifier.testTag(LAYERED_TAG).background(Brush.of(Color.RED)) }) },
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

private val QuarterTurn: PlacementLayerScope.() -> Unit = { rotationZ = 90f }

private const val LAYERED_TAG = "layered"
