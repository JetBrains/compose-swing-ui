package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.graphics.Brush
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.Constraints
import org.jetbrains.compose.swing.foundation.layout.Layout
import org.jetbrains.compose.swing.foundation.layout.LayoutModifierNodeElement
import org.jetbrains.compose.swing.foundation.layout.Measurable
import org.jetbrains.compose.swing.foundation.layout.MeasureResult
import org.jetbrains.compose.swing.foundation.layout.MeasureScope
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.RowScope
import org.jetbrains.compose.swing.foundation.layout.offset
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.componentOrientation
import org.jetbrains.compose.swing.modifier.layout.minimumSize
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Color
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Behavioral tests for `animateContentSize`: the container itself is placed at its own real size (a sibling's
 * own origin is what makes the animated *apparent* size observable, `clipToBounds` narrowing paint to it in the
 * meantime).
 */
class AnimateContentSizeTest {
    @Test
    fun `the size the content is first measured at is taken rather than animated to`() =
        runComposeSwingTest {
            val finished = mutableListOf<Pair<Dimension, Dimension>>()
            mainClock.autoAdvance = false
            setContent {
                Row(modifier = SwingModifier.testTag(ROW)) {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag(CONTAINER)
                                .animateContentSize(
                                    finishedListener = { initial, target -> finished += initial to target },
                                ),
                    ) { Body(size = COLLAPSED) }
                    Sibling()
                }
            }
            val widths =
                List(FRAMES_SAMPLED) {
                    driveOneFrame()
                    layoutRow()
                    sibling.x
                }

