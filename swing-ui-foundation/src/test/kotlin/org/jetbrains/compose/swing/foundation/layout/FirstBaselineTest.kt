package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.foundation.graphics.Brush
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.border
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.withRecordedRepaints
import java.awt.Color
import java.awt.Container
import javax.swing.JComponent
import javax.swing.border.EmptyBorder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A policy reads where a child puts an alignment line, such as its first baseline, through `placeable[line]`. */
class FirstBaselineTest {
    @Test
    fun aLabelsFirstBaselineIsItsComponentBaselineAtTheMeasuredSize() =
        runComposeSwingTest {
            val baselines = linesOf { Label("label") }

            val label = children().single()
            val expected = label.getBaseline(label.width, label.height)
            assertTrue(expected >= 0, "the label under test must carry a baseline")
            assertEquals(listOf(expected), baselines)
        }

    @Test
    fun aPaddedLabelsFirstBaselineIsShiftedByThePaddingAbove() =
        runComposeSwingTest {
            val baselines = linesOf { Label("label", modifier = SwingModifier.padding(top = 7)) }

            val label = children().single()
            assertEquals(
                listOf(7 + label.getBaseline(label.width, label.height)),
                baselines,
                "the baseline must be measured from the top of the space the padding reserved",
            )
        }

    @Test
    fun aLayoutChildsFirstBaselineIsTheLineItsPolicyProvidesBelowItsInsetsAndPadding() =
        runComposeSwingTest {
            val baselines =
                linesOf {
                    Layout(
                        measurePolicy = { _, _ -> layout(CHILD_WIDTH, CHILD_HEIGHT, mapOf(FirstBaseline to 13)) {} },
                        modifier =
                            SwingModifier
                                .border(EmptyBorder(5, 0, 0, 0))
                                .padding(top = 7),
                    )
                }

            assertEquals(
                listOf(25),
                baselines,
                "the policy's line is inside the child's insets, and the padding reserves space above both",
            )
        }

    @Test
    fun aLayoutChildsVerticalLineIsTheLineItsPolicyProvidesRightOfItsInsetsAndPadding() =
        runComposeSwingTest {
            val line = VerticalAlignmentLine(::minOf)
            val lines =
                linesOf(line) {
                    Layout(
                        measurePolicy = { _, _ -> layout(CHILD_WIDTH, CHILD_HEIGHT, mapOf(line to 13)) {} },
                        modifier =
                            SwingModifier
                                .border(EmptyBorder(0, 5, 0, 0))
                                .padding(start = 7),
                    )
                }

            assertEquals(
                listOf(25),
                lines,
                "the policy's line is inside the child's insets, and the padding reserves space left of both",
            )
        }

    @Test
    fun aLayoutModifiersLineTakesThePlaceOfTheLineOfTheContentItWraps() =
        runComposeSwingTest {
            val baselines =
                linesOf {
                    Layout(
                        measurePolicy = { _, _ -> layout(CHILD_WIDTH, CHILD_HEIGHT, mapOf(FirstBaseline to 13)) {} },
                        modifier = SwingModifier.padding(top = 7).lineProviding(),
                    )
                }

            assertEquals(
                listOf(7 + MODIFIER_LINE),
                baselines,
                "the modifier's line is inside its own box, and the padding reserves space above it",
            )
        }

    @Test
    fun aConstrainableComponentOfYourOwnProvidesTheLinesItNames() =
        runComposeSwingTest {
            val baselines = linesOf { SwingNode(factory = { LineProvidingComponent() }) }

            assertEquals(listOf(OWN_LINE), baselines, "the line the component names, though it has no Swing baseline")
        }

    @Test
    fun aLineTheChildsPolicyDoesNotProvideAnswersUnspecified() =
        runComposeSwingTest {
            val lines =
                linesOf(VerticalAlignmentLine(::minOf)) {
                    Layout(
                        measurePolicy = { _, _ -> layout(CHILD_WIDTH, CHILD_HEIGHT, mapOf(FirstBaseline to 13)) {} },
                    )
                }

            assertEquals(listOf(AlignmentLine.UNSPECIFIED), lines)
        }

