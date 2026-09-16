package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.foundation.graphics.Brush
import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.graphics.DecoratedPanel
import org.jetbrains.compose.swing.foundation.graphics.Fill
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Color
import java.awt.Container
import java.awt.image.BufferedImage
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A layout node's decorator: where it paints among its component's decoration steps, and how it is set and cleared. */
class LayoutNodeDecoratorTest {
    @Test
    fun layoutNodeStepsMovedByOneDeclarationReachTheComponentAsOneDecoration() =
        runComposeSwingTest {
            var modifier by mutableStateOf(
                with(BoxScopeInstance) {
                    SwingModifier
                        .then(FillingLayoutNodeElement(Color.GREEN))
                        .then(FillingLayoutNodeElement(Color.YELLOW))
                        .background(Red)
                },
            )
            val panel = decoratedChild { modifier }
            panel.repaints = 0

            modifier =
                with(BoxScopeInstance) {
                    SwingModifier
                        .background(Red)
                        .then(FillingLayoutNodeElement(Color.GREEN))
                        .then(FillingLayoutNodeElement(Color.YELLOW))
                }
            awaitIdle()

            assertEquals(1, panel.repaints, "both steps move inside the moved background in one change, not one each")
            assertEquals(
                Color.YELLOW.rgb,
                onNodeWithTag(DECORATED).captureToImage().getRGB(1, 1),
                "the last step paints innermost",
            )
        }

    @Test
    fun aChainAttachedWholePaintsEachLayoutNodeStepAtItsPlace() =
        runComposeSwingTest {
            val panel =
                decoratedChild {
                    with(BoxScopeInstance) {
                        SwingModifier
                            .background(Red)
                            .then(FillingLayoutNodeElement(Color.GREEN))
                            .then(FillingLayoutNodeElement(Color.YELLOW))
                    }
                }

            val corners = checkNotNull(panel.corners)
            assertEquals(
                Color.YELLOW.rgb,
                onNodeWithTag(DECORATED).captureToImage().getRGB(1, 1),
                "the last step declared paints innermost",
            )
            assertTrue(
                Color.GREEN.rgb !in corners.dropWhile { it != Color.YELLOW.rgb },
                "each step joins at its final place, so no repaint paints a step inside a later one: $corners",
            )
        }

    @Test
    fun aLayoutNodeGivenANewDecoratorAsItMovesPublishesItOnceAtItsNewPlace() =
        runComposeSwingTest {
            var modifier by mutableStateOf(
                with(BoxScopeInstance) { SwingModifier.then(FillingLayoutNodeElement(Color.GREEN)).background(Red) },
            )
            val panel = decoratedChild { modifier }
            panel.repaints = 0

            modifier =
                with(BoxScopeInstance) { SwingModifier.background(Red).then(FillingLayoutNodeElement(Color.YELLOW)) }
            awaitIdle()

            assertEquals(
                1,
                panel.repaints,
                "the new decorator reaches the component once, already at the node's new place",
            )
            assertEquals(
                Color.YELLOW.rgb,
                onNodeWithTag(DECORATED).captureToImage().getRGB(1, 1),
                "the step paints inside the background",
            )
        }

    @Test
    fun aLayoutNodeDecoratorOnAComponentThatIsNotDecoratedIsRefused() {
        val refused =
            assertFailsWith<IllegalStateException> {
                runComposeSwingTest {
                    setContent {
                        Box {
                            SwingNode(
                                factory = { JPanel() },
                                modifier = SwingModifier.then(FillingLayoutNodeElement(Color.GREEN)),
                            )
                        }
                    }
                }
            }
        assertTrue(refused.message.orEmpty().contains(Decoratable::class.java.name), "${refused.message}")
    }

    @Test
    fun aLayoutNodeDecoratorIsSetOnlyWhileAttached() =
        runComposeSwingTest {
            val created = NodeCell()
            var modifier by mutableStateOf(SwingModifier.then(FillingLayoutNodeElement(Color.GREEN, created)))
            decoratedChild { modifier }
            val node = created.node

            modifier = SwingModifier
            awaitIdle()

            assertNull(node.decorator, "a detached node has no decorator")
            assertFailsWith<IllegalStateException> { node.decorator = Fill(Color.YELLOW) }
            assertFailsWith<IllegalStateException> { node.decorator = null }
        }