            assertEquals(listOf(COLLAPSED.width), widths.distinct(), "the width traveled rather than snapped: $widths")
            assertTrue(finished.isEmpty(), "an animation ran and finished as the container appeared: $finished")
        }

    @Test
    fun `a change in the content's size travels the container through the sizes between`() =
        runComposeSwingTest {
            var expanded by mutableStateOf(false)
            setContent {
                Row(modifier = SwingModifier.testTag(ROW)) {
                    Box(modifier = SwingModifier.testTag(CONTAINER).animateContentSize()) {
                        Body(size = if (expanded) EXPANDED else COLLAPSED)
                    }
                    Sibling()
                }
            }
            layoutRow()
            assertEquals(COLLAPSED.width, sibling.x, "the container did not take the room its content needs")

            mainClock.autoAdvance = false
            expanded = true
            val widths =
                List(FRAMES_SAMPLED) {
                    driveOneFrame()
                    layoutRow()
                    sibling.x
                }

            assertEquals(
                widths.sorted(),
                widths,
                "the container did not travel towards its content's new size: $widths",
            )
            assertTrue(
                widths.any { it > COLLAPSED.width && it < EXPANDED.width },
                "the container's width jumped rather than traveled: $widths",
            )
        }

    @Test
    fun `while the size travels the content paints only inside the box it has traveled to`() =
        runComposeSwingTest {
            var expanded by mutableStateOf(false)
            setContent {
                Row(modifier = SwingModifier.testTag(ROW)) {
                    Box(modifier = SwingModifier.testTag(CONTAINER).animateContentSize()) {
                        Box(
                            modifier =
                                SwingModifier
                                    .preferredSize(if (expanded) EXPANDED else COLLAPSED)
                                    .background(Brush.of(Color.RED)),
                        )
                    }
                    Sibling()
                }
            }
            mainClock.autoAdvance = false
            expanded = true
            repeat(FRAMES_INTO_THE_ANIMATION) { driveOneFrame() }
            layoutRow()
            val traveled = sibling.x
            assertTrue(traveled in (COLLAPSED.width + 1) until EXPANDED.width - 2, "the width is mid-travel: $traveled")

            val painted = onNodeWithTag(CONTAINER).captureToImage()

            assertEquals(Color.RED.rgb, painted.getRGB(traveled - 1, 2), "inside the traveled box")
            assertEquals(0, painted.getRGB(traveled + 1, 2), "outside the traveled box")
        }

    @Test
    fun `the container settles at the size its content prefers`() =
        runComposeSwingTest {
            var expanded by mutableStateOf(false)
            setContent {
                Row(modifier = SwingModifier.testTag(ROW)) {
                    Box(modifier = SwingModifier.testTag(CONTAINER).animateContentSize()) {
                        Body(size = if (expanded) EXPANDED else COLLAPSED)
                    }
                    Sibling()
                }
            }

            expanded = true
            awaitIdle()
            layoutRow()

            assertEquals(EXPANDED.width, sibling.x, "the container did not settle at the width of its content")
        }

    @Test
    fun `the alignment places the content inside a box larger than it`() =
        runComposeSwingTest {
            val slots = shrinkingSlots(ComponentOrientation.LEFT_TO_RIGHT)
            val roomier = slots.filter { it.box > COLLAPSED.width }
            assertTrue(roomier.isNotEmpty(), "the box never stood larger than the content it shrank to: $slots")
            for (slot in roomier) {
                assertEquals(
                    slot.boxX + slot.box - COLLAPSED.width,
                    slot.contentX,
                    "the content did not sit at the trailing edge of the box: $slots",
                )
            }
        }

    @Test
    fun `the alignment mirrors the content inside the box under a right-to-left reading order`() =
        runComposeSwingTest {
            val slots = shrinkingSlots(ComponentOrientation.RIGHT_TO_LEFT)
            val roomier = slots.filter { it.box > COLLAPSED.width }
            assertTrue(roomier.isNotEmpty(), "the box never stood larger than the content it shrank to: $slots")
            for (slot in roomier) {
                assertEquals(
                    slot.boxX,
                    slot.contentX,
                    "the content did not sit at the trailing edge, which a right-to-left order puts on the " +
                        "left: $slots",
                )
            }
        }

    @Test
    fun `a container parked while it animates comes back at the size of its content`() =
        runComposeSwingTest {
            var active by mutableStateOf(true)
            var expanded by mutableStateOf(false)
            setContent {
                ReusableContentHost(active = active) {
                    Row(modifier = SwingModifier.testTag(ROW)) {
                        Box(
                            modifier =
                                SwingModifier.testTag(CONTAINER).animateContentSize(alignment = Alignment.BottomEnd),
                        ) {
                            Body(size = if (expanded) EXPANDED else COLLAPSED)
                        }
                        Sibling()
                    }
                }
            }

            mainClock.autoAdvance = false
            expanded = true
            repeat(FRAMES_INTO_THE_ANIMATION) { driveOneFrame() }
            layoutRow()
            assertTrue(sibling.x < EXPANDED.width, "precondition: the size was still traveling: ${sibling.x}")

            // Parking gives the running animation up partway: coming back, the container starts from its content.
            active = false
            awaitIdle()
            active = true
            mainClock.autoAdvance = true
            awaitIdle()
            layoutRow()

            assertEquals(EXPANDED.width, sibling.x, "the container came back at a size other than its content's")
            assertEquals(
                Rectangle(0, 0, EXPANDED.width, EXPANDED.height),
                onNodeWithTag(CONTAINER).fetch().bounds,
                "the container came back placing its content somewhere the animation it gave up left it",
            )
        }

    /**
     * Where a container aligning its content at the bottom end places it frame by frame while the content shrinks,
     * in a row laid out under [orientation]: the box is the row slot between the container's edge and its sibling.
     */
    private suspend fun ComposeSwingTest.shrinkingSlots(orientation: ComponentOrientation): List<Slot> {
        var expanded by mutableStateOf(true)
        setContent {
            Row(modifier = SwingModifier.testTag(ROW).componentOrientation(orientation)) {
                Box(modifier = SwingModifier.testTag(CONTAINER).animateContentSize(alignment = Alignment.BottomEnd)) {
                    Body(size = if (expanded) EXPANDED else COLLAPSED)
                }
                Sibling()
            }
        }

        mainClock.autoAdvance = false
        expanded = false
        return List(FRAMES_SAMPLED) {
            driveOneFrame()
            layoutRow()
            val container = onNodeWithTag(CONTAINER).fetch()
            if (orientation.isLeftToRight) {
                Slot(boxX = 0, box = sibling.x, contentX = container.x)
            } else {
                val boxX = sibling.x + sibling.width
                Slot(boxX = boxX, box = row.width - boxX, contentX = container.x)
            }
        }
    }

    /** One frame of [shrinkingSlots]. */
    private data class Slot(
        val boxX: Int,
        val box: Int,
        val contentX: Int,
    )

    @Test
    fun `the finished callback is handed the sizes the animation ran between`() =
        runComposeSwingTest {
            val finished = mutableListOf<Pair<Dimension, Dimension>>()
            var expanded by mutableStateOf(false)
            setContent {
                Row {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag(CONTAINER)
                                .animateContentSize(
                                    finishedListener = { initial, target -> finished += initial to target },
                                ),
                    ) { Body(size = if (expanded) EXPANDED else COLLAPSED) }
                }
            }

            expanded = true
            awaitIdle()

            assertEquals(listOf(COLLAPSED to EXPANDED), finished, "the container did not report the size it traveled")
        }

    @Test
    fun `a container on the row's baseline travels to its content's new size in one animation`() =
        runComposeSwingTest {
            val finished = mutableListOf<Pair<Dimension, Dimension>>()
            var expanded by mutableStateOf(false)
            setContent {
                Row(modifier = SwingModifier.testTag(ROW)) {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag(CONTAINER)
                                .alignByBaseline()
                                .animateContentSize(
                                    finishedListener = { initial, target -> finished += initial to target },
                                ),
                    ) { Body(size = if (expanded) EXPANDED else COLLAPSED) }
                    Sibling()
                }
            }

            mainClock.autoAdvance = false
            expanded = true
            val widths =
                List(FRAMES_SAMPLED) {
                    driveOneFrame()
                    layoutRow()
                    sibling.x
                }
            assertEquals(widths.sorted(), widths, "the container did not travel towards the new size: $widths")
            assertTrue(
                widths.any { it > COLLAPSED.width && it < EXPANDED.width },
                "the container did not progress towards the new size: $widths",
            )

            mainClock.autoAdvance = true
            awaitIdle()

            assertEquals(EXPANDED.width, sibling.x, "the container did not settle at its content's size")
            assertEquals(listOf(COLLAPSED to EXPANDED), finished, "the change did not run as one animation")
        }

    @Test
    fun `a size change while an animation runs retargets it rather than starting it over`() =
        runComposeSwingTest {
            val finished = mutableListOf<Pair<Dimension, Dimension>>()
            var size by mutableStateOf(COLLAPSED)
            setContent {
                Row(modifier = SwingModifier.testTag(ROW)) {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag(CONTAINER)
                                .animateContentSize(
                                    finishedListener = { initial, target -> finished += initial to target },
                                ),
                    ) { Body(size = size) }
                    Sibling()
                }
            }

            mainClock.autoAdvance = false
            size = EXPANDED
            repeat(FRAMES_INTO_THE_ANIMATION) { driveOneFrame() }
            layoutRow()
            val partway = sibling.x
            assertTrue(
                partway > COLLAPSED.width && partway < EXPANDED.width,
                "the container was not partway through the first animation: $partway",
            )

            size = FURTHER
            repeat(FRAMES_INTO_THE_ANIMATION) { driveOneFrame() }
            layoutRow()
            assertTrue(
                sibling.x > partway,
                "the container fell back towards the size it started from, so the animation began again " +
                    "rather than taking a new target: ${sibling.x} after $partway",
            )

            mainClock.autoAdvance = true
            awaitIdle()

            assertEquals(1, finished.size, "the animation that was retargeted reported itself finished: $finished")
            val (initial, target) = finished.single()
            assertEquals(FURTHER, target, "the container did not settle at the size it was last given")
            assertTrue(
                initial.width > COLLAPSED.width,
                "the animation was handed the size the content had left rather than the size the container " +
                    "stood at when it was retargeted: $initial",
            )
        }

    @Test
    fun `a retarget completes when the finished listener writes state the same composable reads`() =
        runComposeSwingTest {
            var size by mutableStateOf(COLLAPSED)
            setContent {
                var lastRun by remember { mutableStateOf<Pair<Dimension, Dimension>?>(null) }
                Row(modifier = SwingModifier.testTag(ROW)) {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag(CONTAINER)
                                .animateContentSize(
                                    finishedListener = { initial, target -> lastRun = initial to target },
                                ),
                    ) { Body(size = size) }
                    Sibling()
                    Label(text = lastRun?.toString() ?: "none")
                }
            }

            mainClock.autoAdvance = false
            size = EXPANDED
            repeat(FRAMES_INTO_THE_ANIMATION) { driveOneFrame() }
            size = FURTHER
            repeat(FRAMES_INTO_THE_ANIMATION) { driveOneFrame() }

            mainClock.autoAdvance = true
            awaitIdle()
            layoutRow()

            assertEquals(FURTHER.width, sibling.x, "the container did not settle at the size it was last given")
        }

    @Test
    fun `a policy tree's preferred size is the target, not the size traveling towards it`() =
        runComposeSwingTest {
            var expanded by mutableStateOf(false)
            setContent {
                Row(modifier = SwingModifier.testTag(ROW)) {
                    Column(modifier = SwingModifier.testTag(COLUMN)) {
                        Box(modifier = SwingModifier.animateContentSize()) {
                            Body(size = if (expanded) EXPANDED else COLLAPSED)
                        }
                    }
                    Sibling()
                }
            }
            val column = onNodeWithTag(COLUMN).fetch<JComponent>()

            mainClock.autoAdvance = false
            expanded = true
            val partway =
                List(FRAMES_INTO_THE_ANIMATION) {
                    driveOneFrame()
                    layoutRow()
                    sibling.x
                }.last()
            assertTrue(
                partway > COLLAPSED.width && partway < EXPANDED.width,
                "the container was not partway through the animation, so this test cannot tell a pass-through " +
                    "answer apart from one that merely settled: $partway",
            )

            assertEquals(
                EXPANDED,
                column.preferredSize,
                "a policy container's own preferred size must answer with the target the content is traveling " +
                    "to, not the size a frame of that travel currently stands at",
            )
        }

    @Test
    fun `a validate cycle asking preferred, minimum and layout extents in turn starts exactly one animation`() =
        runComposeSwingTest {
            var expanded by mutableStateOf(false)
            setContent {
                Row(modifier = SwingModifier.testTag(ROW)) {
                    Box(modifier = SwingModifier.testTag(CONTAINER).animateContentSize()) {
                        Body(size = if (expanded) EXPANDED else COLLAPSED)
                    }
                    Sibling()
                }
            }
            layoutRow()

            mainClock.autoAdvance = false
            expanded = true
            val widths =
                List(FRAMES_SAMPLED) {
                    driveOneFrame()
                    // A validate cycle a foreign container would run: ask the preferred size, the minimum
                    // size, and only then lay out. An intrinsic hook that is not pass-through would start a
                    // second, competing animation here on every one of these frames.
                    row.preferredSize
                    row.minimumSize
                    layoutRow()
                    sibling.x
                }

            assertTrue(
                widths.distinct().size > 1 && widths.last() < EXPANDED.width,
                "the sampled frames did not travel inside the animation, so the order below proves nothing: $widths",
            )
            assertEquals(
                widths.sorted(),
                widths,
                "asking the preferred and minimum size before laying out perturbed the single animation " +
                    "underway, rather than leaving it to the layout pass alone: $widths",
            )

            mainClock.autoAdvance = true
            awaitIdle()
            layoutRow()
            assertEquals(EXPANDED.width, sibling.x, "the animation never settled on the size its content asks for")
        }

    @Test
    fun `a row's size query at a slot wider than the content starts no animation`() =
        runComposeSwingTest {
            val finished = mutableListOf<Pair<Dimension, Dimension>>()
            setContent {
                Row(modifier = SwingModifier.testTag(ROW)) {
                    // A weighted slot sized by the wider sibling, which the content does not fill.
                    Box(
                        modifier =
                            SwingModifier
                                .testTag(CONTAINER)
                                .weight(1f, fill = false)
                                .alignByBaseline()
                                .animateContentSize(
                                    finishedListener = { initial, target -> finished += initial to target },
                                ),
                    ) { Body(size = COLLAPSED) }
                    Body(size = EXPANDED, modifier = SwingModifier.weight(1f))
                }
            }
            awaitIdle()
            val container = onNodeWithTag(CONTAINER).fetch<JComponent>()

            mainClock.autoAdvance = false
            val widths =
                List(FRAMES_SAMPLED) {
                    row.invalidate()
                    row.preferredSize
                    row.minimumSize
                    layoutRow()
                    driveOneFrame()
                    container.width
                }
            mainClock.autoAdvance = true
            awaitIdle()

            assertEquals(listOf(COLLAPSED.width), widths.distinct(), "a size query moved the container: $widths")
            assertEquals(COLLAPSED, container.size, "a size query left the container at a size of its own")
            assertTrue(finished.isEmpty(), "a size query started an animation: $finished")
        }

    @Test
    fun `a row's intrinsic height ignores an offsetting baseline-aligned child's own layout-modifier-node chain`() =
        runComposeSwingTest {
            val finished = mutableListOf<Pair<Dimension, Dimension>>()
            var expanded by mutableStateOf(false)
            setContent {
                Row(modifier = SwingModifier.testTag(ROW)) {
                    OffsettingBaselineChild(baseline = 5, height = 20)
                    PlainBaselineChild(baseline = 30, height = PLAIN_HEIGHT)
                    Box(
                        modifier =
                            SwingModifier
                                .testTag(CONTAINER)
                                .animateContentSize(
                                    finishedListener = { initial, target -> finished += initial to target },
                                ),
                    ) { Body(size = if (expanded) EXPANDED else COLLAPSED) }
                    Sibling()
                }
            }
            layoutRow()

            assertEquals(
                PLAIN_HEIGHT,
                row.height,
                "the row's height must come from the plain baseline child alone, since a chain carrying a " +
                    "layout-modifier node cannot answer a baseline without measuring the offset it applies",
            )

            mainClock.autoAdvance = false
            expanded = true
            val widths =
                List(FRAMES_SAMPLED) {
                    driveOneFrame()
                    row.preferredSize
                    row.minimumSize
                    layoutRow()
                    sibling.x
                }
            assertEquals(widths.sorted(), widths, "the container did not travel towards the new size: $widths")
            val fixedWidth = OFFSETTING_WIDTH + PLAIN_WIDTH
            assertTrue(
                widths.any { it > fixedWidth + COLLAPSED.width && it < fixedWidth + EXPANDED.width },
                "the container did not progress towards the new size: $widths",
            )

            mainClock.autoAdvance = true
            awaitIdle()
            layoutRow()

            assertEquals(
                OFFSETTING_WIDTH + PLAIN_WIDTH + EXPANDED.width,
                sibling.x,
                "the container did not settle at its content's size",
            )
            assertEquals(listOf(COLLAPSED to EXPANDED), finished, "the change did not run as one animation")
        }

    @Test
    fun `a frame measures the traveling chain once however often a validate cycle asks`() =
        runComposeSwingTest {
            val probe = MeasureCountingElement()
            var expanded by mutableStateOf(false)
            setContent {
                Row(modifier = SwingModifier.testTag(ROW)) {
                    Box(modifier = SwingModifier.testTag(CONTAINER).animateContentSize().then(probe)) {
                        Body(size = if (expanded) EXPANDED else COLLAPSED)
                    }
                    Sibling()
                }
            }

            mainClock.autoAdvance = false
            expanded = true
            // The recomposition and the frame that starts the animation, which measure more than once.
            repeat(2) { driveOneFrame() }
            val frames =
                List(FRAMES_SAMPLED) {
                    val measuresBefore = probe.measures
                    driveOneFrame()
                    repeat(2) {
                        row.preferredSize
                        row.minimumSize
                        layoutRow()
                    }
                    (probe.measures - measuresBefore) to sibling.x
                }

            val widths = frames.map { it.second }
            assertTrue(
                widths.distinct().size > 1 && widths.last() < EXPANDED.width,
                "the sampled frames did not fall inside the animation, so they measure nothing: $widths",
            )
            assertTrue(
                frames.all { it.first == 1 },
                "each frame of the travel must measure the chain exactly once, whoever asks: $frames",
            )
        }

    @Test
    fun `a stock widget target is refused at its first placement rather than as it is declared`() {
        var placed by mutableStateOf(false)
        var measuredUnplaced = false
        val failure =
            assertFailsWith<IllegalStateException> {
                runComposeSwingTest {
                    setContent {
                        val place = placed
                        Layout(
                            measurePolicy = { measurables, constraints ->
                                val placeable = measurables.single().measure(constraints)
                                layout(placeable.width, placeable.height) { if (place) placeable.place(0, 0) }
                            },
                            content = {
                                SwingNode(factory = { JPanel() }, modifier = SwingModifier.animateContentSize())
                            },
                        )
                    }
                    measuredUnplaced = true
                    placed = true
                    awaitIdle()
                }
            }

        assertTrue(measuredUnplaced, "the target was refused before it was placed: ${failure.message}")
        assertTrue(
            failure.message.orEmpty().contains("JPanel"),
            "the error names the component it was handed: ${failure.message}",
        )
    }
}