    @Test
    fun aChildWithoutABaselineAnswersUnspecified() =
        runComposeSwingTest {
            val baselines = linesOf { Layout(measurePolicy = { _, _ -> layout(CHILD_WIDTH, CHILD_HEIGHT) {} }) }

            assertEquals(listOf(AlignmentLine.UNSPECIFIED), baselines)
        }

    @Test
    fun aRowLinesABoxUpOnTheBaselineOfTheFieldInside() =
        runComposeSwingTest {
            setContent {
                Row(modifier = containerModifier(400, 200)) {
                    Label("label", modifier = SwingModifier.alignByBaseline())
                    Box(modifier = SwingModifier.alignByBaseline()) {
                        TextField("field", onValueChange = {}, modifier = SwingModifier.padding(top = 10))
                    }
                }
            }

            val (label, box) = children()
            val field = box.getComponent(0) as JComponent
            assertEquals(
                label.y + label.getBaseline(label.width, label.height),
                box.y + field.y + field.getBaseline(field.width, field.height),
                "the row must put the field's baseline, which the box puts, on the label's",
            )
        }

    @Test
    fun aContainersFirstBaselineIsTheHighestItsChildrenPutWhereItPlacesThem() =
        runComposeSwingTest {
            val baselines =
                linesOf {
                    Box {
                        Layout(measurePolicy = {
                            _,
                            _,
                            ->
                            layout(CHILD_WIDTH, CHILD_HEIGHT, mapOf(FirstBaseline to 25)) {}
                        })
                        Layout(
                            measurePolicy = {
                                _,
                                _,
                                ->
                                layout(CHILD_WIDTH, CHILD_HEIGHT, mapOf(FirstBaseline to 13)) {}
                            },
                            modifier = SwingModifier.padding(top = 7),
                        )
                    }
                }

            assertEquals(listOf(20), baselines, "the higher of 25 and 7 + 13, as FirstBaseline merges them")
        }

    @Test
    fun aLineAContainersPolicyProvidesWinsOverTheLinesOfItsChildren() =
        runComposeSwingTest {
            val baselines =
                linesOf {
                    Layout(
                        content = {
                            Layout(measurePolicy = {
                                _,
                                _,
                                ->
                                layout(CHILD_WIDTH, CHILD_HEIGHT, mapOf(FirstBaseline to 13)) {}
                            })
                        },
                        measurePolicy = { measurables, constraints ->
                            val placeable = measurables.single().measure(constraints)
                            layout(placeable.width, placeable.height, mapOf(FirstBaseline to 25)) {
                                placeable.place(0, 0)
                            }
                        },
                    )
                }

            assertEquals(listOf(25), baselines, "the line the container's policy provides, not its child's")
        }

    @Test
    fun aContainerMeasuringItsChildInItsPlacementPutsTheLineOfThatChild() =
        runComposeSwingTest {
            var baseline = AlignmentLine.UNSPECIFIED
            var failure: IllegalStateException? = null
            setContent {
                Layout(
                    content = {
                        Layout(
                            content = { Label("label") },
                            measurePolicy = { measurables, constraints ->
                                layout(CHILD_WIDTH, CHILD_HEIGHT) {
                                    measurables.single().measure(constraints).place(0, 0)
                                }
                            },
                        )
                    },
                    modifier = containerModifier(200, 200),
                    measurePolicy = { measurables, constraints ->
                        val placeable = measurables.single().measure(Constraints(maxWidth = 200))
                        try {
                            baseline = placeable[FirstBaseline]
                        } catch (e: IllegalStateException) {
                            failure = e
                        }
                        layout(constraints.minWidth, constraints.minHeight) { placeable.place(0, 0) }
                    },
                )
            }
            awaitIdle()

            assertNull(failure, "reading a line of a container that measures its child in placement must not fail")
            val label = (children().single() as Container).getComponent(0)
            assertEquals(label.getBaseline(label.width, label.height), baseline, "the line the label puts")
        }

