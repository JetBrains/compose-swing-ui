package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.IntState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.compose.swing.OnDemandComposition
import org.jetbrains.compose.swing.foundation.graphics.layoutBounds
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Rectangle
import javax.swing.CellRendererPane
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A validation marks a container valid as it ends, so an invalidation made while a validation runs is laid out by a
 * validation after it: a changed read behind an answer, and a layout node's `invalidateMeasurement` and
 * `invalidatePlacement`. A request the running pass already answers is dropped, as androidx drops it.
 */
class InvalidationDuringValidationTest {
    /**
     * The panel's preferred size is set, so its layout asks the container's once, as it lays it out. The policy
     * applies a snapshot as it answers, which reports the change at once.
     */
    @Test
    fun aReadChangedAsTheContainerAnswersASizeQueryIsSizedWith() =
        runComposeSwingTest {
            var extent by mutableIntStateOf(40)
            var changesAsAsked = false
            val policy =
                object : MeasurePolicy {
                    override fun MeasureScope.measure(
                        measurables: List<Measurable>,
                        constraints: Constraints,
                    ): MeasureResult = layout(extent, 20) {}

                    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
                        measurables: List<IntrinsicMeasurable>,
                        height: Int,
                    ): Int {
                        val asked = extent
                        if (changesAsAsked) {
                            changesAsAsked = false
                            Snapshot.withMutableSnapshot { extent = 60 }
                        }
                        return asked
                    }
                }
            setContent {
                SwingNode(
                    factory = { JPanel(FlowLayout(FlowLayout.LEADING, 0, 0)) },
                    modifier = SwingModifier.preferredSize(200, 200),
                ) {
                    Layout(measurePolicy = policy, modifier = SwingModifier.testTag("layout"))
                }
            }
            val layout = onNodeWithTag("layout").fetch<JComponent>()
            assertEquals(Dimension(40, 20), layout.size)

            changesAsAsked = true
            layout.revalidate()
            awaitIdle()