/**
 * A layout modifier passing its child's measurement through, counting how often it is measured. Its
 * intrinsic answers pass through too, so an intrinsic query is not counted as a measurement.
 */
private class MeasureCountingElement : LayoutModifierNodeElement<MeasureCountingNode>() {
    var measures: Int = 0
        private set

    override fun create(): MeasureCountingNode = MeasureCountingNode(onMeasure = { measures++ })

    override fun update(node: MeasureCountingNode) = Unit

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = System.identityHashCode(this)
}

private class MeasureCountingNode(
    private val onMeasure: () -> Unit,
) : LayoutModifierNodeWithPassThroughIntrinsics() {
    override val name: String get() = "measureCounting"

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        onMeasure()
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}

/**
 * A layout-modifier node that passes its child's measurement and intrinsic answers straight through,
 * standing in for a stateful node - an animation, most of all - wherever a test only needs one in a
 * child's layout-modifier chain.
 */
private class PassThroughNode : LayoutModifierNodeWithPassThroughIntrinsics() {
    override val name: String get() = "passThrough"

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}

private object PassThroughElement : LayoutModifierNodeElement<PassThroughNode>() {
    override fun create(): PassThroughNode = PassThroughNode()

    override fun update(node: PassThroughNode) = Unit

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = System.identityHashCode(this)
}