    @Test
    fun readingAContainersLineAgainRunsNoPlacementOfIt() =
        runComposeSwingTest {
            var placementRuns = 0
            var runsWhileReading = -1
            setContent {
                Layout(
                    content = {
                        Layout(
                            content = { Label("label") },
                            measurePolicy = { measurables, constraints ->
                                val placeable = measurables.single().measure(constraints)
                                layout(placeable.width, placeable.height) {
                                    placementRuns++
                                    placeable.place(0, 0)
                                }
                            },
                        )
                    },
                    modifier = containerModifier(200, 200),
                    measurePolicy = { measurables, constraints ->
                        val placeable = measurables.single().measure(Constraints(maxWidth = 200))
                        val runs = placementRuns
                        repeat(3) { placeable[FirstBaseline] }
                        runsWhileReading = placementRuns - runs
                        layout(constraints.minWidth, constraints.minHeight) { placeable.place(0, 0) }
                    },
                )
            }
            awaitIdle()

            assertEquals(1, runsWhileReading, "a container's lines are worked out by the first read, not by each read")
        }

    @Test
    fun measuringAContainerWithoutReadingALineRunsNoPlacementOfIt() =
        runComposeSwingTest {
            var placementRuns = 0
            var runsWhileMeasuring = -1
            setContent {
                Layout(
                    content = {
                        Layout(
                            content = { Label("label") },
                            measurePolicy = { measurables, constraints ->
                                val placeable = measurables.single().measure(constraints)
                                layout(placeable.width, placeable.height) {
                                    placementRuns++
                                    placeable.place(0, 0)
                                }
                            },
                        )
                    },
                    modifier = containerModifier(200, 200),
                    measurePolicy = { measurables, constraints ->
                        val runs = placementRuns
                        val placeable = measurables.single().measure(Constraints(maxWidth = 200))
                        runsWhileMeasuring = placementRuns - runs
                        layout(constraints.minWidth, constraints.minHeight) { placeable.place(0, 0) }
                    },
                )
            }
            awaitIdle()

            assertEquals(0, runsWhileMeasuring, "a container's lines are worked out only for a read")
        }

    @Test
    fun theLineOfAChildItsParentLeavesUnplacedFollowsAStateTheChildsPlacementReads() =
        runComposeSwingTest {
            var y by mutableIntStateOf(0)
            var baseline = AlignmentLine.UNSPECIFIED
            setContent {
                Layout(
                    content = {
                        Layout(
                            content = {
                                Layout(measurePolicy = { _, _ -> layout(20, 20, mapOf(FirstBaseline to 10)) {} })
                            },
                            measurePolicy = { measurables, constraints ->
                                val placeable = measurables.single().measure(constraints)
                                layout(placeable.width, placeable.height + 50) { placeable.place(0, y) }
                            },
                        )
                    },
                    modifier = containerModifier(200, 200),
                    measurePolicy = { measurables, constraints ->
                        baseline = measurables.single().measure(Constraints(maxWidth = 200))[FirstBaseline]
                        layout(constraints.minWidth, constraints.minHeight) {}
                    },
                )
            }
            awaitIdle()
            assertEquals(10, baseline)

            y = 20
            awaitIdle()

            assertEquals(30, baseline, "the line the child's placement moved")
        }