            assertEquals(Dimension(60, 20), layout.size, "the width changed as the size was asked")
        }

    /** The container above measures and places the inner one, and then changes what the inner one measured by. */
    @Test
    fun aReadChangedFromTheBlockOfTheContainerAboveIsMeasuredWith() =
        runComposeSwingTest {
            val extent = mutableIntStateOf(40)
            val trigger = mutableIntStateOf(0)
            setContent {
                Layout(
                    content = {
                        Layout(
                            measurePolicy = { _, _ -> layout(extent.intValue, 20) {} },
                            modifier = SwingModifier.testTag("inner"),
                        )
                    },
                    measurePolicy = writingAsItPlaces(trigger, extent),
                )
            }
            val inner = onNodeWithTag("inner").fetch<JComponent>()
            assertEquals(Dimension(40, 20), inner.size)

            trigger.intValue = 1
            awaitIdle()

            assertEquals(Dimension(WRITTEN, 20), inner.size, "the width changed as the container above placed it")
        }

    /**
     * The column measures and places both containers and then has each lay itself out. The second one's placement
     * block changes what the first one measured by once the column's own blocks have ended.
     */
    @Test
    fun aReadChangedFromTheBlockOfAContainerTheSameValidationLaysOutIsMeasuredWith() =
        runComposeSwingTest {
            val extent = mutableIntStateOf(40)
            val trigger = mutableIntStateOf(0)
            setContent {
                Column {
                    Layout(
                        measurePolicy = { _, _ -> layout(extent.intValue, 20) {} },
                        modifier = SwingModifier.testTag("first"),
                    )
                    Layout(
                        content = { Box(modifier = SwingModifier.size(10, 10)) },
                        measurePolicy = writingAsItPlaces(trigger, extent),
                    )
                }
            }
            val first = onNodeWithTag("first").fetch<JComponent>()
            assertEquals(Dimension(40, 20), first.size)

            trigger.intValue = 1
            awaitIdle()

            assertEquals(Dimension(WRITTEN, 20), first.size, "the width changed as the container beside it placed")
        }

    /** A Swing panel stands between the two containers, and its layout sizes it by what the inner one prefers. */
    @Test
    fun aReadChangedFromTheBlockOfAContainerAboveASwingParentIsMeasuredWith() =
        runComposeSwingTest {
            val extent = mutableIntStateOf(40)
            val trigger = mutableIntStateOf(0)
            setContent {
                Layout(
                    content = {
                        SwingNode(
                            factory = { JPanel(FlowLayout(FlowLayout.LEADING, 0, 0)) },
                            modifier = SwingModifier.testTag("panel"),
                        ) {
                            Layout(measurePolicy = { _, _ -> layout(extent.intValue, 20) {} })
                        }
                    },
                    measurePolicy = writingAsItPlaces(trigger, extent),
                )
            }
            val panel = onNodeWithTag("panel").fetch<JComponent>()
            assertEquals(Dimension(40, 20), panel.size)

            trigger.intValue = 1
            awaitIdle()

            assertEquals(Dimension(WRITTEN, 20), panel.size, "the width changed as the container above placed it")
        }

    /**
     * The column measures the host, whose second cell is wider than the one it holds valid from the first render.
     */
    @Test
    fun aReadChangedAsACellIsRenderedUnderAContainerBeingMeasuredIsSizedWithInThatQuery() =
        runComposeSwingTest {
            setContent { Column { HostOfTwoCells() } }
            val host = onNodeWithTag("host").fetch<JComponent>()

            host.revalidate()
            awaitIdle()

            assertEquals(Dimension(40, 20), host.size, "as wide as the second cell")
        }

    /**
     * The column measures the host, which invalidates the cell it holds as it renders the second one, and then the
     * box below it.
     */
    @Test
    fun aCellInvalidatedAsItIsRenderedUnderAContainerBeingMeasuredLeavesThatMeasureRunning() =
        runComposeSwingTest {
            setContent {
                Column {
                    HostOfTwoCells()
                    Box(modifier = SwingModifier.testTag("below").size(10, 10))
                }
            }
            val host = onNodeWithTag("host").fetch<JComponent>()

            host.revalidate()
            awaitIdle()

            assertEquals(Dimension(40, 20), host.size, "as wide as the second cell")
            assertEquals(20, onNodeWithTag("below").fetch().layoutBounds.y, "the box is placed below the host")
        }

    /**
     * The container above reads the inner one's baseline as it measures it, and then moves the content that baseline
     * follows, which the inner one reads only as it places.
     */
    @Test
    fun aLineMovedFromTheBlockOfTheContainerAboveThatMeasuredByItIsMeasuredWith() =
        runComposeSwingTest {
            val offset = mutableIntStateOf(0)
            val trigger = mutableIntStateOf(0)
            var baseline = AlignmentLine.UNSPECIFIED
            setContent {
                Layout(
                    content = {
                        Layout(
                            content = { DecoratedBaselineChild(baseline = 10) },
                            measurePolicy = { measurables, _ ->
                                val placeable = measurables.single().measure(Constraints())
                                layout(placeable.width, 2 * placeable.height) { placeable.place(0, offset.intValue) }
                            },
                        )
                    },
                    measurePolicy = writingAsItPlaces(trigger, offset) { baseline = it[FirstBaseline] },
                )
            }
            assertEquals(10, baseline)

            trigger.intValue = 1
            awaitIdle()

            assertEquals(10 + WRITTEN, baseline, "the baseline moved as the container above placed its container")
        }

    /**
     * The outer container's validation lays the inner one out once its own placement block has ended. The inner one's
     * placement block changes what the outer one placed it by, and then places its second child.
     */
    @Test
    fun aPlacementReadChangedFromTheBlockOfAContainerBelowThatGoesOnToPlaceIsPlacedWith() =
        runComposeSwingTest {
            val offset = mutableIntStateOf(0)
            val trigger = mutableIntStateOf(0)
            val policy = writingBetweenItsChildren(trigger, offset, measuresAfterWrite = false)
            setContent { PlacedByAnOffsetWrittenBelow(offset, policy) }
            val inner = onNodeWithTag("inner").fetch<JComponent>()
            assertEquals(0, inner.layoutBounds.x)

            trigger.intValue = 1
            awaitIdle()

            assertEquals(WRITTEN, inner.layoutBounds.x, "placed by the offset the container below wrote as it placed")
        }

    /** As above, with the inner container's placement block measuring its second child once it has written. */
    @Test
    fun aPlacementReadChangedFromTheBlockOfAContainerBelowThatGoesOnToMeasureIsPlacedWith() =
        runComposeSwingTest {
            val offset = mutableIntStateOf(0)
            val trigger = mutableIntStateOf(0)
            val policy = writingBetweenItsChildren(trigger, offset, measuresAfterWrite = true)
            setContent { PlacedByAnOffsetWrittenBelow(offset, policy) }
            val inner = onNodeWithTag("inner").fetch<JComponent>()
            assertEquals(0, inner.layoutBounds.x)

            trigger.intValue = 1
            awaitIdle()

            assertEquals(WRITTEN, inner.layoutBounds.x, "placed by the offset the container below wrote as it placed")
        }

    /**
     * The outer container measures the inner one in its placement block, by a least width the placement replay a
     * layout node of the inner one requests finds changed, which resizes the inner one and then validates it. The
     * inner one's placement block, once it is that wide, changes the offset the outer one places it at.
     */
    @Test
    fun aPlacementReadChangedFromTheBlockOfAContainerAPlacementReplayValidatesIsPlacedWith() =
        runComposeSwingTest {
            val requests = Requests(measurement = false)
            var width = 10
            val writer = OffsetWriter()
            val offset = writer.offset
            setContent {
                Layout(
                    content = {
                        Layout(
                            measurePolicy = writer.policy,
                            modifier = SwingModifier.testTag("inner").then(RequestingElement(requests)),
                        )
                    },
                    measurePolicy = { measurables, _ ->
                        layout(200, 20) {
                            measurables.single().measure(Constraints(minWidth = width)).place(offset.intValue, 0)
                        }
                    },
                )
            }
            val inner = onNodeWithTag("inner").fetch<JComponent>()
            assertEquals(Rectangle(0, 0, 10, 20), inner.layoutBounds)

            width = WRITTEN
            checkNotNull(requests.node).request()
            awaitIdle()

            assertEquals(false, writer.reentered, "the inner placement block ran to its end before it was run again")
            assertEquals(Rectangle(WRITTEN + STEP, 0, WRITTEN, 20), inner.layoutBounds, "placed by the offset it wrote")
        }

    /**
     * As above, one level deeper: the middle container's placement replay validates the inner one, whose placement
     * block changes the least width the outer one measures the middle one by, in its own placement block.
     */
    @Test
    fun aPlacementReadChangedFromTheBlockOfAContainerAPlacementReplayBelowValidatesIsPlacedWith() =
        runComposeSwingTest {
            val requests = Requests(measurement = false)
            var width = 10
            val writer = OffsetWriter()
            setContent { MeasuredByAnOffsetWrittenTwoLevelsBelow(writer, requests) { width } }
            val middle = onNodeWithTag("middle").fetch<JComponent>()
            val inner = onNodeWithTag("inner").fetch<JComponent>()
            assertEquals(Rectangle(0, 0, 100, 20), middle.layoutBounds)

            width = WRITTEN
            checkNotNull(requests.node).request()
            awaitIdle()

            assertEquals(false, writer.reentered, "the inner placement block ran to its end before it was run again")
            assertEquals(Rectangle(0, 0, 100 + WRITTEN, 20), middle.layoutBounds, "measured by the offset written")
            assertEquals(Rectangle(STEP, 0, WRITTEN, 20), inner.layoutBounds)
        }

    /**
     * A Swing panel that is a validate root holds both containers, with no Foundation container above it that its
     * validation lays out. The first one's placement block changes what the second one measured by.
     */
    @Test
    fun aReadChangedFromTheBlockOfASiblingInAValidationOnlySwingContainersTakePartInIsMeasuredWith() =
        runComposeSwingTest {
            val extent = mutableIntStateOf(40)
            val trigger = mutableIntStateOf(0)
            setContent {
                SwingNode(
                    factory = {
                        object : JPanel(FlowLayout(FlowLayout.LEADING, 0, 0)) {
                            override fun isValidateRoot() = true
                        }
                    },
                    modifier = SwingModifier.preferredSize(200, 200),
                ) {
                    Layout(
                        content = { Box(modifier = SwingModifier.size(10, 10)) },
                        measurePolicy = writingAsItPlaces(trigger, extent),
                    )
                    Layout(
                        measurePolicy = { _, _ -> layout(extent.intValue, 20) {} },
                        modifier = SwingModifier.testTag("second"),
                    )
                }
            }
            val second = onNodeWithTag("second").fetch<JComponent>()
            assertEquals(Dimension(40, 20), second.size)

            trigger.intValue = 1
            awaitIdle()

            assertEquals(Dimension(WRITTEN, 20), second.size, "the width changed as the container beside it placed")
        }

    @Test
    fun aMeasurementInvalidatedAsTheNodeIsPlacedIsMeasuredAgain() =
        runComposeSwingTest {
            val requests = Requests(measurement = true)
            requests.fromPlacement = 1
            setContent {
                Row {
                    Box(modifier = SwingModifier.testTag("box").then(RequestingElement(requests)).size(20, 20))
                    Box(modifier = SwingModifier.testTag("next").size(20, 20))
                }
            }

            assertEquals(STEP, onNodeWithTag("box").fetch().layoutBounds.x, "placed by the step taken")
            assertEquals(20 + STEP, onNodeWithTag("next").fetch().layoutBounds.x, "and measured with it")
        }

    @Test
    fun aMeasurementInvalidatedAsTheContainerMeasuresTheNodeIsThatMeasure() =
        runComposeSwingTest {
            val requests = Requests(measurement = true)
            setContent { RowOfOneRequesting(requests) }
            val measures = requests.measures

            requests.fromMeasure = REQUESTS
            onNodeWithTag("row").fetch<JComponent>().revalidate()
            awaitIdle()

            assertEquals(measures + 1, requests.measures, "measured once by the pass")
        }

    /**
     * The container's measure block asks the width the node's component prefers, which runs the node's `measure`, and
     * its placement block then measures the component, which is the measure the request asks for. androidx does the
     * same: the component's `measurePending` is cleared as the container measures it, and the container, which is
     * measuring, drops the request the intrinsic query passes up to it.
     */
    @Test
    fun aMeasurementInvalidatedAsTheNodeAnswersTheMeasureBlockOfTheContainerIsTheMeasureOfThatPass() =
        runComposeSwingTest {
            val requests = Requests(measurement = true)
            var asked = 0
            setContent {
                Layout(
                    content = {
                        Box(modifier = SwingModifier.testTag("box").then(RequestingElement(requests)).size(20, 20))
                    },
                    measurePolicy = { measurables, constraints ->
                        asked = measurables.single().maxIntrinsicWidth(constraints.maxHeight)
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            measurables.single().measure(Constraints()).place(0, 0)
                        }
                    },
                    modifier = containerModifier(200, 100),
                )
            }
            assertEquals(20, asked)
            val measures = requests.measures

            requests.fromMeasure = 1
            onNodeWithTag(CONTAINER_TAG).fetch<JComponent>().revalidate()
            awaitIdle()

            assertEquals(measures + 2, requests.measures, "measured for the width asked and by the pass, once each")
            assertEquals(20, asked, "the width asked before the step was taken stands")
            assertEquals(STEP, onNodeWithTag("box").fetch().layoutBounds.x, "laid out with the step taken")
        }

    /**
     * The container's measure block measures the component and then has the node take a step, which changes what the
     * component measures by. androidx measures the component again: its measure has returned, so the request sets its
     * `measurePending`.
     */
    @Test
    fun aMeasurementInvalidatedLaterInTheMeasureBlockThatMeasuredTheNodeIsMeasuredAgain() =
        runComposeSwingTest {
            val requests = Requests(measurement = true)
            var requestsAfterMeasure = false
            setContent {
                Layout(
                    content = {
                        Box(modifier = SwingModifier.then(RequestingElement(requests)).size(20, 20))
                        Box(modifier = SwingModifier.testTag("next").size(20, 20))
                    },
                    measurePolicy = { measurables, constraints ->
                        val first = measurables[0].measure(Constraints())
                        if (requestsAfterMeasure) {
                            requestsAfterMeasure = false
                            requests.node?.request()
                        }
                        val next = measurables[1].measure(Constraints())
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            first.place(0, 0)
                            next.place(first.width, 0)
                        }
                    },
                    modifier = containerModifier(200, 100),
                )
            }
            assertEquals(20, onNodeWithTag("next").fetch().layoutBounds.x)

            requestsAfterMeasure = true
            onNodeWithTag(CONTAINER_TAG).fetch<JComponent>().revalidate()
            awaitIdle()

            assertEquals(20 + STEP, onNodeWithTag("next").fetch().layoutBounds.x, "placed after the widened component")
        }

    @Test
    fun aMeasurementInvalidatedAsThePlacementBlockOfTheContainerMeasuresTheNodeIsThatMeasure() =
        runComposeSwingTest {
            val requests = Requests(measurement = true)
            setContent {
                Layout(
                    content = { Box(modifier = SwingModifier.then(RequestingElement(requests)).size(20, 20)) },
                    measurePolicy = { measurables, constraints ->
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            measurables.single().measure(Constraints()).place(0, 0)
                        }
                    },
                    modifier = containerModifier(200, 100),
                )
            }
            val measures = requests.measures

            requests.fromMeasure = REQUESTS
            onNodeWithTag(CONTAINER_TAG).fetch<JComponent>().revalidate()
            awaitIdle()

            assertEquals(measures + 1, requests.measures, "measured once by the pass")
        }

    @Test
    fun aPlacementInvalidatedAsTheContainerMeasuresTheNodeIsPlacedByThatPass() =
        runComposeSwingTest {
            val requests = Requests(measurement = false)
            setContent { RowOfOneRequesting(requests) }
            val placements = requests.placements

            requests.fromMeasure = REQUESTS
            onNodeWithTag("row").fetch<JComponent>().revalidate()
            awaitIdle()

            assertEquals(placements + 1, requests.placements, "placed once by the pass")
            assertEquals(STEP, onNodeWithTag("box").fetch().layoutBounds.x, "by the step taken")
        }

    @Test
    fun aPlacementInvalidatedAsTheContainerPlacesTheNodeIsThatPlacement() =
        runComposeSwingTest {
            val requests = Requests(measurement = false)
            setContent { RowOfOneRequesting(requests) }
            val placements = requests.placements

            requests.fromPlacement = REQUESTS
            onNodeWithTag("row").fetch<JComponent>().revalidate()
            awaitIdle()

            assertEquals(placements + 1, requests.placements, "placed once by the pass")
        }

    /**
     * The row measures the box holding the node's component again as the sibling changes size, and leaves the box
     * valid at the size it holds, so no layout pass of the box follows that measure.
     */
    @Test
    fun aPlacementInvalidatedAsAValidContainerIsMeasuredByItsOwnContainerIsPlacedAgain() =
        runComposeSwingTest {
            val requests = Requests(measurement = false)
            var sibling by mutableIntStateOf(20)
            setContent {
                Row {
                    Box(modifier = SwingModifier.size(100, 40)) {
                        Box(modifier = SwingModifier.testTag("box").then(RequestingElement(requests)).size(20, 20))
                    }
                    Box(modifier = SwingModifier.size(sibling, 20))
                }
            }
            assertEquals(0, onNodeWithTag("box").fetch().layoutBounds.x)

            requests.fromMeasure = 1
            sibling = 30
            awaitIdle()

            assertEquals(STEP, onNodeWithTag("box").fetch().layoutBounds.x, "placed by the step taken")
        }

    /** As above for a measurement, which the measure of the box that the row runs does not lay out. */
    @Test
    fun aMeasurementInvalidatedAsAValidContainerIsMeasuredByItsOwnContainerIsMeasuredAgain() =
        runComposeSwingTest {
            val requests = Requests(measurement = true)
            var sibling by mutableIntStateOf(20)
            setContent {
                Row {
                    Box(modifier = SwingModifier.size(100, 40)) {
                        Box(modifier = SwingModifier.testTag("box").then(RequestingElement(requests)).size(20, 20))
                    }
                    Box(modifier = SwingModifier.size(sibling, 20))
                }
            }
            assertEquals(0, onNodeWithTag("box").fetch().layoutBounds.x)

            requests.fromMeasure = 1
            sibling = 30
            awaitIdle()

            assertEquals(STEP, onNodeWithTag("box").fetch().layoutBounds.x, "laid out with the step taken")
        }

    /**
     * The container's measure block has the node take a step, and then measures the box holding the node's component,
     * which is valid and keeps its size, so no layout pass of the box follows that measure.
     */
    @Test
    fun aMeasurementInvalidatedBeforeAValidContainerIsMeasuredByItsOwnContainerIsMeasuredAgain() =
        runComposeSwingTest {
            val requests = Requests(measurement = true)
            var requestsBeforeMeasure = false
            setContent {
                Layout(
                    content = {
                        Box(modifier = SwingModifier.size(100, 40)) {
                            Box(modifier = SwingModifier.testTag("box").then(RequestingElement(requests)).size(20, 20))
                        }
                    },
                    measurePolicy = { measurables, constraints ->
                        if (requestsBeforeMeasure) {
                            requestsBeforeMeasure = false
                            requests.node?.request()
                        }
                        val placeable = measurables.single().measure(Constraints())
                        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
                    },
                    modifier = containerModifier(200, 100),
                )
            }
            assertEquals(0, onNodeWithTag("box").fetch().layoutBounds.x)

            requestsBeforeMeasure = true
            onNodeWithTag(CONTAINER_TAG).fetch<JComponent>().revalidate()
            awaitIdle()

            assertEquals(STEP, onNodeWithTag("box").fetch().layoutBounds.x, "laid out with the step taken")
        }

    /**
     * The panel's preferred size is set, so its layout asks the row's once, as it lays it out, and the same validation
     * then lays the row out, which is the measure the request asks for. androidx does the same: the component's
     * `measurePending` is cleared as the row measures it, and the ancestor that asked the size is measuring and drops
     * the request the intrinsic query passes up to it, so the size it took from the query stands.
     */
    @Test
    fun aMeasurementInvalidatedAsTheNodeAnswersASizeQueryIsTheMeasureOfThePassThatFollows() =
        runComposeSwingTest {
            val requests = Requests(measurement = true)
            setContent { RowUnderFlowLayout(RequestingElement(requests)) }
            val row = onNodeWithTag("row").fetch<JComponent>()
            assertEquals(Dimension(20, 20), row.size)

            requests.fromMeasure = 1
            row.revalidate()
            awaitIdle()

            assertEquals(Dimension(20, 20), row.size, "the size asked before the step was taken stands")
            val placedAt = onNodeWithTag("box").fetch().layoutBounds.x
            assertEquals(STEP / 2, placedAt, "laid out with the step taken, centered in the row")
        }

    /**
     * As above for a placement: androidx drops a request to place a node whose measure is pending, as it is from the
     * size query on, and the row's pass places the component.
     */
    @Test
    fun aPlacementInvalidatedAsTheNodeAnswersASizeQueryIsThePlacementOfThePassThatFollows() =
        runComposeSwingTest {
            val requests = Requests(measurement = false)
            setContent { RowUnderFlowLayout(RequestingElement(requests)) }
            val row = onNodeWithTag("row").fetch<JComponent>()

            requests.fromMeasure = 1
            row.revalidate()
            awaitIdle()

            assertEquals(Dimension(20, 20), row.size, "the size asked before the step was taken stands")
            val placedAt = onNodeWithTag("box").fetch().layoutBounds.x
            assertEquals(STEP / 2, placedAt, "placed by the step taken, centered in the row")
        }

    /** The row is valid and nothing lays it out after the query: the test asks its minimum size, which nothing uses. */
    @Test
    fun aMeasurementInvalidatedAsTheNodeAnswersASizeQueryNoLayoutFollowsIsMeasuredAgain() =
        runComposeSwingTest {
            val requests = Requests(measurement = true)
            setContent { RowUnderFlowLayout(RequestingElement(requests)) }
            val row = onNodeWithTag("row").fetch<JComponent>()
            assertEquals(Dimension(20, 20), row.size)

            requests.fromMeasure = 1
            row.minimumSize
            awaitIdle()

            assertEquals(Dimension(20 + STEP, 20), row.size, "sized with the step taken as the size was asked")
        }

    /** The size query holds the tree lock, so placement follows after the query returns. */
    @Test
    fun aPlacementInvalidatedAsTheNodeAnswersASizeQueryOfAValidContainerIsPlacedAfterValidation() =
        runComposeSwingTest {
            val requests = Requests(measurement = false)
            setContent { RowUnderFlowLayout(RequestingElement(requests)) }
            val row = onNodeWithTag("row").fetch<JComponent>()

            requests.fromMeasure = 1
            row.minimumSize
            assertEquals(0, onNodeWithTag("box").fetch().layoutBounds.x, "placement waits for the tree lock")
            awaitIdle()

            assertEquals(STEP, onNodeWithTag("box").fetch().layoutBounds.x, "placed by the step taken")
            assertEquals(Dimension(20, 20), row.size)
        }

    /**
     * The container measures the row in its measure block and asks the row's width in its placement block, which runs
     * the node's `measure` after the row's measure, and then lays the row out by that measure. androidx measures the
     * component again as the row is laid out: the request sets its `measurePending`, which the row's measure cleared
     * before it.
     */
    @Test
    fun aMeasurementInvalidatedAsTheNodeAnswersASizeQueryAfterTheContainerWasMeasuredIsMeasuredAgain() =
        runComposeSwingTest {
            val requests = Requests(measurement = true)
            var requestsAsAsked = false
            setContent {
                Layout(
                    content = {
                        Row(modifier = SwingModifier.testTag("row")) {
                            Box(modifier = SwingModifier.then(RequestingElement(requests)).size(20, 20))
                        }
                    },
                    measurePolicy = { measurables, constraints ->
                        val placeable = measurables.single().measure(Constraints())
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            if (requestsAsAsked) {
                                requestsAsAsked = false
                                requests.fromMeasure = 1
                            }
                            measurables.single().maxIntrinsicWidth(placeable.height)
                            placeable.place(0, 0)
                        }
                    },
                    modifier = containerModifier(200, 100),
                )
            }
            val row = onNodeWithTag("row").fetch<JComponent>()
            assertEquals(Dimension(20, 20), row.layoutBounds.size)

            requestsAsAsked = true
            row.revalidate()
            awaitIdle()

            assertEquals(Dimension(20 + STEP, 20), row.layoutBounds.size, "measured with the step taken")
        }

    /** The panel's layout asks the row's preferred size in each validation, which runs the node's `measure`. */
    @Test
    fun aNodeInvalidatingItsMeasurementOnEveryMeasureUnderASizeQueryIsLaidOutByItsLastMeasure() =
        runComposeSwingTest {
            val requests = Requests(measurement = true)
            setContent { RowUnderFlowLayout(RequestingOnEveryMeasureElement(requests)) }

            onNodeWithTag("row").fetch<JComponent>().revalidate()
            awaitIdle()

            assertEquals(requests.measures, onNodeWithTag("box").fetch().layoutBounds.x, "placed by its last measure")
        }

    @Test
    fun aNodeInvalidatingItsPlacementOnEveryMeasureUnderASizeQueryIsLaidOutByItsLastMeasure() =
        runComposeSwingTest {
            val requests = Requests(measurement = false)
            setContent { RowUnderFlowLayout(RequestingOnEveryMeasureElement(requests)) }

            onNodeWithTag("row").fetch<JComponent>().revalidate()
            awaitIdle()

            assertEquals(requests.measures, onNodeWithTag("box").fetch().layoutBounds.x, "placed by its last measure")
        }
}

