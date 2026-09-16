package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import java.awt.Dimension
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A [LayoutModifierNode]'s `coroutineScope` runs on the composition's own effect context, so a coroutine
 * it launches is driven by the harness's [org.jetbrains.compose.swing.test.MainTestClock] the same way a
 * `LaunchedEffect` is, and is cancelled once the node detaches.
 */
class LayoutModifierNodeTest {
    @Test
    fun aLayoutModifierNodeCoroutineScopeIsDrivenByTheHarnessClockAndCancelledOnDetach() =
        runComposeSwingTest {
            mainClock.autoAdvance = false
            var frames = 0
            var present by mutableStateOf(true)
            setContent {
                Row {
                    if (present) {
                        SwingNode(
                            factory = { JPanel() },
                            modifier = SwingModifier then frameCountingLayoutModifier { frames++ },
                        )
                    }
                }
            }

            assertEquals(0, frames, "an effect gated on the next frame must not run before one is sent")
            mainClock.advanceTimeByFrame()
            assertEquals(1, frames, "the effect must run once after the first frame")
            mainClock.advanceTimeByFrame()
            assertEquals(2, frames, "and once more after the second")

            present = false
            awaitIdle()
            // A coroutine parked in withFrameNanos when its scope is cancelled may still be resumed with
            // the cancellation on a later frame rather than synchronously; give it one to settle before
            // treating the count as final.
            mainClock.advanceTimeByFrame()
            val framesAtDetach = frames
            mainClock.advanceTimeByFrame()

            assertEquals(
                framesAtDetach,
                frames,
                "detaching the node must cancel what its coroutine scope was running",
            )
        }

    @Test
    fun aLayoutModifierNodeKeepsItsRunningCoroutineWhenItsChildMovesToAnotherRow() =
        runComposeSwingTest {
            mainClock.autoAdvance = false
            var frames = 0
            var attaches = 0
            var inFirstRow by mutableStateOf(true)
            setContent {
                val child =
                    remember {
                        movableContentOf {
                            SwingNode(
                                factory = { JPanel() },
                                modifier = frameCountingLayoutModifier(onAttach = { attaches++ }) { frames++ },
                            )
                        }
                    }
                Column {
                    Row { if (inFirstRow) child() }
                    Row { if (!inFirstRow) child() }
                }
            }
            mainClock.advanceTimeByFrame()
            assertEquals(1, frames, "the node's coroutine must run once after the first frame, before the move")

            inFirstRow = false
            awaitIdle()
            val framesAfterMove = frames
            mainClock.advanceTimeByFrame()

            assertEquals(framesAfterMove + 1, frames, "the coroutine the node started must keep running after the move")
            assertEquals(1, attaches, "the move must keep the node rather than attach a new one")
        }

    @Test
    fun everyNodeOfAChildsModifierIsAttachedWhileAnyOfItsNodesAttachesOrDetaches() =
        runComposeSwingTest {
            val chain = mutableListOf<SwingModifier.Node>()
            val sightings = mutableListOf<String>()
            var present by mutableStateOf(true)
            setContent {
                Row {
                    if (present) {
                        SwingNode(
                            factory = { JPanel() },
                            modifier =
                                SwingModifier then
                                    ChainLayoutElement("first", chain, sightings) then
                                    ChainComponentElement("component", chain, sightings) then
                                    ChainLayoutElement("second", chain, sightings),
                        )
                    }
                }
            }
            assertEquals(
                listOf("onAttach first: 3 of 3", "onAttach second: 3 of 3", "onAttach component: 3 of 3"),
                sightings,
                "every node of the chain must be attached before the first onAttach runs",
            )
            sightings.clear()

            present = false
            awaitIdle()

            assertEquals(
                listOf("onDetach component: 3 of 3", "onDetach first: 3 of 3", "onDetach second: 3 of 3"),
                sightings,
                "a released chain must run every onDetach before any node detaches",
            )
        }

    /** A node that overrides no intrinsic hook answers a size query through its own `measure`, as androidx's does. */
    @Test
    fun aLayoutModifierNodesSizeQueriesAnswerThroughItsOwnMeasure() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    SizedChild(0, SwingModifier then WidthDoublingElement)
                }
            }

            assertEquals(
                Dimension(2 * CHILD_WIDTH, CHILD_HEIGHT),
                containerPreferredSize(),
                "the row must ask for the width the node's measure reports",
            )
        }
}

/** Reports twice the width of the content it wraps, and places the content at its left edge. */
private object WidthDoublingElement : LayoutModifierNodeElement<WidthDoublingNode>() {
    override fun create(): WidthDoublingNode = WidthDoublingNode()

    override fun update(node: WidthDoublingNode) = Unit

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = WidthDoublingElement::class.hashCode()
}

