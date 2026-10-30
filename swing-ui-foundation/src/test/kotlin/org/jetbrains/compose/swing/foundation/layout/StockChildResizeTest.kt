package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.graphics.Decoration
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.withRecordedRepaints
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.GridBagConstraints
import java.awt.Insets
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.border.EmptyBorder
import javax.swing.text.JTextComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A stock child whose height follows its width, sized to the width a preference read uses because a layout of its
 * container follows: it is sized with its paint outsets around that width, and its resize requests no container for a
 * layout pass.
 */
class StockChildResizeTest {
    @Test
    fun aDecoratedWrappingTextAreaTakesItsWrappedHeightInsideItsPaintOutsetsInTheValidationThatGrantsItsWidth() {
        val heightModifiers: Map<String, ConstrainedScope.() -> SwingModifier> =
            mapOf(
                "measured" to { SwingModifier },
                "read its intrinsic height" to { SwingModifier.height(IntrinsicSize.Max) },
            )
        for ((name, heightModifier) in heightModifiers) {
            runComposeSwingTest {
                var width by mutableIntStateOf(WIDTH + 80)
                setContent {
                    Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(width, 600)) {
                        Column(modifier = SwingModifier.center().testTag("column")) {
                            Box(modifier = heightModifier().fillMaxWidth()) {
                                SwingNode(
                                    factory = { DecoratedTextArea(WRAPPING_TEXT) },
                                    modifier = SwingModifier.testTag("text").fillMaxWidth().shadow(16, Color.BLACK),
                                )
                            }
                        }
                    }
                }
                settleWithPaint()
                val wide = onNodeWithTag("text").fetch<JComponent>().height
                val column = onNodeWithTag("column").fetch<JComponent>()
                withRecordedRepaints { recorder ->
                    width = WIDTH / 2
                    awaitIdle()
                    assertFalse(column in recorder.relayouts, "$name: the column requests no validation of its own")
                }
                val text = onNodeWithTag("text").fetch<DecoratedTextArea>()

                assertEquals(
                    WIDTH / 2,
                    text.decoration.localLayoutBounds(text).width,
                    "$name: the area's layout bounds fill the narrower width",
                )
                assertWrapped(text, "$name: a single validation")
                assertTrue(text.height > wide, "$name: into more lines than it took at the wider width")
            }
        }
    }

    @Test
    fun wrappingTextAValidContainerResizesForItsPreferredSizeRequestsNoLayoutPass() {
        for ((kind, wrapping) in WrappingTexts) {
            runComposeSwingTest {
                var side by mutableStateOf("Side")
                setContent {
                    Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(320, 600)) {
                        Column(modifier = SwingModifier.center()) {
                            Row(modifier = SwingModifier.testTag("row").height(IntrinsicSize.Max)) {
                                Box(modifier = SwingModifier.testTag("box")) {
                                    wrapping(SwingModifier.testTag("text").fillMaxWidth())
                                }
                                Label(side)
                            }
                        }
                    }
                }
                settleWithPaint()
                val row = onNodeWithTag("row").fetch<JComponent>()
                val box = onNodeWithTag("box").fetch<JComponent>()

                withRecordedRepaints { recorder ->
                    side = "Other"
                    awaitIdle()
                    assertFalse(
                        row in recorder.relayouts || box in recorder.relayouts,
                        "$kind: the row reads the box's preferred size, which resizes the text, and neither requests " +
                            "a layout pass",
                    )
                }
                assertWrapped(onNodeWithTag("text").fetch<JComponent>(), "$kind: the validation")
            }
        }
    }

    @Test
    fun anIntrinsicWidthReadThatResizesWrappingTextInsideAValidContainerRequestsNoLayoutPass() =
        runComposeSwingTest {
            // Reports its width with the height its one child takes at a width of the container's height, as a
            // container turning its child sideways does, and lays the child out across its own width, within its
            // height.
            val sidewaysPolicy =
                object : MeasurePolicy {
                    override fun MeasureScope.measure(
                        measurables: List<Measurable>,
                        constraints: Constraints,
                    ): MeasureResult {
                        val offer = Constraints(constraints.maxWidth, constraints.maxWidth, 0, constraints.maxHeight)
                        val placeable = measurables.single().measure(offer)
                        return layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
                    }

                    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
                        measurables: List<IntrinsicMeasurable>,
                        height: Int,
                    ): Int = measurables.single().maxIntrinsicHeight(height)
                }
            var height by mutableIntStateOf(WIDTH)
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(320, 600)) {
                    Column(modifier = SwingModifier.center()) {
                        Row(modifier = SwingModifier.testTag("row").height(height)) {
                            Layout(
                                content = {
                                    Box(modifier = SwingModifier.testTag("box")) {
                                        Label("<html>$WRAPPING_TEXT</html>", modifier = SwingModifier.fillMaxWidth())
                                    }
                                },
                                modifier = SwingModifier.testTag("sideways").fillMaxHeight().width(IntrinsicSize.Max),
                                measurePolicy = sidewaysPolicy,
                            )
                        }
                    }
                }
            }
            settleWithPaint()
            val containers =
                listOf("row", "sideways", "box").associateWith { onNodeWithTag(it).fetch<JComponent>() }

            withRecordedRepaints { recorder ->
                height = WIDTH / 2
                awaitIdle()
                assertEquals(
                    emptyList(),
                    containers.filterValues { it in recorder.relayouts }.keys.toList(),
                    "the row reads the sideways container's width at the new height, whose policy reads the box's " +
                        "height there, which resizes the text, and none requests a layout pass",
                )
            }
        }

    @Test
    fun readingANestedContainersPreferredSizeLaysNoStockChildOutAgain() =
        runComposeSwingTest {
            var width by mutableIntStateOf(WIDTH)
            val area = ResizeCountingTextArea()
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(width, 600)) {
                    Box(modifier = SwingModifier.north()) {
                        Column(modifier = SwingModifier.testTag("column")) {
                            SwingNode(
                                factory = { JPanel(BorderLayout()).apply { add(area) } },
                                modifier = SwingModifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            settleWithPaint()
            width = WIDTH - 60
            awaitIdle()
            area.resetCounts()

            // The box reads the column's preferred size, at a width wider than the one the border layout grants.
            onNodeWithTag("column").fetch<JComponent>().revalidate()
            awaitIdle()

            assertEquals(0, area.resizes, "the panel holding the area is not laid out again")
        }

    @Test
    fun aStockPanelHoldingWrappingTextReportsItsHeightAtTheWidthItsLayoutGaveItWhenReadBeforeAnyPaint() =
        runComposeSwingTest {
            var width by mutableIntStateOf(WIDTH)
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(width, 600)) {
                    Panel(PanelLayout.GridBag, modifier = SwingModifier.north()) {
                        Box(
                            modifier =
                                SwingModifier
                                    .testTag("box")
                                    .item(weightx = 1.0, fill = GridBagConstraints.HORIZONTAL),
                        ) {
                            SwingNode(
                                factory = { JPanel(BorderLayout()).apply { add(wrappingTextArea()) } },
                                modifier = SwingModifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            settleWithPaint()
            val box = onNodeWithTag("box").fetch<JComponent>()

            assertEquals(Dimension(WIDTH, wrappedHeightAt(WIDTH)), box.preferredSize, "wide: preferred")

            width = WIDTH - 60
            awaitIdle()
            box.invalidate()

            assertTrue(wrappedHeightAt(WIDTH - 60) > wrappedHeightAt(WIDTH), "the text wraps into more lines")
            assertEquals(
                Dimension(WIDTH - 60, wrappedHeightAt(WIDTH - 60)),
                box.preferredSize,
                "narrow: preferred, read after the layout that sized the panel to it",
            )
            assertEquals(box.preferredSize, box.minimumSize, "narrow: minimum")
        }

    @Test
    fun aStockContainerHoldingTextNotReflowedSinceItsLastResizeLaysTheTextOutOnceAtANarrowerWidth() =
        assertStockContainerLaysTextOutOnceAtANarrowerWidth(
            mapOf<String, Holder>(
                "Box" to { modifier, content -> Box(modifier = modifier) { content() } },
                "Column" to { modifier, content -> Column(modifier = modifier) { content() } },
                "Box holding a Column in a Box" to { modifier, content ->
                    Box(modifier = modifier) { Box { Column { content() } } }
                },
            ),
        )

    @ParameterizedTest(name = "{0}, {1}")
    @CsvSource(
        "plain label, unbounded",
        "HTML label, unbounded",
        "wrapping text area, unbounded",
        "plain label, at most 10",
        "HTML label, at most 10",
        "wrapping text area, at most 10",
        "plain label, at least 30",
        "HTML label, at least 30",
        "wrapping text area, at least 30",
    )
    fun eachChildFillingAColumnsWidthTakesEveryNewWidthInOneResizeAtTheHeightItHolds(
        kind: String,
        bound: String,
    ) = runComposeSwingTest {
        var width by mutableIntStateOf(800)
        val records = List(3) { ResizeRecord() }
        setContent {
            Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(width, 600)) {
                Column(modifier = SwingModifier.center()) {
                    for (record in records) {
                        SwingNode(
                            factory = { recordingChild(kind, "One line", record) },
                            modifier =
                                when (bound) {
                                    "at most 10" -> SwingModifier.fillMaxWidth().heightIn(max = 10)
                                    "at least 30" -> SwingModifier.fillMaxWidth().heightIn(min = 30)
                                    else -> SwingModifier.fillMaxWidth()
                                },
                        )
                    }
                }
            }
        }
        awaitIdle()
        val heights = records.map { it.child.height }

        for (newWidth in listOf(790, 780, 700)) {
            records.forEach { it.reset() }
            width = newWidth
            awaitIdle()

            assertEquals(
                heights.map { listOf(newWidth to it) },
                records.map { it.bounds.distinct() },
                "width $newWidth: every size each child was given",
            )
            assertEquals(listOf(1, 1, 1), records.map { it.resizedEvents.size }, "width $newWidth: resize events")
        }
    }

    @Test
    fun aTextAreaTakingAnotherLineAtANewWidthReachesItsListenerOnlyAtItsFinalSize() =
        runComposeSwingTest {
            var width by mutableIntStateOf(WIDTH)
            val record = ResizeRecord()
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(width, 600)) {
                    Column(modifier = SwingModifier.center()) {
                        SwingNode(
                            factory = { recordingChild("wrapping text area", WRAPPING_TEXT, record) },
                            modifier = SwingModifier.fillMaxWidth(),
                        )
                    }
                }
            }
            awaitIdle()
            val wide = record.child.height
            assertEquals(wrappedHeightAt(WIDTH), wide, "wide: the height the text takes at its width")
            record.reset()

            width = WIDTH / 2
            awaitIdle()

            val narrow = record.child.height
            assertEquals(wrappedHeightAt(WIDTH / 2), narrow, "narrow: the height the text takes at its width")
            assertTrue(narrow > wide, "the text takes more lines")
            assertEquals(
                listOf(WIDTH / 2 to wide, WIDTH / 2 to narrow),
                record.bounds.distinct(),
                "narrowing: it is read at the new width and the height it holds, then takes the height read there",
            )
            assertEquals(
                listOf(WIDTH / 2 to narrow, WIDTH / 2 to narrow),
                record.resizedEvents,
                "narrowing: each resize event finds the final size",
            )
            record.reset()

            width = WIDTH
            awaitIdle()

            assertEquals(wrappedHeightAt(WIDTH), record.child.height, "widened: the height the text takes at its width")
            assertEquals(
                listOf(WIDTH to narrow, WIDTH to wide),
                record.bounds.distinct(),
                "widening: it is read at the new width and the height it holds, then takes the height read there",
            )
            assertEquals(
                listOf(WIDTH to wide, WIDTH to wide),
                record.resizedEvents,
                "widening: each resize event finds the final size",
            )
        }

    @ParameterizedTest(name = "{0}, insets {1} each side, held at height {2}")
    @CsvSource(
        "text area, 2, 1",
        "text area, 2, 4",
        "text area, 2, 5",
        "editor pane, 3, 4",
        "editor pane, 3, 6",
        "editor pane, 3, 7",
    )
    fun aTextChildHeldAtAnyHeightTakesTheHeightItWrapsToAtANewWidth(
        kind: String,
        inset: Int,
        heldHeight: Int,
    ) = runComposeSwingTest {
        val child = borderedTextChild(kind, inset)
        var offer by mutableStateOf(Dimension(500, 600))
        setContent {
            Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(800, 800)) {
                val offered = offer
                Layout(
                    content = { SwingNode(factory = { child }) },
                    modifier = SwingModifier.center(),
                    measurePolicy =
                        MeasurePolicy { measurables, _ ->
                            val placeable =
                                measurables
                                    .single()
                                    .measure(Constraints(offered.width, offered.width, 0, offered.height))
                            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                        },
                )
            }
        }
        awaitIdle()

        offer = Dimension(500, heldHeight)
        awaitIdle()
        assertEquals(Dimension(500, heldHeight), child.size, "the child holds the offered height")

        offer = Dimension(80, 600)
        awaitIdle()

        val wrapped = borderedTextChild(kind, inset).apply { setSize(80, 600) }.preferredSize.height
        assertEquals(Dimension(80, wrapped), child.size, "the height it wraps to at the new width")
    }
}

private fun wrappedHeightAt(width: Int) = wrappingTextArea().apply { setSize(width, 1) }.preferredSize.height

private fun borderedTextChild(
    kind: String,
    inset: Int,
): JTextComponent {
    val child =
        when (kind) {
            "text area" -> wrappingTextArea()
            else -> JEditorPane("text/plain", WRAPPING_TEXT)
        }
    child.border = EmptyBorder(inset, inset, inset, inset)
    return child
}

/** What one stock child was resized to: the sizes set on it, and the size it held at each resize event. */
private class ResizeRecord {
    lateinit var child: JComponent
    val bounds = mutableListOf<Pair<Int, Int>>()
    val resizedEvents = mutableListOf<Pair<Int, Int>>()

    fun reset() {
        bounds.clear()
        resizedEvents.clear()
    }
}

private fun recordingChild(
    kind: String,
    text: String,
    record: ResizeRecord,
): JComponent {
    val child =
        when (kind) {
            "plain label" -> RecordingLabel(text, record)
            "HTML label" -> RecordingLabel("<html>$text</html>", record)
            else -> RecordingTextArea(text, record)
        }
    child.addComponentListener(
        object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                record.resizedEvents += child.width to child.height
            }
        },
    )
    record.child = child
    return child
}

private class RecordingLabel(
    text: String,
    private val record: ResizeRecord,
) : JLabel(text) {
    override fun setBounds(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        record.bounds += width to height
        super.setBounds(x, y, width, height)
    }
}

private class RecordingTextArea(
    text: String,
    private val record: ResizeRecord,
) : JTextArea(text) {
    init {
        lineWrap = true
        wrapStyleWord = true
    }

    override fun setBounds(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        record.bounds += width to height
        super.setBounds(x, y, width, height)
    }
}

/** A wrapping text area that paints through the decoration its modifier declares, its insets carrying the outsets. */
internal class DecoratedTextArea(
    text: String,
) : JTextArea(text),
    Decoratable {
    override var decoration: Decoration = Decoration.None

    init {
        lineWrap = true
        wrapStyleWord = true
    }

    override fun getGraphics(): Graphics? = decoration.getGraphics(this, super.getGraphics())

    override fun paint(g: Graphics) = decoration.paint(this, g) { super.paint(it) }

    override fun getInsets(): Insets = decoration.insets(super.getInsets())

    override fun getInsets(insets: Insets?): Insets =
        decoration.insets(super.getInsets(insets), insets ?: Insets(0, 0, 0, 0))
}

private const val WIDTH = 320