/** How far a [RequestingNode] moves and widens its content with each request it makes. */
private const val STEP = 10

/** How many requests a node makes where one more than the pass answers would show. */
private const val REQUESTS = 3

/** What [writingAsItPlaces] writes. */
private const val WRITTEN = 60

/**
 * A row of a set preferred size, which its parent's layout therefore asks no size of, holding one box with a
 * [RequestingNode].
 */
@Composable
private fun RowOfOneRequesting(requests: Requests) {
    Row(modifier = SwingModifier.testTag("row").preferredSize(200, 100)) {
        Box(modifier = SwingModifier.testTag("box").then(RequestingElement(requests)).size(20, 20))
    }
}

/** A row of one box with the layout node of [requesting], under a Swing parent whose layout asks the row's size. */
@Composable
private fun RowUnderFlowLayout(requesting: LayoutModifierNodeElement<*>) {
    SwingNode(
        factory = { JPanel(FlowLayout(FlowLayout.LEADING, 0, 0)) },
        modifier = SwingModifier.preferredSize(200, 200),
    ) {
        Row(modifier = SwingModifier.testTag("row")) {
            Box(modifier = SwingModifier.testTag("box").then(requesting).size(20, 20))
        }
    }
}

/**
 * A [CellHost] rendering a cell of width 20 and then one of width 40 through one composition. A cell the host
 * validated stays valid in its `CellRendererPane`, and answers the size it holds until it is invalidated.
 */