/** A component reporting a fixed [reported] baseline at whatever size it is asked. */
private class ControlledBaselinePanel(
    private val reported: Int,
) : JPanel() {
    override fun getBaseline(
        width: Int,
        height: Int,
    ): Int = reported
}

/**
 * A row child on the shared baseline whose chain also carries a [PassThroughNode], moved off its
 * placement by an unrelated [offset] - the shape a stateful node such as an animation leaves behind.
 */
@Composable
private fun RowScope.OffsettingBaselineChild(
    baseline: Int,
    height: Int,
) = SwingNode(
    factory = { ControlledBaselinePanel(baseline) },
    modifier =
        SwingModifier
            .preferredSize(OFFSETTING_WIDTH, height)
            .minimumSize(OFFSETTING_WIDTH, height)
            // moves the content without changing what it measures at
            .offset(y = 6)
            .alignByBaseline()
            .then(PassThroughElement),
)

/** A row child on the shared baseline whose chain carries no layout-modifier node. */
@Composable
private fun RowScope.PlainBaselineChild(
    baseline: Int,
    height: Int,
) = SwingNode(
    factory = { ControlledBaselinePanel(baseline) },
    modifier =
        SwingModifier
            .preferredSize(PLAIN_WIDTH, height)
            .minimumSize(PLAIN_WIDTH, height)
            .alignByBaseline(),
)

