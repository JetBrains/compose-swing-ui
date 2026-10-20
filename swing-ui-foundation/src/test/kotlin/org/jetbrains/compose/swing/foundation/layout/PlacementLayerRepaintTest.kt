package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.compose.swing.assertPixelColor
import org.jetbrains.compose.swing.foundation.graphics.Brush
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Color
import java.awt.ComponentOrientation
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.RepaintManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Behavioral tests for a layout modifier placing its content with a layer: what a replayed, moved or dropped
 * placement repaints.
 */
class PlacementLayerRepaintTest {
    companion object {
        @JvmStatic
        fun placementReads(): List<Arguments> =
            listOf(
                Arguments.of(Named.of("an absolute placement", false)),
                Arguments.of(Named.of("a relative placement", true)),
            )
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
                            SwingModifier
                                .testTag("layered")
                                .placementLayer { alpha = 0.5f }
                                .styleable { preferredSize(40, 40) },
                    )
                }
            }
            val row = onWindowWithTitle(WINDOW_TITLE).onNodeWithTag(CONTAINER_TAG).fetch<JComponent>()
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
                                .then(PlacementReadOffsetElement(shift, 1))
                                .placementLayer {
                                    alpha = 0.5f
                                    clip = true
                                }.then(PlacementReadOffsetElement(shift, -1))
                                .background(Brush.of(Color.RED))
                                .styleable { preferredSize(40, 40) },
                    )
                }
            }
            val row = onWindowWithTitle(WINDOW_TITLE).onNodeWithTag(CONTAINER_TAG).fetch<JComponent>()
            val layered = onWindowWithTitle(WINDOW_TITLE).onNodeWithTag("layered").fetch<JComponent>()
            val placed = layered.bounds
            val repaintManager = RepaintManager.currentManager(layered)
            repaintManager.markCompletelyClean(row)
            repaintManager.markCompletelyClean(layered)

            shift.intValue = 20
            Snapshot.sendApplyNotifications()

            assertEquals(placed, layered.bounds, "the content cancels the layer's move")
            assertEquals(Rectangle(0, 0, 40, 40), layered.dirtyRegion(), "a moved box repaints")
            assertTrue(row.dirtyRegion().isEmpty, "only the component repaints: ${row.dirtyRegion()}")
            assertTrue(layered.isValidUpToTheValidateRoot(), "a moved box must not invalidate anything")
            val image = onWindowWithTitle(WINDOW_TITLE).onNodeWithTag("layered").captureToImage()
            image.assertPixelColor(Color(0, true), 1, 10, "the clip follows the moved box")
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
                                .then(PlacementReadLayerElement(layer, relative = false))
                                .background(Brush.of(Color.RED))
                                .styleable { preferredSize(40, 40) },
                    )
                }
            }
            assertEquals(128.0, onNodeWithTag("layered").captureToImage().alphaAt(10, 10), 1.0)

            layer.value = null
            awaitIdle()

            onNodeWithTag("layered").captureToImage().assertPixelColor(Color.RED, 10, 10)
        }

    /**
     * A node placed without a layer clears the layer's decorator, or the component stays a painting origin and
     * non-opaque. No other decoration is declared here, so the layer is the only thing either reading can come from.
     */
    @Test
    fun aNodeThatStopsPlacingWithALayerClearsTheLayerDecorator() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val layer = mutableStateOf<(PlacementLayerScope.() -> Unit)?>({ alpha = 0.5f })
            // Between the opaque box and the child, so a paint of the child alone does not paint it, and a paint
            // starting at the box or above it does.
            val host = PaintCountingPanel()
            val child = ClipRecordingChild().apply { isOpaque = true }
            setWindowContent {
                Row {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag("layered")
                                .opaque(true)
                                .then(PlacementReadLayerElement(layer, relative = false))
                                .styleable { preferredSize(40, 40) },
                    ) {
                        SwingNode(
                            factory = { host },
                            modifier = SwingModifier.styleable({ preferredSize(40, 40) }, RawKind),
                        ) {
                            SwingNode(factory = { child })
                        }
                    }
                }
            }
            val component = onWindowWithTitle(WINDOW_TITLE).onNodeWithTag("layered").fetch<JComponent>()
            assertTrue(host.isPaintedForTheRepaintOf(child), "a layer makes the component a painting origin")
            assertFalse(component.isOpaque, "with the decorator set, the component reports non-opaque")

            layer.value = null
            awaitIdle()

            assertFalse(host.isPaintedForTheRepaintOf(child), "placed without a layer, it is no painting origin")
            assertTrue(component.isOpaque, "with the decorator cleared, the component is opaque again")
        }

    @Test
    fun aRelativePlacementWithALayerMirrorsUnderARightToLeftParent() =
        runComposeSwingTest {
            setContent {
                Row(
                    modifier =
                        ContainerModifier.styleable(containerStyle(40 * 2, 40, ComponentOrientation.RIGHT_TO_LEFT)),
                ) {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag("layered")
                                .then(ClipLayerElement(relative = true, width = 40))
                                .background(Brush.of(Color.RED))
                                .styleable { preferredSize(10, 10) },
                    )
                }
            }

            onNodeWithTag("layered").assertProperty(
                40 * 2 - 10,
                "the content sits at the trailing edge of the row and of its layer",
            ) { this.x }
            onNodeWithTag("layered").captureToImage().assertPixelColor(Color.RED, 10 / 2, 10 / 2)
        }

    @Test
    fun aPlacementWithALayerReportsItsContentsBaseline() =
        runComposeSwingTest {
            setContent {
                Row(
                    modifier = ContainerModifier.styleable(containerStyle(CHILD_WIDTH * CHILD_COUNT, CHILD_HEIGHT * 2)),
                ) {
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

    /**
     * A placement read that first places with a layer, hands it a new block instance, or places without it, at
     * unchanged bounds, repaints the component, invalidates nothing and leaves its container clean; the pixels
     * follow.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("placementReads")
    fun aPlacementThatTakesChangesOrDropsItsLayerRepaintsOnlyTheComponent(relative: Boolean) =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val block = mutableStateOf<(PlacementLayerScope.() -> Unit)?>(null)
            setWindowContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag("layered")
                                .then(PlacementReadLayerElement(block, relative))
                                .background(Brush.of(Color.RED))
                                .styleable { preferredSize(40, 40) },
                    )
                }
            }
            val row = onWindowWithTitle(WINDOW_TITLE).onNodeWithTag(CONTAINER_TAG).fetch<JComponent>()
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
                assertEquals(Rectangle(0, 0, 40, 40), layered.dirtyRegion(), "repaints")
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
                                .placementLayer(block)
                                .background(Brush.of(Color.RED))
                                .styleable { preferredSize(40, 40) },
                    )
                }
            }
            assertEquals(128.0, onNodeWithTag("layered").captureToImage().alphaAt(10, 10), 1.0)

            faded = false
            awaitIdle()

            onNodeWithTag("layered").captureToImage().assertPixelColor(Color.RED, 10, 10)
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