@Composable
private fun HostOfTwoCells() {
    val row = remember { mutableIntStateOf(0) }
    val context = rememberCompositionContext()
    val cell =
        remember(context) {
            OnDemandComposition(context) {
                Layout(measurePolicy = { _, _ -> layout(20 + 20 * row.intValue, 20) {} })
            }
        }
    DisposableEffect(cell) { onDispose { cell.dispose() } }
    SwingNode(
        factory = { CellHost { index -> checkNotNull(cell.recompose { row.intValue = index }) } },
        modifier = SwingModifier.testTag("host"),
    )
}

/**
 * A container placing its one child, a container of two boxes laid out by [innerPolicy], at [offset], which
 * [innerPolicy] writes as it places; see [writingBetweenItsChildren].
 */
@Composable
private fun PlacedByAnOffsetWrittenBelow(
    offset: IntState,
    innerPolicy: MeasurePolicy,
) {
    Layout(
        content = {
            Layout(
                content = {
                    Box(modifier = SwingModifier.size(10, 10))
                    Box(modifier = SwingModifier.size(10, 10))
                },
                measurePolicy = innerPolicy,
                modifier = SwingModifier.testTag("inner"),
            )
        },
        measurePolicy = { measurables, _ ->
            val placeable = measurables.single().measure(Constraints())
            layout(200, 20) { placeable.place(offset.intValue, 0) }
        },
    )
}

