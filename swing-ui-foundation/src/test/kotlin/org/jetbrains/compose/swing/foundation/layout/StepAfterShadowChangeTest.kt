package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.foundation.graphics.RectangleShape
import org.jetbrains.compose.swing.foundation.graphics.alpha
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.graphics.clip
import org.jetbrains.compose.swing.foundation.graphics.layoutBounds
import org.jetbrains.compose.swing.foundation.graphics.paintOutsets
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Color
import java.awt.Container
import java.awt.Dimension
import java.awt.Insets
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A shadowed box whose step declared after the shadow changes keeps the paint outsets, insets, bounds and pixels of a
 * box that declared the new step from the start. A node that leaves in the pass that writes a node declared before it
 * leaves the composition applying.
 */
class StepAfterShadowChangeTest {
    @Test
    fun aShadowKeepsItsPaintOutsetsWhenAClipToBoundsAfterItBecomesAFade() =
        assertShadowFitsOnceTheStepAfterItChanges(StepAfterShadow.ClipToBounds, StepAfterShadow.Alpha)

    @Test
    fun aShadowKeepsItsPaintOutsetsWhenAClipToBoundsAfterItBecomesAClip() =
        assertShadowFitsOnceTheStepAfterItChanges(StepAfterShadow.ClipToBounds, StepAfterShadow.Clip)

    @Test
    fun aShadowKeepsItsPaintOutsetsWhenAClipToBoundsAfterItIsRemoved() =
        assertShadowFitsOnceTheStepAfterItChanges(StepAfterShadow.ClipToBounds, StepAfterShadow.None)

    @Test
    fun aShadowKeepsItsPaintOutsetsWhenAFadingPlacementLayerAfterItBecomesAFade() =
        assertShadowFitsOnceTheStepAfterItChanges(StepAfterShadow.FadingLayer, StepAfterShadow.Alpha)

    @Test
    fun aShadowKeepsItsPaintOutsetsWhenAFadeAfterItBecomesAClipToBounds() =
        assertShadowFitsOnceTheStepAfterItChanges(StepAfterShadow.Alpha, StepAfterShadow.ClipToBounds)

    @Test
    fun aShadowKeepsItsPaintOutsetsWhenAClipAfterItBecomesAClipToBounds() =
        assertShadowFitsOnceTheStepAfterItChanges(StepAfterShadow.Clip, StepAfterShadow.ClipToBounds)

    /**
     * One modifier pass drops the clip and hands the node declared before it a decorator with another reach. The pass
     * after it is applied too.
     */
    @Test
    fun aLayeredNodeLeavesAsANodeBeforeItTakesADecoratorWithOtherOutsets() =
        runComposeSwingTest {
            var reach by mutableIntStateOf(4)
            setContent {
                Column {
                    Row(modifier = SwingModifier.testTag("row")) {
                        val haloed = SwingModifier.testTag("box").then(HaloElement(reach)).size(20, 20)
                        Box(modifier = if (reach == 4) haloed.clipToBounds() else haloed)
                    }
                }
            }

            for (next in listOf(8, 12)) {
                reach = next
                awaitIdle()

                val spread = Insets(next, next, next, next)
                val box = onNodeWithTag("box").fetch<JComponent>()
                assertEquals(spread, box.paintOutsets, "the box takes a reach of $next")
                assertEquals(spread, onNodeWithTag("row").fetch<JComponent>().paintOutsets, "and its row spreads by it")
                assertEquals(Rectangle(0, 0, 20, 20), box.layoutBounds, "around the same layout bounds")
            }
        }

    /**
     * One modifier pass drops a node that sets its decorator from its placement and hands the node declared before it
     * a decorator with another reach. The pass after it is applied too.
     */
    @Test
    fun aNodeDecoratingAsItIsPlacedLeavesAsANodeBeforeItTakesADecoratorWithOtherOutsets() =
        runComposeSwingTest {
            var reach by mutableIntStateOf(4)
            val inner = { 2 }
            setContent {
                Column {
                    Row(modifier = SwingModifier.testTag("row")) {
                        val haloed = SwingModifier.testTag("box").then(HaloElement(reach)).size(20, 20)
                        val placed = haloed.then(ReachingElement(inner, whilePlacing = true))
                        Box(modifier = if (reach == 4) placed else haloed)
                    }
                }
            }

            for (next in listOf(8, 12)) {
                reach = next
                awaitIdle()

                val spread = Insets(next, next, next, next)
                val box = onNodeWithTag("box").fetch<JComponent>()
                assertEquals(spread, box.paintOutsets, "the box takes a reach of $next")
                assertEquals(spread, onNodeWithTag("row").fetch<JComponent>().paintOutsets, "and its row spreads by it")
                assertEquals(Rectangle(0, 0, 20, 20), box.layoutBounds, "around the same layout bounds")
            }
        }