    /**
     * A container's line is worked out by replaying its placement outside the placement block's own pass. That
     * replay must not swallow the placement block's own recorded reads: a state read in a layer block the placement
     * sets up must keep repainting on a later change, even after a parent has read the container's line in between.
     */
    @Test
    fun aLayerReadSurvivesAParentReadingTheContainersLineInALaterEvent() =
        runComposeSwingTest {
            var y by mutableIntStateOf(0)
            var byLine by mutableStateOf(false)
            val alpha = mutableFloatStateOf(1f)
            var layerRuns = 0
            val block: PlacementLayerScope.() -> Unit = {
                layerRuns++
                this.alpha = alpha.floatValue
            }
            setContent {
                Layout(
                    content = {
                        Layout(
                            content = {
                                Layout(measurePolicy = { _, _ -> layout(20, 20, mapOf(FirstBaseline to 10)) {} })
                            },
                            measurePolicy = { measurables, constraints ->
                                val placeable = measurables.single().measure(constraints)
                                layout(placeable.width, placeable.height + 50) {
                                    placeable.placeWithLayer(0, y, layerBlock = block)
                                }
                            },
                        )
                    },
                    modifier = containerModifier(200, 200),
                    measurePolicy = { measurables, constraints ->
                        val box = measurables.single().measure(Constraints(maxWidth = 200, maxHeight = 200))
                        layout(constraints.minWidth, constraints.minHeight) {
                            box.place(0, if (byLine) 100 - box[FirstBaseline] else 0)
                        }
                    },
                )
            }
            awaitIdle()

            // Move the layer, then have the parent start reading the container's line in a later event: the line
            // replay this triggers must not be recorded under the placement block's own scope.
            y = 20
            awaitIdle()
            byLine = true
            awaitIdle()

            val before = layerRuns
            alpha.floatValue = 0.5f
            awaitIdle()
            assertTrue(
                layerRuns > before,
                "the layer block must run again after the line replay ($before -> $layerRuns)",
            )
        }

    /**
     * A layer moves no line, so a read only in the layer block places nothing while the parent reads the container's
     * line: the block runs again and the child repaints with what it sets.
     */
    @Test
    fun aLayerReadRunsNoPlacementWhileItsParentReadsTheContainersLine() =
        runComposeSwingTest {
            val alpha = mutableFloatStateOf(1f)
            val scale = mutableFloatStateOf(1f)
            var innerRuns = 0
            var outerRuns = 0
            var layerRuns = 0
            val block: PlacementLayerScope.() -> Unit = {
                layerRuns++
                this.alpha = alpha.floatValue
                scaleX = scale.floatValue
            }
            setContent {
                Layout(
                    content = {
                        Layout(
                            measurePolicy = { measurables, constraints ->
                                val placeable = measurables.single().measure(constraints)
                                layout(placeable.width, placeable.height + 50) {
                                    innerRuns++
                                    placeable.placeWithLayer(0, 20, layerBlock = block)
                                }
                            },
                        ) {
                            Layout(
                                modifier = SwingModifier.testTag(LAYERED_TAG).background(Brush.of(Color.RED)),
                                measurePolicy = { _, _ -> layout(20, 20, mapOf(FirstBaseline to 10)) {} },
                            )
                        }
                    },
                    modifier = containerModifier(200, 200),
                    measurePolicy = { measurables, constraints ->
                        val box = measurables.single().measure(Constraints(maxWidth = 200, maxHeight = 200))
                        layout(constraints.minWidth, constraints.minHeight) {
                            outerRuns++
                            box.place(0, 100 - box[FirstBaseline])
                        }
                    },
                )
            }
            awaitIdle()
            val layered = onNodeWithTag(LAYERED_TAG).fetch<JComponent>()

            for ((change, state) in listOf("alpha" to alpha, "scale" to scale)) {
                innerRuns = 0
                outerRuns = 0
                layerRuns = 0
                val recorded =
                    withRecordedRepaints { recorder ->
                        state.floatValue = 0.5f
                        awaitIdle()
                        recorder
                    }
                assertEquals(0, innerRuns, "the container's placement, for $change")
                assertEquals(0, outerRuns, "the parent's placement by the container's line, for $change")
                assertEquals(1, layerRuns, "the layer block, for $change")
                assertTrue(recorded.repaintsOf(layered) > 0, "the child must be repainted, for $change")
            }

            val image = onNodeWithTag(LAYERED_TAG).captureToImage()
            assertEquals(128.0, (image.getRGB(10, 10) ushr 24).toDouble(), 1.0, "the faded background")
            assertEquals(0, image.getRGB(2, 10), "the background the halved width leaves bare")
        }