    @Test
    fun aDetachedNodesDecoratorPaintsNoMore() =
        runComposeSwingTest {
            var modifier by mutableStateOf(
                with(BoxScopeInstance) { SwingModifier.background(Red).then(FillingLayoutNodeElement(Color.GREEN)) },
            )
            decoratedChild { modifier }
            assertEquals(Color.GREEN.rgb, onNodeWithTag(DECORATED).captureToImage().getRGB(1, 1))

            modifier = with(BoxScopeInstance) { SwingModifier.background(Red) }
            awaitIdle()

            assertEquals(
                Color.RED.rgb,
                onNodeWithTag(DECORATED).captureToImage().getRGB(1, 1),
                "a detached node's decorator paints no more",
            )
        }

    @Test
    fun clearingTheDecoratorStopsItsPaintingAndSettingItAgainRestoresIt() =
        runComposeSwingTest {
            val created = NodeCell()
            decoratedChild {
                with(BoxScopeInstance) {
                    SwingModifier.background(Red).then(FillingLayoutNodeElement(Color.GREEN, created))
                }
            }
            val node = created.node
            val decorator = checkNotNull(node.decorator)

            node.decorator = null
            assertNull(node.decorator)
            assertEquals(
                Color.RED.rgb,
                onNodeWithTag(DECORATED).captureToImage().getRGB(1, 1),
                "a cleared decorator paints no more",
            )

            node.decorator = decorator
            assertEquals(
                Color.GREEN.rgb,
                onNodeWithTag(DECORATED).captureToImage().getRGB(1, 1),
                "setting it again paints it again",
            )
        }

    @Test
    fun aNodeHoldingADecoratorFailsToPlaceWithALayerAndKeepsItsDecorator() =
        runComposeSwingTest {
            val created = NodeCell()
            decoratedChild { SwingModifier.then(FillingLayoutNodeElement(Color.GREEN, created, layered = true)) }
            val node = created.node as FillingLayoutNode

            assertEquals(
                "A layout node holding a decorator cannot place its content with a layer, which paints in the " +
                    "decorator's place. Declare the decorator on a node of its own.",
                node.layerFailure?.message,
            )
            assertEquals(Fill(Color.GREEN), node.decorator)
            assertEquals(
                Color.GREEN.rgb,
                onNodeWithTag(DECORATED).captureToImage().getRGB(1, 1),
                "the decorator still paints",
            )
        }

    @Test
    fun aNodePlacingWithALayerReadsNoDecoratorAndFailsToSetOne() =
        runComposeSwingTest {
            val created = NodeCell()
            decoratedChild { SwingModifier.then(FillingLayoutNodeElement(null, created, layered = true)) }
            val node = created.node

            assertNull(node.decorator, "the layer is not the node's decorator")
            val thrown = assertFailsWith<IllegalStateException> { node.decorator = Fill(Color.YELLOW) }
            assertEquals(
                "A layout node placing its content with a layer cannot hold a decorator, since the layer paints " +
                    "in its place. Declare the decorator on a node of its own.",
                thrown.message,
            )
            node.decorator = null
            assertNull(node.decorator)
        }

    @Test
    fun aNodeAttachedAgainPaintsWithTheDecoratorItHeldBeforeItsDetach() =
        runComposeSwingTest {
            val reused = FillingLayoutNode()
            var modifier by mutableStateOf(SwingModifier.then(FillingLayoutNodeElement(Color.GREEN, reused = reused)))
            decoratedChild { modifier }

            modifier = SwingModifier
            awaitIdle()
            modifier = SwingModifier.then(FillingLayoutNodeElement(null, reused = reused))
            awaitIdle()

            assertEquals(Fill(Color.GREEN), reused.decorator, "the node keeps its decorator across the detach")
            assertEquals(
                Color.GREEN.rgb,
                onNodeWithTag(DECORATED).captureToImage().getRGB(1, 1),
                "the decorator paints again",
            )
        }

    @Test
    fun aNodeAttachedAgainKeepsTheDecoratorItSetsAfterPlacingWithALayerBefore() =
        runComposeSwingTest {
            val reused = FillingLayoutNode()
            var modifier by mutableStateOf(
                SwingModifier.then(FillingLayoutNodeElement(null, layered = true, reused = reused)),
            )
            decoratedChild { modifier }

            modifier = SwingModifier
            awaitIdle()
            modifier = SwingModifier.then(FillingLayoutNodeElement(Color.GREEN, reused = reused))
            awaitIdle()

            assertEquals(
                Fill(Color.GREEN),
                reused.decorator,
                "placing without the layer keeps the decorator",
            )
            assertEquals(
                Color.GREEN.rgb,
                onNodeWithTag(DECORATED).captureToImage().getRGB(1, 1),
                "the decorator paints",
            )
        }