    /**
     * One modifier pass drops a node whose `measure` needs the node attached and hands the node declared before it a
     * decorator with another reach, while the row holds no settled result: placing the panel beside the box resized
     * it. The pass after it is applied too.
     */
    @Test
    fun aNodeMeasuringWhileAttachedLeavesAsANodeBeforeItTakesADecoratorWithOtherOutsets() =
        runComposeSwingTest {
            var reach by mutableIntStateOf(4)
            setContent {
                Column {
                    Row(modifier = SwingModifier.testTag("row")) {
                        val haloed = SwingModifier.testTag("box").then(HaloElement(reach)).size(20, 20)
                        Box(modifier = if (reach == 4) haloed.then(AttachedMeasureElement()) else haloed)
                        SwingNode(factory = { JPanel() }, modifier = SwingModifier.size(20, 20))
                    }
                }
            }

            for (next in listOf(8, 12)) {
                reach = next
                awaitIdle()

                val spread = Insets(next, next, next, next)
                val box = onNodeWithTag("box").fetch<JComponent>()
                assertEquals(spread, box.paintOutsets, "the box takes a reach of $next")
                assertEquals(Rectangle(0, 0, 20, 20), box.layoutBounds, "around the same layout bounds")
            }
        }

    /**
     * One modifier pass drops a node whose `measure` needs the node attached and writes the node declared before it,
     * which asks the row, invalid by then, for its preferred size. The pass after it is applied too.
     */
    @Test
    fun aNodeMeasuringWhileAttachedLeavesAsANodeBeforeItAsksTheRowForItsPreferredSize() =
        runComposeSwingTest {
            var tick by mutableIntStateOf(0)
            val answers = mutableListOf<Dimension>()
            val ask = { row: Container ->
                row.invalidate()
                answers += row.preferredSize
            }
            setContent {
                Column {
                    Row {
                        val asking = SwingModifier.then(AskingElement(tick, ask))
                        Box(modifier = if (tick == 0) asking.then(AttachedMeasureElement()) else asking) {
                            SwingNode(factory = { JPanel() }, modifier = SwingModifier.size(20, 20))
                        }
                    }
                }
            }

            for (next in listOf(1, 2)) {
                tick = next
                awaitIdle()

                assertEquals(List(next) { Dimension(20, 20) }, answers, "the row answers as tick $next is written")
            }
        }

    /**
     * One modifier pass drops a node that sets its decorator from its placement and writes the node declared before
     * it, which reads the alignment lines of the row. The pass after it is applied too.
     */
    @Test
    fun aNodeDecoratingAsItIsPlacedLeavesAsANodeBeforeItReadsTheAlignmentLinesOfTheRow() =
        runComposeSwingTest {
            var tick by mutableIntStateOf(0)
            var reads = 0
            val inner = { 2 }
            val read = { row: Container ->
                (row as Constrainable).alignmentLines
                reads += 1
            }
            setContent {
                Column {
                    Row {
                        val asking = SwingModifier.then(AskingElement(tick, read)).size(20, 20)
                        val placed = asking.then(ReachingElement(inner, whilePlacing = true))
                        Box(modifier = if (tick == 0) placed else asking)
                    }
                }
            }

            for (next in listOf(1, 2)) {
                tick = next
                awaitIdle()

                assertEquals(next, reads, "the lines are read as tick $next is written")
            }
        }

    /**
     * A box is deactivated while it holds a decorator with outsets and a node that sets its decorator from its
     * placement. It leaves its row, which spreads no more.
     */
    @Test
    fun aNodeDecoratingAsItIsPlacedIsDeactivatedWithItsBox() =
        runComposeSwingTest {
            var active by mutableStateOf(true)
            val inner = { 2 }
            setContent {
                Column {
                    Row(modifier = SwingModifier.testTag("row")) {
                        ReusableContentHost(active) {
                            val haloed = SwingModifier.then(HaloElement(4)).size(20, 20)
                            Box(modifier = haloed.then(ReachingElement(inner, whilePlacing = true)))
                        }
                    }
                }
            }
            val row = onNodeWithTag("row").fetch<JComponent>()
            assertEquals(Insets(6, 6, 6, 6), row.paintOutsets, "the row spreads by both reaches of the box")

            active = false
            awaitIdle()

            assertEquals(0, row.componentCount, "the box leaves its row")
            assertEquals(Insets(0, 0, 0, 0), row.paintOutsets, "which spreads no more")
        }

