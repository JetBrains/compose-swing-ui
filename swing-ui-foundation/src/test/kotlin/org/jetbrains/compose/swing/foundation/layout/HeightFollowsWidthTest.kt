package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.withRecordedRepaints
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A stock component whose height follows its width, such as a wrapping text area, inside a Foundation container: it
 * takes the height it reports for the width it is granted in the validation that grants it, and the paint that
 * follows finds nothing to lay out again.
 */
class HeightFollowsWidthTest {
    @Test
    fun aWrappingTextAreaInAContainerAFlowLayoutHoldsTakesItsWrappedHeightInTheFirstValidation() =
        runComposeSwingTest {
            setContent {
                Panel(PanelLayout.Flow()) {
                    Column(modifier = SwingModifier.testTag("column")) {
                        TextArea(
                            WRAPPING_TEXT,
                            {},
                            SwingModifier.testTag("text").fillMaxWidth(),
                            lineWrap = true,
                            wrapStyleWord = true,
                        )
                    }
                }
            }
            val text = onNodeWithTag("text").fetch<JTextArea>()
            val column = onNodeWithTag("column").fetch<JComponent>()

            assertWrapped(text, "the first validation")
            assertEquals(text.size, column.size, "the column holds the area at the size it asked for")
            settleWithPaint()
            assertWrapped(text, "the paint that follows")
        }

    @Test
    fun aWrappingTextAreaTakesItsWrappedHeightInTheValidationThatGrantsItsWidth() {
        val containers: Map<String, @Composable (SwingModifier, @Composable ConstrainedScope.() -> Unit) -> Unit> =
            mapOf(
                "Column" to { modifier, content -> Column(modifier = modifier) { content() } },
                "Box" to { modifier, content -> Box(modifier = modifier) { content() } },
                "Layout" to { modifier, content -> Layout(StackPolicy, modifier = modifier) { content() } },
            )
        for ((name, container) in containers) {
            runComposeSwingTest {
                var measures = 0
                var width by mutableIntStateOf(WIDTH + 80)
                setContent {
                    // Side by side, each as wide, so the reference's own layout leaves the container alone.
                    Panel(PanelLayout.Grid(), modifier = SwingModifier.preferredSize(2 * width, HEIGHT)) {
                        Panel(PanelLayout.Border()) {
                            TextArea(
                                WRAPPING_TEXT,
                                {},
                                SwingModifier.testTag("reference").north(),
                                lineWrap = true,
                                wrapStyleWord = true,
                            )
                        }
                        Panel(PanelLayout.Border()) {
                            container(SwingModifier.center().testTag("container")) {
                                TextArea(
                                    WRAPPING_TEXT,
                                    {},
                                    SwingModifier.testTag("text").fillMaxWidth().countingMeasures { measures++ },
                                    lineWrap = true,
                                    wrapStyleWord = true,
                                )
                            }
                        }
                    }
                }
                assertWrapped(onNodeWithTag("text").fetch<JTextArea>(), "$name: the first validation")
                settleWithPaint()
                val container = onNodeWithTag("container").fetch<JComponent>()
                withRecordedRepaints { recorder ->
                    width = WIDTH
                    awaitIdle()
                    assertFalse(container in recorder.relayouts, "$name: the container asks for no validation")
                }
                val text = onNodeWithTag("text").fetch<JTextArea>()
                val laidOut = text.bounds

                assertWrapped(text, "$name: a single validation")
                val passes = measures
                settleWithPaint()
                assertEquals(laidOut, text.bounds, "$name: the paint that follows lays nothing out again")
                assertEquals(passes, measures, "$name: and measures nothing again")
                assertEquals(
                    onNodeWithTag("reference").fetch<JComponent>().height,
                    text.height,
                    "$name: as many lines as Swing's own layout reaches once it has painted",
                )
            }
        }
    }