/**
 * A container measuring its one child, the middle container, by a least width of 100 plus the offset [writer] writes,
 * in its placement block. The middle container measures the inner one by the least width [width] answers, in its
 * placement block; the inner one is laid out by [writer] and holds the node [requests] makes.
 */
@Composable
private fun MeasuredByAnOffsetWrittenTwoLevelsBelow(
    writer: OffsetWriter,
    requests: Requests,
    width: () -> Int,
) {
    Layout(
        content = {
            Layout(
                content = {
                    Layout(
                        measurePolicy = writer.policy,
                        modifier = SwingModifier.testTag("inner").then(RequestingElement(requests)),
                    )
                },
                measurePolicy = { measurables, constraints ->
                    layout(constraints.minWidth, 20) {
                        measurables.single().measure(Constraints(minWidth = width())).place(0, 0)
                    }
                },
                modifier = SwingModifier.testTag("middle"),
            )
        },
        measurePolicy = { measurables, _ ->
            layout(300, 20) {
                measurables.single().measure(Constraints(minWidth = 100 + writer.offset.intValue)).place(0, 0)
            }
        },
    )
}

/**
 * A component as wide as the wider of two cells. Asked its preferred size for the first time since it was invalidated,
 * it renders each cell through [render] into a `CellRendererPane` that keeps the cell, and validates the cell before
 * it reads what the cell prefers.
 */