    /**
     * A shadowed box alone in its row, whose step declared after the shadow changes from [before] to [after], takes
     * the paint outsets, insets, bounds and pixels of a box that declared [after] from the start, and so does its row.
     */
    private fun assertShadowFitsOnceTheStepAfterItChanges(
        before: StepAfterShadow,
        after: StepAfterShadow,
    ) = runComposeSwingTest {
        var step by mutableStateOf(before)
        setContent {
            Column(verticalArrangement = Arrangement.spacedBy(40)) {
                for ((tag, declared) in listOf("subject" to step, "reference" to after)) {
                    Row(modifier = SwingModifier.testTag("$tag row")) {
                        val shadowed = SwingModifier.testTag(tag).size(20, 20).shadow(6, Color.BLACK)
                        val stepped =
                            when (declared) {
                                StepAfterShadow.ClipToBounds -> shadowed.clipToBounds()
                                StepAfterShadow.FadingLayer -> shadowed.placementLayer { alpha = 0.5f }
                                StepAfterShadow.Alpha -> shadowed.alpha(0.5f)
                                StepAfterShadow.Clip -> shadowed.clip(RectangleShape)
                                StepAfterShadow.None -> shadowed
                            }
                        Box(modifier = stepped.background(Color.RED, RectangleShape))
                    }
                }
            }
        }

        step = after
        awaitIdle()

        for (tag in listOf("", " row")) {
            val subject = onNodeWithTag("subject$tag").fetch<JComponent>()
            val reference = onNodeWithTag("reference$tag").fetch<JComponent>()
            assertTrue(reference.paintOutsets.left > 0, "the shadow spills past the reference$tag's layout bounds")
            assertEquals(reference.paintOutsets, subject.paintOutsets, "the subject$tag keeps the shadow's outsets")
            assertEquals(reference.insets, subject.insets, "and reports them in its insets")
            assertEquals(reference.size, subject.size, "and its bounds carry them")
            assertImagesPixelPerfect(
                onNodeWithTag("reference$tag").captureToImage(),
                onNodeWithTag("subject$tag").captureToImage(),
            )
        }
    }

    /** The step an [assertShadowFitsOnceTheStepAfterItChanges] box declares after its shadow. */
    private enum class StepAfterShadow { ClipToBounds, FadingLayer, Alpha, Clip, None }
}

/** Declares a layout node that takes a [Halo] of [reach] as its decorator when the element is applied. */
private data class HaloElement(
    private val reach: Int,
) : LayoutModifierNodeElement<HaloNode>() {
    override fun create(): HaloNode = HaloNode()

    override fun update(node: HaloNode) {
        node.decorator = Halo(reach)
    }
}

/** Places its content where it is. */
private class HaloNode : LayoutModifierNode() {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}

/** Declares a node that hands [ask] the container of its component each time [tick] changes. */
private class AskingElement(
    private val tick: Int,
    private val ask: (Container) -> Unit,
) : LayoutModifierNodeElement<HaloNode>() {
    override fun create(): HaloNode = HaloNode()

    override fun update(node: HaloNode) {
        node.component.parent?.let(ask)
    }

    override fun equals(other: Any?): Boolean = other is AskingElement && other.tick == tick

    override fun hashCode(): Int = tick
}

/** Declares a node whose `measure` needs the node attached, as a node that animates its size does. */
private class AttachedMeasureElement : LayoutModifierNodeElement<AttachedMeasureNode>() {
    override fun create(): AttachedMeasureNode = AttachedMeasureNode()

    override fun update(node: AttachedMeasureNode) = Unit

    override fun equals(other: Any?): Boolean = other is AttachedMeasureElement

    override fun hashCode(): Int = AttachedMeasureElement::class.hashCode()
}

/** Reads its coroutine scope as it measures, which throws once the node is detached. */
private class AttachedMeasureNode : LayoutModifierNode() {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        coroutineScope
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}