    @Test
    fun aWrappingTextAreaOfferedAnUnboundedWidthTakesTheHeightForTheWidthItTakesInOneValidation() =
        runComposeSwingTest {
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(WIDTH, HEIGHT)) {
                    Column(modifier = SwingModifier.center()) {
                        TextArea(
                            WRAPPING_TEXT,
                            {},
                            SwingModifier.testTag("text").wrapContentWidth(unbounded = true),
                            lineWrap = true,
                            wrapStyleWord = true,
                        )
                    }
                }
            }
            val text = onNodeWithTag("text").fetch<JTextArea>()
            val laidOut = text.bounds

            assertEquals(text.preferredSize, text.size, "a single validation lays the area out at the size it prefers")
            settleWithPaint()
            assertEquals(laidOut, text.bounds, "and the paint that follows lays nothing out again")
        }

    @Test
    fun aWrappingTextAreaSharingARowTakesItsWrappedHeightInOneValidation() =
        runComposeSwingTest {
            var width by mutableIntStateOf(WIDTH + 80)
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(width, HEIGHT)) {
                    Column(modifier = SwingModifier.center()) {
                        Row(modifier = SwingModifier.testTag("row")) {
                            TextArea(
                                WRAPPING_TEXT,
                                {},
                                SwingModifier.testTag("text").weight(1f),
                                lineWrap = true,
                                wrapStyleWord = true,
                            )
                            Label("Side", modifier = SwingModifier.testTag("side"))
                        }
                        Label("After", modifier = SwingModifier.testTag("after"))
                    }
                }
            }
            assertWrapped(onNodeWithTag("text").fetch<JTextArea>(), "the first validation")
            settleWithPaint()
            val row = onNodeWithTag("row").fetch<JComponent>()
            withRecordedRepaints { recorder ->
                width = WIDTH
                awaitIdle()
                assertFalse(row in recorder.relayouts, "the row asks for no validation of its own")
            }
            val text = onNodeWithTag("text").fetch<JTextArea>()
            val side = onNodeWithTag("side").fetch<JComponent>()

            assertEquals(WIDTH - side.width, text.width, "the weighted area takes what the label leaves")
            assertWrapped(text, "a single validation")
            assertEquals(text.height, onNodeWithTag("row").fetch<JComponent>().height, "the row holds the area")
            assertEquals(
                text.height,
                onNodeWithTag("after").fetch<JComponent>().y,
                "the column places what follows below the wrapped area",
            )
        }

    @Test
    fun aWrappingTextAreaInAValidContainerItsParentNarrowsTakesItsWrappedHeightInOneValidation() =
        runComposeSwingTest {
            var width by mutableIntStateOf(WIDTH)
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(width, HEIGHT)) {
                    Column(modifier = SwingModifier.center().testTag("column")) {
                        Box(modifier = SwingModifier.testTag("box")) {
                            TextArea(
                                WRAPPING_TEXT,
                                {},
                                SwingModifier.testTag("text").fillMaxWidth(),
                                lineWrap = true,
                                wrapStyleWord = true,
                            )
                        }
                    }
                }
            }
            val text = onNodeWithTag("text").fetch<JTextArea>()
            val wide = text.height

            val column = onNodeWithTag("column").fetch<JComponent>()
            val box = onNodeWithTag("box").fetch<JComponent>()
            withRecordedRepaints { recorder ->
                width = WIDTH / 2
                awaitIdle()
                assertFalse(
                    column in recorder.relayouts || box in recorder.relayouts,
                    "the box, resizing the area as the column lays it out, asks for no layout pass of its own",
                )
            }

            assertEquals(WIDTH / 2, text.width, "the area takes the narrower width")
            assertWrapped(text, "the validation that narrows its container")
            assertTrue(text.height > wide, "into more lines than it took at the wider width")
            assertEquals(text.height, onNodeWithTag("box").fetch<JComponent>().height, "the box holds the area")
        }

    @Test
    fun anHtmlLabelTakesItsWrappedHeightInTheValidationThatGrantsItsWidth() =
        runComposeSwingTest {
            var width by mutableIntStateOf(WIDTH + 80)
            setContent {
                Panel(PanelLayout.Grid(), modifier = SwingModifier.preferredSize(2 * width, HEIGHT)) {
                    Panel(PanelLayout.Border()) {
                        Label("<html>$WRAPPING_TEXT</html>", modifier = SwingModifier.testTag("reference").north())
                    }
                    Panel(PanelLayout.Border()) {
                        Column(modifier = SwingModifier.center().testTag("column")) {
                            Label(
                                "<html>$WRAPPING_TEXT</html>",
                                modifier = SwingModifier.testTag("label").fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            assertHtmlWrapped(onNodeWithTag("label").fetch<JComponent>(), "the first validation")
            settleWithPaint()
            val column = onNodeWithTag("column").fetch<JComponent>()
            withRecordedRepaints { recorder ->
                width = WIDTH
                awaitIdle()
                assertFalse(column in recorder.relayouts, "the column asks for no validation of its own")
            }
            val label = onNodeWithTag("label").fetch<JComponent>()
            val laidOut = label.bounds

            assertHtmlWrapped(label, "a single validation")
            settleWithPaint()
            assertEquals(laidOut, label.bounds, "the paint that follows lays nothing out again")
            assertEquals(
                onNodeWithTag("reference").fetch<JComponent>().size,
                label.size,
                "as many lines as Swing's own layout reaches once it has painted",
            )
        }

    @Test
    fun anHtmlLabelAlignedByItsBaselineTakesItsWrappedHeightInOneValidation() =
        runComposeSwingTest {
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(WIDTH, HEIGHT)) {
                    Column(modifier = SwingModifier.center()) {
                        Row(modifier = SwingModifier.testTag("row")) {
                            Label(
                                "<html>$WRAPPING_TEXT</html>",
                                modifier = SwingModifier.testTag("label").weight(1f).alignByBaseline(),
                            )
                            Label("Side", modifier = SwingModifier.alignByBaseline())
                        }
                    }
                }
            }
            val label = onNodeWithTag("label").fetch<JComponent>()
            val laidOut = label.bounds

            assertHtmlWrapped(label, "a single validation")
            settleWithPaint()
            settleWithPaint()
            assertEquals(laidOut, label.bounds, "the paints that follow lay nothing out again")
        }

    @Test
    fun aWrappingTextAreaInValidContainersNestedInOneItsParentNarrowsTakesItsWrappedHeightInOneValidation() {
        for (askedItsIntrinsicHeight in listOf(false, true)) {
            val name = if (askedItsIntrinsicHeight) "the outer box asked its intrinsic height" else "the boxes measured"
            runComposeSwingTest {
                var width by mutableIntStateOf(WIDTH)
                setContent {
                    Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(width, HEIGHT)) {
                        Column(modifier = SwingModifier.center().testTag("column")) {
                            val outer =
                                if (askedItsIntrinsicHeight) SwingModifier.height(IntrinsicSize.Max) else SwingModifier
                            Box(modifier = outer.testTag("outer")) {
                                Box(modifier = SwingModifier.testTag("inner")) {
                                    TextArea(
                                        WRAPPING_TEXT,
                                        {},
                                        SwingModifier.testTag("text").fillMaxWidth(),
                                        lineWrap = true,
                                        wrapStyleWord = true,
                                    )
                                }
                            }
                        }
                    }
                }
                val text = onNodeWithTag("text").fetch<JTextArea>()
                val wide = text.height

                val containers =
                    listOf("column", "outer", "inner").associateWith { onNodeWithTag(it).fetch<JComponent>() }
                withRecordedRepaints { recorder ->
                    width = WIDTH / 2
                    awaitIdle()
                    assertEquals(
                        emptyList(),
                        containers.filterValues { it in recorder.relayouts }.keys.toList(),
                        "$name: the boxes, resizing the area as the column lays them out, ask for no layout pass",
                    )
                }

                assertEquals(WIDTH / 2, text.width, "$name: the area takes the narrower width")
                assertWrapped(text, "$name: the validation that narrows its containers")
                assertTrue(text.height > wide, "$name: into more lines than it took at the wider width")
            }
        }
    }

    @Test
    fun aComponentGrantedTheWidthItHoldsIsAskedWhatItPrefersOnlyOnce() =
        runComposeSwingTest {
            var label by mutableStateOf("Before")
            val text = CountingTextArea(WRAPPING_TEXT)
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(WIDTH, HEIGHT)) {
                    Column(modifier = SwingModifier.center()) {
                        SwingNode(factory = { text }, modifier = SwingModifier.fillMaxWidth())
                        Label(label)
                    }
                }
            }
            val laidOut = text.bounds
            text.queries = 0

            label = "After"
            awaitIdle()

            assertEquals(laidOut, text.bounds, "a sibling's change leaves the area where it was")
            assertEquals(1, text.queries, "and the area, granted the width it holds, is asked once")
        }

    @Test
    fun thePaintAfterAWidthChangeAsksNoPreferredSizeOfContentWhoseHeightDoesNotFollowItsWidth() =
        runComposeSwingTest {
            var width by mutableIntStateOf(WIDTH)
            var widthQuestions = 0
            val panel = ResizeCountingPanel()
            val nested =
                object : MeasurePolicy {
                    override fun MeasureScope.measure(
                        measurables: List<Measurable>,
                        constraints: Constraints,
                    ): MeasureResult {
                        val placeable = measurables.single().measure(constraints)
                        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }

                    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
                        measurables: List<IntrinsicMeasurable>,
                        height: Int,
                    ): Int {
                        widthQuestions++
                        return measurables.single().maxIntrinsicWidth(height)
                    }

                    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
                        measurables: List<IntrinsicMeasurable>,
                        width: Int,
                    ): Int = measurables.single().maxIntrinsicHeight(width)
                }
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(width, HEIGHT)) {
                    Column(modifier = SwingModifier.north()) {
                        Layout(
                            content = { Label("Nested", modifier = SwingModifier.fillMaxWidth()) },
                            measurePolicy = nested,
                        )
                        SwingNode(factory = { panel }, modifier = SwingModifier.fillMaxWidth())
                    }
                }
            }
            settleWithPaint()
            width = WIDTH + 80
            awaitIdle()
            widthQuestions = 0
            panel.queries = 0

            captureToImage()

            assertEquals(0, widthQuestions, "the paint asks no nested container the preferred size it no longer holds")
            assertEquals(0, panel.queries, "nor a component the column holds, whose answer it holds")
        }

    private companion object {
        const val WIDTH = 320
        const val HEIGHT = 600

        /** Stacks its children, each at the whole width it is offered and the height it then answers. */
        val StackPolicy =
            MeasurePolicy { measurables, constraints ->
                val offer = Constraints(constraints.maxWidth, constraints.maxWidth, 0, constraints.maxHeight)
                val placeables = measurables.map { it.measure(offer) }
                layout(constraints.maxWidth, placeables.sumOf { it.height }) {
                    var y = 0
                    placeables.forEach {
                        it.place(0, y)
                        y += it.height
                    }
                }
            }

        /** Asserts [label] holds the height it prefers at the width it holds, and that this is several lines. */
        fun assertHtmlWrapped(
            label: JComponent,
            pass: String,
        ) {
            val line = label.getFontMetrics(label.font).height
            assertTrue(label.height > line, "$pass wraps the label into several lines, but it is ${label.height} tall")
            assertEquals(
                label.preferredSize.height,
                label.height,
                "$pass lays the label out at the height it prefers at its width",
            )
        }
    }
}