private const val ROW = "row"
private const val COLUMN = "column"
private const val CONTAINER = "container"
private const val SIBLING = "sibling"

/** The width every baseline-alignment fixture child asks for, immaterial to the tests reading it. */
private const val OFFSETTING_WIDTH = 20
private const val PLAIN_WIDTH = 20

/** The height that must alone decide the row's own height. */
private const val PLAIN_HEIGHT = 40

/** A content of a known size, so what the container does with it can be asserted in pixels. */
@Composable
private fun Body(
    size: Dimension,
    modifier: SwingModifier = SwingModifier,
) = Label(text = "", modifier = modifier.preferredSize(size))

/**
 * A fixed-size sibling placed after the animated container, so a `Row` places it at that container's
 * own apparent right edge - the animated size the container's own real bounds do not carry, since those
 * stay at its content's real, un-animated size and rely on `clipToBounds` to hide the difference.
 */
@Composable
private fun Sibling() = Label(text = "", modifier = SwingModifier.testTag(SIBLING).preferredSize(Dimension(1, 1)))

private val ComposeSwingTest.row: JComponent
    get() = onNodeWithTag(ROW).fetch<JComponent>()

private val ComposeSwingTest.sibling: JComponent
    get() = onNodeWithTag(SIBLING).fetch<JComponent>()

/** Drives the row's own layout manager once, so the animated container's own chain measures and places again. */
private fun ComposeSwingTest.layoutRow() {
    row.size = row.preferredSize
    row.doLayout()
}

/** The size the content takes collapsed. */
private val COLLAPSED = Dimension(40, 20)

/** The size it takes expanded, larger on both axes so both cells of the alignment can be read off one animation. */
private val EXPANDED = Dimension(160, 80)

/** A third size, given to a container already traveling towards [EXPANDED]. */
private val FURTHER = Dimension(220, 120)

/** Frames to sample, enough to cover the passes that mount and measure the content and animate past them. */
private const val FRAMES_SAMPLED = 8

/** Frames that leave an animation running well short of the size it aims at. */
private const val FRAMES_INTO_THE_ANIMATION = 3