    @Test
    fun aNodeWhoseComponentLeavesForAPlainContainerStopsPaintingTheLayerAndPlacingItAgainPaintsANewDecorator() =
        runComposeSwingTest {
            val reused = FillingLayoutNode()
            var modifier by mutableStateOf(
                SwingModifier.then(FillingLayoutNodeElement(null, layered = true, reused = reused)),
            )
            val panel = decoratedChild { modifier }

            // Something outside the composition moves the component into a plain container, bypassing the
            // applier entirely.
            val container = panel.parent as Container
            container.remove(panel)
            JPanel().add(panel)

            assertFalse(
                panel.decoration.isDecorated,
                "a component moved out of its container stops painting the layer it placed with",
            )

            reused.decorator = Fill(Color.GREEN)
            assertFalse(
                panel.decoration.isDecorated,
                "a decorator set before the node is placed again does not paint yet",
            )

            container.add(panel)
            modifier = SwingModifier
            awaitIdle()
            modifier = SwingModifier.then(FillingLayoutNodeElement(null, reused = reused))
            awaitIdle()

            assertEquals(
                Color.GREEN.rgb,
                onNodeWithTag(DECORATED).captureToImage().getRGB(1, 1),
                "the decorator set while the node was placed elsewhere paints once it is placed again",
            )
        }
}

/** Composes a [RecordingPanel] declaring [modifier] in a [Box], and returns it once the composition is idle. */
private suspend fun ComposeSwingTest.decoratedChild(modifier: () -> SwingModifier): RecordingPanel {
    var panel: RecordingPanel? = null
    setContent {
        Box {
            SwingNode(
                factory = { RecordingPanel().also { panel = it } },
                modifier = modifier().testTag(DECORATED).preferredSize(20, 20),
            )
        }
    }
    awaitIdle()
    return checkNotNull(panel)
}

/**
 * A decorated panel painting no background of its own, counting its repaints, which each change of its decoration
 * asks for once, and recording in [corners] the color its decoration paints at (1, 1) at each repaint.
 */
private class RecordingPanel : DecoratedPanel() {
    var repaints = 0
    var invalidations = 0

    /** Null while the superclass constructor repaints, before this class is initialized. */
    val corners: MutableList<Int>? = ArrayList()

    init {
        isOpaque = false
    }

    override fun repaint(
        tm: Long,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        repaints++
        corners?.add(paintedAlone(this).getRGB(1, 1))
        super.repaint(tm, x, y, width, height)
    }

    override fun invalidate() {
        invalidations++
        super.invalidate()
    }
}

/** The node a [FillingLayoutNodeElement] created, as a test holding the modifier reaches it. */
private class NodeCell {
    lateinit var node: LayoutModifierNode
}

/**
 * A layout node whose decorator fills its area with [color] under its content, or that sets none where [color] is
 * null, placing the content with a layer where [layered]. It records the node in [created], and attaches [reused]
 * where one is given.
 */
private class FillingLayoutNodeElement(
    private val color: Color?,
    private val created: NodeCell? = null,
    private val layered: Boolean = false,
    private val reused: FillingLayoutNode? = null,
) : LayoutModifierNodeElement<FillingLayoutNode>() {
    override fun create(): FillingLayoutNode = (reused ?: FillingLayoutNode()).also { created?.node = it }

    override fun update(node: FillingLayoutNode) {
        if (color != null) node.decorator = Fill(color)
        node.layered = layered
    }

    override fun equals(other: Any?): Boolean =
        other is FillingLayoutNodeElement && other.color == color && other.layered == layered

    override fun hashCode(): Int = color.hashCode() * 31 + layered.hashCode()
}

/** Places its content where it is, with a layer where [layered], recording a failure to place with it. */
private class FillingLayoutNode : LayoutModifierNode() {
    var layered = false
    var layerFailure: IllegalStateException? = null

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            if (layered) {
                try {
                    placeable.placeWithLayer(0, 0) {}
                } catch (failure: IllegalStateException) {
                    layerFailure = failure
                    placeable.place(0, 0)
                }
            } else {
                placeable.place(0, 0)
            }
        }
    }
}

private val Red = Brush { _, _ -> Color.RED }

/** [panel]'s decoration painted around its empty, 20 pixels square, content. */
private fun paintedAlone(panel: DecoratedPanel): BufferedImage {
    val image = BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
        graphics.clipRect(0, 0, 20, 20)
        panel.decoration.paint(panel, graphics) {}
    } finally {
        graphics.dispose()
    }
    return image
}

/** The tag [decoratedChild] gives the component it composes. */
private const val DECORATED = "decorated"