private class WidthDoublingNode : LayoutModifierNode() {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints.copy(maxWidth = constraints.maxWidth / 2))
        return layout(2 * placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}

/** Counts each run of the node's `measure`; its intrinsic hooks answer without running it. */
internal fun SwingModifier.countingMeasures(onMeasure: () -> Unit): SwingModifier =
    this then MeasureCountingElement(onMeasure)

private class MeasureCountingElement(
    private val onMeasure: () -> Unit,
) : LayoutModifierNodeElement<MeasureCountingNode>() {
    override fun create(): MeasureCountingNode = MeasureCountingNode(onMeasure)

    override fun update(node: MeasureCountingNode) {
        node.onMeasure = onMeasure
    }

    override fun equals(other: Any?): Boolean = other is MeasureCountingElement

    override fun hashCode(): Int = MeasureCountingElement::class.hashCode()
}

private class MeasureCountingNode(
    var onMeasure: () -> Unit,
) : LayoutModifierNode() {
    override val name: String get() = "measureCounting"

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        onMeasure()
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
    ): Int = measurable.minIntrinsicWidth(height)

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
    ): Int = measurable.maxIntrinsicWidth(height)

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int = measurable.minIntrinsicHeight(width)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int = measurable.maxIntrinsicHeight(width)
}

private fun frameCountingLayoutModifier(
    onAttach: () -> Unit = {},
    onFrame: () -> Unit,
): SwingModifier = SwingModifier then FrameCountingLayoutModifierElement(onAttach, onFrame)

private class FrameCountingLayoutModifierElement(
    private val onAttach: () -> Unit,
    private val onFrame: () -> Unit,
) : LayoutModifierNodeElement<FrameCountingLayoutModifierNode>() {
    override fun create(): FrameCountingLayoutModifierNode = FrameCountingLayoutModifierNode(onAttach, onFrame)

    override fun update(node: FrameCountingLayoutModifierNode) {
        node.onFrame = onFrame
    }

    override fun equals(other: Any?): Boolean = other is FrameCountingLayoutModifierElement

    override fun hashCode(): Int = FrameCountingLayoutModifierElement::class.hashCode()
}

private class FrameCountingLayoutModifierNode(
    private val onAttached: () -> Unit,
    var onFrame: () -> Unit,
) : LayoutModifierNode() {
    override val name: String get() = "frameCounting"

    override fun onAttach() {
        onAttached()
        coroutineScope.launch {
            while (true) {
                withFrameNanos { onFrame() }
            }
        }
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}

/** A layout node recording, as it attaches and detaches, how many nodes of its modifier are attached. */
private class ChainLayoutElement(
    private val label: String,
    private val chain: MutableList<SwingModifier.Node>,
    private val sightings: MutableList<String>,
) : LayoutModifierNodeElement<ChainLayoutNode>() {
    override fun create(): ChainLayoutNode = ChainLayoutNode(label, chain, sightings).also { chain += it }

    override fun update(node: ChainLayoutNode) = Unit

    override fun equals(other: Any?): Boolean = other is ChainLayoutElement && other.label == label

    override fun hashCode(): Int = label.hashCode()
}

private class ChainLayoutNode(
    private val label: String,
    private val chain: List<SwingModifier.Node>,
    private val sightings: MutableList<String>,
) : LayoutModifierNode() {
    override val name: String get() = label

    override fun onAttach() {
        sightings += "onAttach $label: ${chain.count { it.isAttached }} of ${chain.size}"
    }

    override fun onDetach() {
        sightings += "onDetach $label: ${chain.count { it.isAttached }} of ${chain.size}"
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}

/** A component node recording, as it attaches and detaches, how many nodes of its modifier are attached. */
private class ChainComponentElement(
    private val label: String,
    private val chain: MutableList<SwingModifier.Node>,
    private val sightings: MutableList<String>,
) : SwingModifier.NodeElement<Component, ChainComponentNode>() {
    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): ChainComponentNode = ChainComponentNode(label, chain, sightings).also { chain += it }

    override fun update(node: ChainComponentNode) = Unit

    override fun equals(other: Any?): Boolean = other is ChainComponentElement && other.label == label

    override fun hashCode(): Int = label.hashCode()
}

private class ChainComponentNode(
    private val label: String,
    private val chain: List<SwingModifier.Node>,
    private val sightings: MutableList<String>,
) : SwingModifier.ComponentNode<Component>() {
    override fun onAttach() {
        sightings += "onAttach $label: ${chain.count { it.isAttached }} of ${chain.size}"
    }

    override fun onDetach() {
        sightings += "onDetach $label: ${chain.count { it.isAttached }} of ${chain.size}"
    }
}