private class CellHost(
    private val render: (row: Int) -> Component,
) : JComponent() {
    private val rendererPane = CellRendererPane().also { add(it) }
    private var cellWidth = -1

    override fun invalidate() {
        cellWidth = -1
        super.invalidate()
    }

    override fun getPreferredSize(): Dimension {
        if (cellWidth < 0) {
            var width = 0
            repeat(2) { row ->
                val cell = render(row)
                rendererPane.add(cell)
                cell.validate()
                width = maxOf(width, cell.preferredSize.width)
            }
            cellWidth = width
        }
        return Dimension(cellWidth, 20)
    }
}

/**
 * Measures its one child unconstrained, hands the placeable to [onMeasured], and places it. Once [trigger] is set, its
 * placement block writes [WRITTEN] to [state] after placing the child, once, and applies the write, which reports the
 * change at once. It reads [state] nowhere.
 */
private fun writingAsItPlaces(
    trigger: IntState,
    state: MutableIntState,
    onMeasured: (Placeable) -> Unit = {},
): MeasurePolicy {
    var written = false
    return MeasurePolicy { measurables, _ ->
        val writes = trigger.intValue != 0
        val placeable = measurables.single().measure(Constraints())
        onMeasured(placeable)
        layout(placeable.width, placeable.height) {
            placeable.place(0, 0)
            if (writes && !written) {
                written = true
                state.intValue = WRITTEN
                Snapshot.sendApplyNotifications()
            }
        }
    }
}

