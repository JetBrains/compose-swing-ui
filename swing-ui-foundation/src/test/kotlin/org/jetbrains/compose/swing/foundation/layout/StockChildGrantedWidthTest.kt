package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.interaction.assertIsOfType
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.withRecordedRepaints
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A stock child whose height follows its width, in a container whose parent measures it. */
class StockChildGrantedWidthTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("unplacedChildren")
    fun readingTheBaselineOfAnUnplacedContainerKeepsItsStockLeafBounds(
        name: String,
        child: Pair<() -> JTextArea, ConstrainedScope.() -> SwingModifier>,
    ) {
        val (create, childModifier) = child
        runComposeSwingTest(rootSize = Dimension(320, 600)) {
            root.layout = BorderLayout()
            val area = create()
            var placed by mutableStateOf(true)
            var offeredWidth by mutableIntStateOf(160)
            var readBaseline by mutableStateOf(false)
            var baselineReads = 0
            setContent {
                Layout(
                    modifier = SwingModifier.testTag("parent"),
                    measurePolicy = { measurables, constraints ->
                        val shouldPlace = placed
                        val placeable = measurables.single().measure(Constraints(offeredWidth, offeredWidth, 100, 100))
                        if (readBaseline) {
                            placeable[FirstBaseline]
                            baselineReads++
                        }
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            if (shouldPlace) placeable.place(0, 0)
                        }
                    },
                ) {
                    Box(modifier = SwingModifier.testTag("box")) {
                        SwingNode(factory = { area }, modifier = childModifier())
                    }
                }
            }
            captureToImage()
            awaitIdle()
            placed = false
            awaitIdle()
            val before = area.bounds
            val box = onNodeWithTag("box").fetch<JComponent>()
            offeredWidth = 240
            readBaseline = true
            awaitIdle()
            assertSame(
                box,
                onNodeWithTag("box").fetch<JComponent>(),
                "the component is updated in place, not rebuilt",
            )
            assertTrue(baselineReads > 0, "$name: the parent read the unplaced container's baseline")
            assertEquals(before, area.bounds, "$name: reading the baseline does not resize the stock leaf")
            onNodeWithTag("box").assertProperty(
                Dimension(0, 0),
                "$name: the nested container remains unplaced",
            ) { size }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unplacedChildren")
    fun readingTheAlignmentLinesOfAnUnplacedContainerKeepsItsStockLeafBounds(
        name: String,
        child: Pair<() -> JTextArea, ConstrainedScope.() -> SwingModifier>,
    ) {
        val (create, childModifier) = child
        runComposeSwingTest(rootSize = Dimension(320, 600)) {
            root.layout = BorderLayout()
            val area = create()
            var placed by mutableStateOf(true)
            var offeredWidth by mutableIntStateOf(160)
            setContent {
                Layout(
                    modifier = SwingModifier.testTag("parent"),
                    measurePolicy = { measurables, constraints ->
                        val placeable = measurables.single().measure(Constraints(offeredWidth, offeredWidth, 100, 100))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            if (placed) placeable.place(0, 0)
                        }
                    },
                ) {
                    Box(modifier = SwingModifier.testTag("box")) {
                        SwingNode(factory = { area }, modifier = childModifier())
                    }
                }
            }
            captureToImage()
            awaitIdle()
            placed = false
            awaitIdle()
            val before = area.bounds
            offeredWidth = 240
            awaitIdle()
            onNodeWithTag("box").assertIsOfType<ConstrainedPanel>().assertProperty(
                true,
                "$name: the unplaced container provides its stock child's baseline",
            ) { FirstBaseline in alignmentLines }
            assertEquals(before, area.bounds, "$name: reading alignment lines does not resize the stock leaf")
            onNodeWithTag("box").assertProperty(Dimension(0, 0), "$name: the nested container remains unplaced") {
                size
            }
        }
    }

    @Test
    fun aPlacementBlockMayMeasureAndReadTheBaselineBeforePlacingAStockChildAtANewWidth() =
        runComposeSwingTest {
            var width by mutableIntStateOf(WIDTH)
            val area = ResizeCountingTextArea()
            var baseline = -1
            setContent {
                borderLayoutSlot(BorderLayout.CENTER)(width) { modifier ->
                    Layout(
                        content = { SwingNode(factory = { area }, modifier = SwingModifier.fillMaxWidth()) },
                        modifier = modifier.testTag("parent"),
                        measurePolicy = { measurables, constraints ->
                            layout(constraints.maxWidth, constraints.maxHeight) {
                                val child =
                                    measurables.single().measure(
                                        Constraints(
                                            constraints.maxWidth,
                                            constraints.maxWidth,
                                            0,
                                            constraints.maxHeight,
                                        ),
                                    )
                                baseline = child[FirstBaseline]
                                child.place(0, 0)
                            }
                        },
                    )
                }
            }
            settleWithPaint()
            width = WIDTH / 2
            awaitIdle()
            val parent = onNodeWithTag("parent").fetch<JComponent>()
            assertEquals(parent.width, area.width, "the placement-time measurement grants the stock child width")
            assertTrue(baseline >= 0, "the placement block reads the stock child's baseline before placing it")
            assertWrapped(area, "the placement-time baseline read lays out wrapping text at its new width")
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unplacedChildrenForMeasurement")
    fun aContainerMeasuredByAParentThatDoesNotPlaceItLeavesItsStockChildWhereItWas(
        name: String,
        child: Pair<() -> JTextArea, ConstrainedScope.() -> SwingModifier>,
    ) {
        val (create, childModifier) = child
        runComposeSwingTest {
            val area = create()
            var width by mutableIntStateOf(WIDTH)
            var placed by mutableStateOf(true)
            setContent {
                borderLayoutSlot(BorderLayout.CENTER)(width) { modifier ->
                    Layout(
                        content = {
                            Box(modifier = SwingModifier.testTag("box")) {
                                SwingNode(factory = { area }, modifier = childModifier())
                            }
                        },
                        modifier = modifier.testTag("parent"),
                        measurePolicy =
                            MeasurePolicy { measurables, constraints ->
                                val placeables = measurables.map { it.measure(constraints) }
                                layout(constraints.maxWidth, constraints.maxHeight) {
                                    if (placed) placeables.forEach { it.place(0, 0) }
                                }
                            },
                    )
                }
            }
            settleWithPaint()
            placed = false
            awaitIdle()
            val bounds = area.bounds

            width = WIDTH - 60
            awaitIdle()

            assertEquals(bounds, area.bounds, "$name: measured at another width by a parent placing nothing")
            onNodeWithTag("box")
                .assertIsOfType<JComponent>()
                .assertProperty(true, "$name: and the measure leaves the box valid") { isValid }
            onNodeWithTag("parent")
                .assertIsOfType<JComponent>()
                .assertProperty(true, "$name: and the measure leaves the parent valid") { isValid }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("stockChildren")
    fun stockChildrenReuseAnUnchangedGrantAndApplyANewWidthOnce(
        name: String,
        foreignPanel: Boolean,
    ) {
        runComposeSwingTest {
            var sibling by mutableStateOf("Before")
            var width by mutableIntStateOf(WIDTH)
            var resizes = 0
            val child: JComponent =
                if (foreignPanel) {
                    CountingSwingPanel(JPanel().apply { repeat(500) { add(JLabel("Counted")) } })
                } else {
                    ResizeCountingTextArea()
                }
            child.addComponentListener(
                object : ComponentAdapter() {
                    override fun componentResized(event: ComponentEvent) {
                        resizes++
                    }
                },
            )
            setContent {
                borderLayoutSlot(if (foreignPanel) BorderLayout.NORTH else BorderLayout.CENTER)(width) { modifier ->
                    Column(modifier = modifier.testTag("column")) {
                        SwingNode(factory = { child }, modifier = SwingModifier.fillMaxWidth())
                        Label(sibling)
                    }
                }
            }
            settleWithPaint()
            val bounds = child.bounds
            (child as? CountingSwingPanel)?.resetCounts()
            resizes = 0
            (child as? ResizeCountingTextArea)?.resetCounts()
            withRecordedRepaints { recorder ->
                sibling = "After"
                awaitIdle()
                onNodeWithTag("column").assertProperty(false) { this in recorder.relayouts }
            }
            assertEquals(bounds, child.bounds, "$name: same grant preserves bounds")
            assertEquals(0, resizes, "same grant sends no resize event")
            assertEquals(
                0,
                (child as? ResizeCountingTextArea)?.resizes ?: (child as CountingSwingPanel).layouts,
                "same grant does no child work",
            )
            (child as? CountingSwingPanel)?.resetCounts()
            width = WIDTH + 80
            awaitIdle()
            assertEquals(width, child.width, "new grant reaches the child")
            if (foreignPanel) {
                assertEquals(1, resizes, "new grant resizes once")
                assertEquals(1, (child as CountingSwingPanel).layouts, "new grant lays the foreign panel out once")
                assertEquals(3, child.queries, "width read, height read and layout each read preferences once")
            } else {
                assertEquals(
                    1,
                    (child as ResizeCountingTextArea).widthChanges,
                    "$name: new grant gives the text area its new width once",
                )
            }
        }
    }

    companion object {
        @JvmStatic
        fun unplacedChildren(): List<Arguments> =
            unplacedBaselineChildren.map { (name, child) -> Arguments.of(name, child) }

        @JvmStatic
        fun unplacedChildrenForMeasurement(): List<Arguments> {
            val children: Map<String, Pair<() -> JTextArea, ConstrainedScope.() -> SwingModifier>> =
                mapOf(
                    "filling the width" to ({ ResizeCountingTextArea() } to { SwingModifier.fillMaxWidth() }),
                    "at a fixed height" to
                        ({ ResizeCountingTextArea() } to { SwingModifier.fillMaxWidth().height(SLOT_HEIGHT / 4) }),
                    "with paint outsets" to
                        (
                            { DecoratedTextArea(WRAPPING_TEXT) } to
                                { SwingModifier.fillMaxWidth().shadow(16, Color.BLACK) }
                        ),
                )
            return children.map { (name, child) -> Arguments.of(name, child) }
        }

        @JvmStatic
        fun stockChildren(): List<Arguments> =
            listOf(
                "a text area in BorderLayout CENTER" to false,
                "a foreign panel in BorderLayout NORTH" to true,
            ).map { (name, foreignPanel) -> Arguments.of(Named.of(name, name), foreignPanel) }
    }
}

private val unplacedBaselineChildren: Map<String, Pair<() -> JTextArea, ConstrainedScope.() -> SwingModifier>> =
    mapOf(
        "filling the width" to
            ({ ResizeCountingTextArea(WRAPPING_TEXT.repeat(12)) } to { SwingModifier.fillMaxWidth() }),
        "at a fixed height" to
            (
                { ResizeCountingTextArea(WRAPPING_TEXT.repeat(12)) } to
                    { SwingModifier.fillMaxWidth().height(SLOT_HEIGHT / 4) }
            ),
        "with paint outsets" to
            (
                {
                    DecoratedTextArea(
                        WRAPPING_TEXT.repeat(12),
                    )
                } to { SwingModifier.fillMaxWidth().shadow(16, Color.BLACK) }
            ),
    )

private const val WIDTH = 320