    @Test
    fun aStateAContainerPlacesByRunsEachPlacementOnceWhileItsParentReadsTheContainersLine() =
        runComposeSwingTest {
            val content = setLinePlacedContent()
            awaitIdle()

            for (y in listOf(10, 20)) {
                content.innerRuns = 0
                content.outerRuns = 0
                content.y = y
                awaitIdle()
                assertEquals(2, content.innerRuns, "the container's placement and its line replay, for y = $y")
                assertEquals(1, content.outerRuns, "the parent's placement by the moved line, for y = $y")
            }
        }

    @Test
    fun aStateAContainerPlacesByRunsEachPlacementOnceAfterItsParentMeasuresItAgainWithoutLayingItOut() =
        runComposeSwingTest {
            val content = setLinePlacedContent()
            awaitIdle()
            content.offerShrink = 1
            awaitIdle()

            content.innerRuns = 0
            content.outerRuns = 0
            content.y = 20
            awaitIdle()

            assertEquals(2, content.innerRuns, "the container's placement and its line replay")
            assertEquals(1, content.outerRuns, "the parent's placement by the moved line")
        }

    @Test
    fun aStateAContainerPlacesByRunsItsPlacementOnceAfterItsParentStopsReadingTheContainersLine() =
        runComposeSwingTest {
            val content = setLinePlacedContent()
            awaitIdle()
            content.byLine = false
            awaitIdle()

            content.innerRuns = 0
            content.outerRuns = 0
            content.y = 20
            awaitIdle()

            assertEquals(1, content.innerRuns, "the container's placement alone")
            assertEquals(0, content.outerRuns, "the parent reads no line of the container")
        }

    /** A child measured past its offer is centered on it, as androidx centers it, and its line moves with it. */
    @Test
    fun aLayoutChildMeasuredPastItsOfferReadsItsLineWhereTheCenteredChildPutsIt() =
        runComposeSwingTest {
            val baselines =
                linesOf(offer = Constraints.fixed(CHILD_WIDTH, CHILD_HEIGHT)) {
                    Layout(
                        measurePolicy = { _, _ ->
                            layout(CHILD_WIDTH, CHILD_HEIGHT + 20, mapOf(FirstBaseline to 13)) {}
                        },
                    )
                }

            assertEquals(listOf(13 - 10), baselines, "the child stands 10 above the offer it overflows by 20")
        }

    @Test
    fun aRequiredSizePastTheOfferReadsTheLineWhereTheCenteredContentPutsIt() =
        runComposeSwingTest {
            val baselines =
                linesOf(offer = Constraints.fixed(CHILD_WIDTH, CHILD_HEIGHT)) {
                    Label("label", modifier = SwingModifier.requiredSize(CHILD_WIDTH, CHILD_HEIGHT + 20))
                }

            val label = children().single()
            assertEquals(CHILD_HEIGHT + 20, label.height)
            assertEquals(
                listOf(label.getBaseline(label.width, label.height) - 10),
                baselines,
                "the content stands 10 above the offer it overflows by 20",
            )
        }