/**
 * Places its two children side by side. Once [trigger] is set, its placement block writes [WRITTEN] to [state] between
 * the two, once, and applies the write, which reports the change at once. It reads [state] nowhere. It measures the
 * second child in its placement block, after the write, where [measuresAfterWrite], and in its measure block otherwise.
 */
private fun writingBetweenItsChildren(
    trigger: IntState,
    state: MutableIntState,
    measuresAfterWrite: Boolean,
): MeasurePolicy {
    var written = false
    return MeasurePolicy { measurables, _ ->
        val writes = trigger.intValue != 0
        val first = measurables[0].measure(Constraints())
        val second = if (measuresAfterWrite) null else measurables[1].measure(Constraints())
        layout(20, 10) {
            first.place(0, 0)
            if (writes && !written) {
                written = true
                state.intValue = WRITTEN
                Snapshot.sendApplyNotifications()
            }
            (second ?: measurables[1].measure(Constraints())).place(10, 0)
        }
    }
}

/** A policy writing an offset its container above reads, and whether its placement block was run while it ran. */
private class OffsetWriter {
    val offset = mutableIntStateOf(0)
    var reentered = false
    private var placing = false

    /**
     * Lays out at the least width it is offered. Once that width is [WRITTEN], its placement block writes [WRITTEN] to
     * [offset], once, and applies the write, which reports the change at once.
     */
    val policy =
        MeasurePolicy { _, constraints ->
            layout(constraints.minWidth, 20) {
                reentered = reentered || placing
                placing = true
                if (constraints.minWidth == WRITTEN && offset.intValue == 0) {
                    offset.intValue = WRITTEN
                    Snapshot.sendApplyNotifications()
                }
                placing = false
            }
        }
}