/** Text a wrapping text area breaks into several lines at a width of a few hundred pixels. */
internal const val WRAPPING_TEXT =
    "Wrapping text that is much longer than one line at three hundred and twenty pixels, so it has to break " +
        "into several lines to fit the width the parent gives it, and its height depends on that width."

/** Asserts [text] holds the height it prefers at the width it holds, and that this is several lines. */
internal fun assertWrapped(
    text: JComponent,
    pass: String,
) {
    val inner = text.height - text.insets.top - text.insets.bottom
    assertTrue(
        inner > text.getFontMetrics(text.font).height,
        "$pass wraps the text into several lines, but it is ${text.height} tall",
    )
    assertEquals(
        text.preferredSize.height,
        text.height,
        "$pass lays the text out at the height it prefers at its width",
    )
}

/** Paints the tree and lets the validation any paint asks for run, as a window's next frame would. */
internal suspend fun ComposeSwingTest.settleWithPaint() {
    captureToImage()
    awaitIdle()
}

/** A panel holding a label, counting how often it is asked what it prefers. */
private class ResizeCountingPanel : JPanel(BorderLayout()) {
    var queries = 0

    init {
        add(JLabel("Counted"))
    }

    override fun getPreferredSize(): Dimension {
        queries++
        return super.getPreferredSize()
    }
}

/** A wrapping text area counting how often it is asked what it prefers. */
private class CountingTextArea(
    text: String,
) : JTextArea(text) {
    var queries = 0

    init {
        lineWrap = true
        wrapStyleWord = true
    }

    override fun getPreferredSize(): Dimension {
        queries++
        return super.getPreferredSize()
    }
}