    /**
     * What a policy reads as [line] of each child [content] declares, measured under [offer]. It reads them in its
     * placement block, which only a layout pass runs.
     */
    private fun ComposeSwingTest.linesOf(
        line: AlignmentLine = FirstBaseline,
        offer: Constraints = Constraints(maxWidth = 200),
        content: @Composable ConstrainedScope.() -> Unit,
    ): List<Int> {
        val lines = mutableListOf<Int>()
        setContent {
            Layout(
                content = content,
                modifier = containerModifier(200, 200),
                measurePolicy = { measurables, constraints ->
                    val placeables = measurables.map { it.measure(offer) }
                    layout(constraints.minWidth, constraints.minHeight) {
                        lines.clear()
                        placeables.forEach {
                            lines += it[line]
                            it.place(0, 0)
                        }
                    }
                },
            )
        }
        return lines
    }

    private fun ComposeSwingTest.children(): List<JComponent> =
        onNodeWithTag(CONTAINER_TAG).fetch<JComponent>().childrenInDeclarationOrder().map { it as JComponent }

    /** Wraps its content in a box of the content's size, and names [FirstBaseline] at [MODIFIER_LINE] in it. */
    context(scope: ConstrainedScope)
    private fun SwingModifier.lineProviding(): SwingModifier =
        layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height, mapOf(FirstBaseline to MODIFIER_LINE)) {
                placeable.place(0, 0)
            }
        }

    /** What [setLinePlacedContent] composes from, and the runs of each placement block it counts. */
    private class LinePlacedContainer {
        var y by mutableIntStateOf(0)
        var byLine by mutableStateOf(true)
        var offerShrink by mutableIntStateOf(0)
        var innerRuns = 0
        var outerRuns = 0
    }

    /**
     * Composes a container placing its child at [LinePlacedContainer.y], inside a parent that places it by its
     * [FirstBaseline] while [LinePlacedContainer.byLine]. The parent offers the container
     * [LinePlacedContainer.offerShrink] less height than it has, which measures the container again at its own size.
     */
    private fun ComposeSwingTest.setLinePlacedContent(): LinePlacedContainer {
        val content = LinePlacedContainer()
        setContent {
            Layout(
                content = {
                    Layout(
                        content = {
                            Layout(measurePolicy = { _, _ -> layout(20, 20, mapOf(FirstBaseline to 10)) {} })
                        },
                        measurePolicy = { measurables, constraints ->
                            val placeable = measurables.single().measure(constraints)
                            layout(placeable.width, placeable.height + 50) {
                                content.innerRuns++
                                placeable.place(0, content.y)
                            }
                        },
                    )
                },
                modifier = containerModifier(200, 200),
                measurePolicy = { measurables, constraints ->
                    val offer = Constraints(maxWidth = 200, maxHeight = 200 - content.offerShrink)
                    val box = measurables.single().measure(offer)
                    layout(constraints.minWidth, constraints.minHeight) {
                        content.outerRuns++
                        box.place(0, if (content.byLine) 100 - box[FirstBaseline] else 0)
                    }
                },
            )
        }
        return content
    }

    /** A component of its own that answers constrained questions and names [FirstBaseline] at [OWN_LINE]. */
    private class LineProvidingComponent :
        JComponent(),
        Constrainable {
        override fun measure(constraints: Constraints) = Unit

        override val constrainedWidth: Int get() = CHILD_WIDTH

        override val constrainedHeight: Int get() = CHILD_HEIGHT

        override val alignmentLines: Map<AlignmentLine, Int> get() = mapOf(FirstBaseline to OWN_LINE)

        override fun minIntrinsicWidth(height: Int): Int = CHILD_WIDTH

        override fun maxIntrinsicWidth(height: Int): Int = CHILD_WIDTH

        override fun minIntrinsicHeight(width: Int): Int = CHILD_HEIGHT

        override fun maxIntrinsicHeight(width: Int): Int = CHILD_HEIGHT
    }

    private companion object {
        /** Where [LineProvidingComponent] puts its line, from its own top. */
        const val OWN_LINE = 9

        /** Where a layout modifier puts its line, from the top of its own box. */
        const val MODIFIER_LINE = 21

        const val LAYERED_TAG = "layered"
    }
}