/**
 * The requests a [RequestingNode] makes, its `invalidateMeasurement` where [measurement] and its `invalidatePlacement`
 * otherwise, and how often the node has run.
 */
private class Requests(
    val measurement: Boolean,
) {
    /** The node the element made last. */
    var node: RequestingNode? = null

    /** How many more runs of `measure` make the request. */
    var fromMeasure = 0

    /** How many more runs of the placement make the request, once the content is placed. */
    var fromPlacement = 0

    var measures = 0
    var placements = 0
}

private data class RequestingElement(
    private val requests: Requests,
) : LayoutModifierNodeElement<RequestingNode>() {
    override fun create(): RequestingNode = RequestingNode(requests).also { requests.node = it }

    override fun update(node: RequestingNode) = Unit
}

/**
 * Reports its content widened by its step as it stands when `measure` starts, and places the content at its step.
 * Each request it makes takes a step of [STEP] first.
 */
private class RequestingNode(
    private val requests: Requests,
) : LayoutModifierNode() {
    private var step = 0

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        requests.measures++
        val placeable = measurable.measure(constraints)
        val width = placeable.width + step
        if (requests.fromMeasure > 0) {
            requests.fromMeasure--
            request()
        }
        return layout(width, placeable.height) {
            requests.placements++
            placeable.place(step, 0)
            if (requests.fromPlacement > 0) {
                requests.fromPlacement--
                request()
            }
        }
    }

    /** Takes a step and makes the request. */
    fun request() {
        step += STEP
        if (requests.measurement) invalidateMeasurement() else invalidatePlacement()
    }
}

private data class RequestingOnEveryMeasureElement(
    private val requests: Requests,
) : LayoutModifierNodeElement<RequestingOnEveryMeasureNode>() {
    override fun create(): RequestingOnEveryMeasureNode = RequestingOnEveryMeasureNode(requests)

    override fun update(node: RequestingOnEveryMeasureNode) = Unit
}

/** Makes its request on every `measure`, and places its content at the number of the `measure` that placed it. */
private class RequestingOnEveryMeasureNode(
    private val requests: Requests,
) : LayoutModifierNode() {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val measure = ++requests.measures
        val placeable = measurable.measure(constraints)
        if (requests.measurement) invalidateMeasurement() else invalidatePlacement()
        return layout(placeable.width, placeable.height) { placeable.place(measure, 0) }
    }
}
