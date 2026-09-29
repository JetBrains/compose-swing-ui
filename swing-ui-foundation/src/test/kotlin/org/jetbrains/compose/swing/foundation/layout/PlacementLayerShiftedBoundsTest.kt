package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import org.jetbrains.compose.swing.foundation.graphics.Brush
import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.graphics.layoutBounds
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Color
import java.awt.Component
import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlacementLayerShiftedBoundsTest {
    @Test
    fun anIdentityPlacementLayerKeepsPaintFromAnOffsetChild() =
        runComposeSwingTest {
            val identityLayer = CapturingOffsetLayerElement(alpha = 1f)
            setContent { ShiftedLayerFixture(identityLayer) }

            assertShiftedLayerPainted()
        }

    @Test
    fun ordinaryChildOverflowStaysClippedWithoutAPlacementLayer() =
        runComposeSwingTest {
            setContent { OrdinaryOverflowFixture() }

            val layered = onNodeWithTag("layered").fetch<JComponent>()
            val spill = layered.layoutBounds.x + layered.layoutBounds.width + 5
            val pixel = onNodeWithTag("root").captureToImage().getRGB(spill, 20)
            assertEquals(
                0,
                pixel and 0xFFFFFF,
                "no-layer control at ($spill, 20); component=${layered.bounds}, " +
                    "layout=${layered.layoutBounds}, outsets=${layered.paintOutsets()}",
            )
        }

    @Test
    fun anAlphaLayerWithShiftedPlacementPaintsPastTheComponentBounds() =
        runComposeSwingTest {
            val layer = CapturingOffsetLayerElement(alpha = 0.5f)
            setContent { ShiftedLayerFixture(layer) }

            assertShiftedLayerPainted()
        }

    @Test
    fun aClippingLayerGathersItsBoxInsteadOfTheLargerPlacedComponent() =
        runComposeSwingTest {
            val layer = CapturingOffsetLayerElement(alpha = 0.5f, clip = true)
            setContent { ShiftedLayerFixture(layer, leafSize = 56) }

            val row = onNodeWithTag("row").fetch<JComponent>()
            val leaf = onNodeWithTag("leaf").fetch<JComponent>()
            val root = onNodeWithTag("root").fetch<JComponent>()
            val image = root.captureToImage()
            assertTrue(leaf.bounds.width > 40, "fixture needs a larger placed component: ${leaf.bounds}")
            assertEquals(20, row.paintOutsets().left)
            assertEquals(0, row.paintOutsets().right)
            val rowBounds = row.boundsInRoot(root)
            assertTrue(image.getRGB(rowBounds.x + 10, rowBounds.y + 20).isRedDominant())
            assertTrue(image.getRGB(rowBounds.x - 5, rowBounds.y + 20).isRedDominant())
            assertFalse(image.getRGB(rowBounds.x + rowBounds.width + 5, rowBounds.y + 20).isRedDominant())
        }

    @Test
    fun aClippedChildOutsideItsOuterClipAddsNoPaintOutsetsToItsParent() =
        runComposeSwingTest {
            val outerClip = CapturingOffsetLayerElement(alpha = 1f, clip = true, offsetX = 20)
            val innerClip = CapturingOffsetLayerElement(alpha = 1f, clip = true, offsetX = 20)
            setContent { ShiftedLayerFixture(innerClip, outerLayer = outerClip, middleSize = 20, leafSize = 0) }

            assertEquals(Insets(0, 0, 0, 0), onNodeWithTag("row").fetch<JComponent>().paintOutsets())
        }

    @Test
    fun aClipBoxScaledByAnOuterLayerSetsItsParentsPaintOutsets() =
        runComposeSwingTest {
            val clip = CapturingOffsetLayerElement(alpha = 1f, clip = true)
            val scale = CapturingOffsetLayerElement(alpha = 1f, scale = 2f)
            setContent { ShiftedLayerFixture(clip, outerLayer = scale) }

            assertEquals(Insets(20, 80, 20, 0), onNodeWithTag("row").fetch<JComponent>().paintOutsets())
        }

    @Test
    fun anOuterClipCutsAnInnerScaledClipBox() =
        runComposeSwingTest {
            val outerClip = CapturingOffsetLayerElement(alpha = 1f, clip = true)
            val innerClip = CapturingOffsetLayerElement(alpha = 1f, clip = true, scale = 2f)
            setContent { ShiftedLayerFixture(innerClip, outerLayer = outerClip, middleSize = 20) }

            assertEquals(Insets(0, 20, 0, 0), onNodeWithTag("row").fetch<JComponent>().paintOutsets())
        }

    @Test
    fun aScaleDeclaredInsideAClipAddsNoPaintRoomPastTheClipBox() =
        runComposeSwingTest {
            val outerClip = CapturingOffsetLayerElement(alpha = 1f, clip = true)
            val scale = CapturingOffsetLayerElement(alpha = 1f, scale = 2f, offsetX = -30)
            setContent { ShiftedLayerFixture(scale, outerLayer = outerClip) }

            val row = onNodeWithTag("row").fetch<JComponent>()
            val root = onNodeWithTag("root").fetch<JComponent>()
            val image = root.captureToImage()
            assertEquals(Insets(0, 20, 0, 0), row.paintOutsets())
            val rowBounds = row.boundsInRoot(root)
            val clipBox = Rectangle(rowBounds.x - 20, rowBounds.y, 40, 40)
            val middleY = clipBox.y + clipBox.height / 2
            for (point in listOf(Point(clipBox.x - 5, middleY), Point(clipBox.x + clipBox.width + 5, middleY))) {
                assertFalse(image.getRGB(point.x, point.y).isRedDominant(), "content outside the clip box at $point")
            }
        }

    @Test
    fun removingAPlacementLayerDropsParentOutsetsAndSpill() =
        runComposeSwingTest {
            val layer = CapturingOffsetLayerElement(alpha = 0.5f)
            setContent { ShiftedLayerFixture(layer) }

            val row = onNodeWithTag("row").fetch<JComponent>()
            val leaf = onNodeWithTag("leaf").fetch<JComponent>()
            val root = onNodeWithTag("root").fetch<JComponent>()
            val rowBounds = row.boundsInRoot(root)
            val leafNativeInRoot = SwingUtilities.convertRectangle(leaf.parent, leaf.bounds, root)
            val sample = outsidePoint(rowBounds, leafNativeInRoot, "Row")
            assertTrue(
                row.paintOutsets().left > 0,
                "active layer must propagate left paint outsets: ${row.paintOutsets()}",
            )
            assertTrue(root.captureToImage().getRGB(sample.x, sample.y).isRedDominant())

            layer.active.value = false
            awaitIdle()

            assertEquals(Insets(0, 0, 0, 0), row.paintOutsets())
            assertFalse(root.captureToImage().getRGB(sample.x, sample.y).isRedDominant())
        }

    @Test
    fun aHostClipCutsTheShiftedPixelAtItsOwnLayerBox() =
        runComposeSwingTest {
            val layer = CapturingOffsetLayerElement(alpha = 0.5f)
            setContent { ShiftedLayerFixture(layer, hostClip = true) }

            val layered = onNodeWithTag("layered").fetch<JComponent>()
            val leaf = onNodeWithTag("leaf").fetch<JComponent>()
            val root = onNodeWithTag("root").fetch<JComponent>()
            val row = onNodeWithTag("row").fetch<JComponent>()
            val image = onNodeWithTag("root").captureToImage()
            val layerBounds = layered.boundsInRoot(root)
            val rowBounds = row.boundsInRoot(root)
            val leafNativeInRoot = SwingUtilities.convertRectangle(leaf.parent, leaf.bounds, root)
            val sample = outsidePoint(rowBounds, leafNativeInRoot, "host clip")
            val localSample = Point(sample.x - layerBounds.x, sample.y - layerBounds.y)
            val hostDecoration = (layered as Decoratable).decoration
            assertEquals(
                null,
                hostDecoration.contentPoint(localSample.x, localSample.y, clipped = true),
                "sample $sample maps to host-local $localSample, which the host layer box clips; " +
                    "layer=${layer.node.layerOrNull?.geometry()}, host=$layerBounds, row=$rowBounds",
            )
            assertEquals(
                0,
                image.getRGB(sample.x, sample.y) and 0xFFFFFF,
                "host clip at $sample; leaf=$leafNativeInRoot, row=$rowBounds, host=$layerBounds",
            )
            assertTrue(
                image.getRGB(rowBounds.x + 10, rowBounds.y + 20).isRedDominant(),
                "host clip must retain paint inside its layer box; leaf=$leafNativeInRoot, row=$rowBounds",
            )
        }

    @Test
    fun anExplicitRowClipCutsShiftedLayerPaint() =
        runComposeSwingTest {
            val layer = CapturingOffsetLayerElement(alpha = 0.5f)
            setContent { ShiftedLayerFixture(layer, rowClip = true) }

            val leaf = onNodeWithTag("leaf").fetch<JComponent>()
            val root = onNodeWithTag("root").fetch<JComponent>()
            val image = onNodeWithTag("root").captureToImage()
            val rowBounds = onNodeWithTag("row").fetch<JComponent>().boundsInRoot(root)
            val leafNativeInRoot = SwingUtilities.convertRectangle(leaf.parent, leaf.bounds, root)
            val sample = outsidePoint(rowBounds, leafNativeInRoot, "Row clip")
            val pixel = image.getRGB(sample.x, sample.y)
            assertEquals(
                0,
                pixel and 0xFFFFFF,
                "Row clip at $sample; leaf=$leafNativeInRoot, row=$rowBounds",
            )
            assertTrue(
                image.getRGB(rowBounds.x + 10, rowBounds.y + 20).isRedDominant(),
                "Row clip must retain paint inside its bounds; leaf=$leafNativeInRoot, row=$rowBounds",
            )
        }

    private fun ComposeSwingTest.assertShiftedLayerPainted() {
        val layered = onNodeWithTag("layered").fetch<JComponent>()
        val leaf = onNodeWithTag("leaf").fetch<JComponent>()
        val root = onNodeWithTag("root").fetch<JComponent>()
        val row = onNodeWithTag("row").fetch<JComponent>()
        val image = onNodeWithTag("root").captureToImage()
        val rowBounds = row.boundsInRoot(root)
        val leafNativeInRoot = SwingUtilities.convertRectangle(leaf.parent, leaf.bounds, root)
        val target = outsidePoint(rowBounds, leafNativeInRoot, "Row")
        val imageBounds = Rectangle(0, 0, image.width, image.height)
        assertTrue(imageBounds.contains(target), "sample $target must lie within captured image $imageBounds")
        assertTrue(!rowBounds.contains(target), "sample $target must lie outside Row logical bounds $rowBounds")
        val pixel = image.getRGB(target.x, target.y)
        assertTrue(
            pixel.isRedDominant(),
            "expected red spill at $target, outside Row=$rowBounds and inside root; " +
                "pixel=0x${pixel.toUInt().toString(16)}, leaf=${leaf.bounds}, leaf in root=$leafNativeInRoot, " +
                "layered=${layered.boundsInRoot(root)}, outsets=${layered.paintOutsets()}",
        )
    }

    @Composable
    private fun ShiftedLayerFixture(
        layer: CapturingOffsetLayerElement,
        hostClip: Boolean = false,
        rowClip: Boolean = false,
        leafSize: Int = 40,
        outerLayer: CapturingOffsetLayerElement? = null,
        middleSize: Int? = null,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = SwingModifier.testTag("root").preferredSize(240, 80)) {
            val rowModifier = SwingModifier.testTag("row").let { if (rowClip) it.clipToBounds() else it }
            Row(modifier = rowModifier) {
                val layeredModifier =
                    SwingModifier
                        .testTag("layered")
                        .preferredSize(40, 40)
                        .let { if (hostClip) it.clipToBounds() else it }
                        .then(outerLayer ?: SwingModifier)
                        .then(middleSize?.let { SwingModifier.preferredSize(it, it) } ?: SwingModifier)
                        .then(layer)
                Box(
                    contentAlignment = Alignment.TopStart,
                    modifier = layeredModifier,
                ) {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag("leaf")
                                .requiredSize(leafSize, leafSize)
                                .background(Brush.of(Color.RED)),
                    )
                }
            }
        }
    }

    @Composable
    private fun OrdinaryOverflowFixture() {
        Box(modifier = SwingModifier.testTag("root").preferredSize(120, 80)) {
            Row {
                Box(modifier = SwingModifier.size(20, 40))
                Box(
                    contentAlignment = Alignment.TopStart,
                    modifier = SwingModifier.testTag("layered").preferredSize(40, 40),
                ) {
                    Box(
                        modifier =
                            SwingModifier
                                .requiredSize(56, 56)
                                .offset(x = 20)
                                .background(Brush.of(Color.RED)),
                    )
                }
            }
        }
    }

    private class CapturingOffsetLayerElement(
        private val alpha: Float,
        private val clip: Boolean = false,
        private val scale: Float = 1f,
        private val offsetX: Int = -20,
    ) : LayoutModifierNodeElement<CapturingOffsetLayerNode>() {
        val active = mutableStateOf(true)

        lateinit var node: CapturingOffsetLayerNode
            private set

        override fun create(): CapturingOffsetLayerNode =
            CapturingOffsetLayerNode(alpha, clip, scale, offsetX, active).also { node = it }

        override fun update(node: CapturingOffsetLayerNode) = Unit

        override fun equals(other: Any?): Boolean = this === other

        override fun hashCode(): Int = System.identityHashCode(this)
    }

    private class CapturingOffsetLayerNode(
        private val alpha: Float,
        private val clip: Boolean,
        private val scale: Float,
        private val offsetX: Int,
        private val active: State<Boolean>,
    ) : LayoutModifierNode() {
        override val name: String get() = "capturingOffsetLayer"

        override val declaredValues: Map<String, Any?> get() = emptyMap()

        override fun MeasureScope.measure(
            measurable: Measurable,
            constraints: Constraints,
        ): MeasureResult {
            val placeable = measurable.measure(constraints)
            return layout(placeable.width, placeable.height) {
                if (active.value) {
                    placeable.placeWithLayer(offsetX, 0) {
                        this.alpha = this@CapturingOffsetLayerNode.alpha
                        this.clip = this@CapturingOffsetLayerNode.clip
                        this.scaleX = this@CapturingOffsetLayerNode.scale
                        this.scaleY = this@CapturingOffsetLayerNode.scale
                    }
                } else {
                    placeable.place(offsetX, 0)
                }
            }
        }
    }

    private fun PlacementLayer.geometry(): String = "($boxX, $boxY, $boxWidth, $boxHeight)"

    private fun JComponent.boundsInRoot(root: Component): Rectangle =
        SwingUtilities.convertRectangle(this, (this as Decoratable).decoration.localLayoutBounds(this), root)

    private fun outsidePoint(
        layer: Rectangle,
        leaf: Rectangle,
        description: String,
    ): Point {
        val leftOverflow = layer.x - leaf.x
        val rightOverflow = leaf.x + leaf.width - (layer.x + layer.width)
        val x =
            when {
                leftOverflow > 0 -> leaf.x + leftOverflow / 2
                rightOverflow > 0 -> layer.x + layer.width + rightOverflow / 2
                else -> error("No $description overflow: layer=$layer leaf=$leaf")
            }
        return Point(x, leaf.y + leaf.height / 2)
    }

    private fun Int.isRedDominant(): Boolean {
        val red = (this ushr 16) and 0xFF
        val green = (this ushr 8) and 0xFF
        val blue = this and 0xFF
        return red > 0 && red > green && red > blue
    }

    private fun JComponent.paintOutsets(): Insets = (this as Decoratable).decoration.paintOutsets()
}
