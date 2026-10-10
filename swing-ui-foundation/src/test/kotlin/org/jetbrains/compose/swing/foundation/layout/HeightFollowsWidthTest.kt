package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertAskedForNoLayout
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.withRecordedRepaints
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Content whose height follows its width. A stock component inside a Foundation container, such as a wrapping text
 * area, takes the height it reports for the width it is granted in the validation that grants it, and the paint that
 * follows finds nothing to lay out again. A Foundation container under a stock parent that sets its width before asking
 * its height takes the height for that width in the same validation; under a parent that stretches it to another width
 * than it asked for, it takes that height one validation later, as Swing's own wrapping components do.
 */
class HeightFollowsWidthTest {
    @Test
    fun aContainerUnderAStockParentTakesTheHeightForTheWidthItIsGrantedInTheFirstValidation() {
        val slots = swingParentSlots(emptyList())
        for (name in listOf("BorderLayout NORTH", "BorderLayout SOUTH")) {
            runComposeSwingTest {
                setContent {
                    slots.getValue(name)(WIDTH) { modifier ->
                        Box(modifier = modifier.testTag("box")) {
                            Label(
                                "Preview",
                                modifier = SwingModifier.testTag("label").fillMaxWidth().aspectRatio(16f / 9f),
                            )
                        }
                    }
                }
                val box = onNodeWithTag("box").fetch<JComponent>()
                val label = onNodeWithTag("label").fetch<JComponent>()
                val atRatio = Dimension(WIDTH, (WIDTH * 9f / 16f).roundToInt())
                assertEquals(atRatio, box.size, "$name: the first validation grants the ratio's height at the width")
                assertEquals(Rectangle(Point(), atRatio), label.bounds, "$name: and the label fills it")
                assertEquals(atRatio.height, box.minimumSize.height, "$name: the least height is at that width too")
                withRecordedRepaints { recorder ->
                    settleWithPaint()
                    recorder.assertAskedForNoLayout(box, "$name: the paint")
                }
                assertEquals(atRatio, box.size, "$name: the paint lays nothing out again")
            }
        }
    }

    @Test
    fun aViewTrackingTheViewportWidthSettlesWhereItsScrollBarChangesItsWidth() {
        for ((changing, view) in ChangingViews) {
            for (case in ScrollBarCase.entries) {
                runComposeSwingTest {
                    val reference = JScrollPane()
                    val border = reference.insets
                    val wide = WIDTH - border.left - border.right
                    val narrow = wide - reference.verticalScrollBar.preferredSize.width
                    val change = mutableIntStateOf(0)
                    setContent {
                        val extent = case.extent(tallAtWide = wide.atRatio, tallAtNarrow = narrow.atRatio)
                        ScrollPane(modifier = SwingModifier.preferredSize(WIDTH, extent + border.top + border.bottom)) {
                            Viewport { view(change) }
                        }
                    }
                    val pane = onNodeOfType<JScrollPane>().fetch()
                    val settled = onNodeWithTag("view").fetch<JComponent>()
                    settleWithPaint()
                    val name = "$case, $changing"
                    val bounds = settled.bounds
                    val scrolls = case != ScrollBarCase.AtNeither
                    assertEquals(scrolls, pane.verticalScrollBar.isVisible, "$name: the view settles on the scroll bar")
                    assertEquals(if (scrolls) narrow else wide, bounds.width, "$name: at the width it leaves")
                    val label = onNodeWithTag("label").fetch<JComponent>()
                    if (case == ScrollBarCase.AtTheWideWidthOnly) {
                        assertEquals(wide.atRatio, bounds.height, "$name: keeping the height for the wide width")
                    }
                    if (case == ScrollBarCase.AtNeither) assertEquals(wide.atRatio, label.height, "$name: at the ratio")

                    repeat(4) {
                        change.intValue++
                        awaitIdle()
                        settleWithPaint()
                        assertEquals(bounds, settled.bounds, "$name: the view keeps its bounds after change $it")
                        assertEquals(scrolls, pane.verticalScrollBar.isVisible, "$name: and the bar after change $it")
                    }
                }
            }
        }
    }

    @Test
    fun aContainerAStockParentStopsStretchingTakesTheHeightForItsPreferredWidthAfterOnePaint() {
        for (askedBeforeThePaint in listOf(false, true)) {
            runComposeSwingTest {
                val layout = FillingOrPreferredLayout()
                setContent {
                    SwingNode(factory = { JPanel(layout) }, modifier = SwingModifier.preferredSize(WIDTH, HEIGHT)) {
                        Box(modifier = SwingModifier.testTag("box")) {
                            Label(
                                "Preview",
                                modifier = SwingModifier.testTag("label").fillMaxWidth().aspectRatio(16f / 9f),
                            )
                        }
                    }
                }
                val box = onNodeWithTag("box").fetch<JComponent>()
                val label = onNodeWithTag("label").fetch<JComponent>()
                settleWithPaint()
                assertEquals(Dimension(WIDTH, (WIDTH * 9f / 16f).roundToInt()), box.size, "the filled width's height")

                layout.fills = false
                (box.parent as JComponent).revalidate()
                awaitIdle()
                if (askedBeforeThePaint) box.preferredSize
                settleWithPaint()

                val name = if (askedBeforeThePaint) "asked before the paint" else "asked by the parent only"
                val preferredWidth = label.preferredSize.width
                assertEquals(
                    Dimension(preferredWidth, (preferredWidth * 9f / 16f).roundToInt()),
                    box.size,
                    "$name: the validation after the paint grants the label's preferred width and the ratio's " +
                        "height at it",
                )
                assertEquals(box.preferredSize, box.size, "$name: which is the size the box prefers")
            }
        }
    }

    @Test
    fun aContainerAStockParentLaysBackAtAnEarlierWidthAnswersItsHeightThereOnceAPaintFoundItSettled() =
        runComposeSwingTest {
            var width by mutableIntStateOf(WIDTH)
            setContent {
                SwingNode(
                    factory = { JPanel(FillingOrPreferredLayout()) },
                    modifier = SwingModifier.preferredSize(width, HEIGHT),
                ) {
                    Box(modifier = SwingModifier.testTag("box")) {
                        Label("Preview", modifier = SwingModifier.fillMaxWidth().aspectRatio(16f / 9f))
                    }
                }
            }
            val box = onNodeWithTag("box").fetch<JComponent>()
            settleWithPaint()
            width = WIDTH / 2
            awaitIdle()
            settleWithPaint()
            settleWithPaint()
            assertEquals(Dimension(WIDTH / 2, WIDTH / 2 * 9 / 16), box.size, "the narrow width's height, painted")

            width = WIDTH
            awaitIdle()

            assertEquals(WIDTH, box.width, "the parent lays the box back at the full width")
            assertEquals(WIDTH * 9 / 16, box.preferredSize.height, "the box prefers the ratio's height at that width")
            assertEquals(WIDTH * 9 / 16, box.minimumSize.height, "and can shrink to it")
        }

    @Test
    fun wrappingTextInAContainerUnderAStockParentTakesItsWrappedHeightInTheFirstValidation() {
        for ((kind, wrapping) in WrappingTexts) {
            runComposeSwingTest {
                setContent {
                    Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(WIDTH, HEIGHT)) {
                        Column(modifier = SwingModifier.north().testTag("column")) {
                            wrapping(SwingModifier.testTag("text").fillMaxWidth())
                        }
                    }
                }
                val text = onNodeWithTag("text").fetch<JComponent>()
                val column = onNodeWithTag("column").fetch<JComponent>()
                assertWrapped(text, "$kind: the first validation")
                assertEquals(text.height, column.height, "$kind: the column holds it")
                withRecordedRepaints { recorder ->
                    settleWithPaint()
                    recorder.assertAskedForNoLayout(column, "$kind: the paint")
                }
                assertWrapped(text, "$kind: the paint")
            }
        }
    }

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
    fun aWrappingTextAreaNestedInAContainerUnderAStockParentTakesItsWrappedHeightInTheFirstValidation() =
        runComposeSwingTest {
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(WIDTH, HEIGHT)) {
                    Column(modifier = SwingModifier.north().testTag("column")) {
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
            val column = onNodeWithTag("column").fetch<JComponent>()
            val box = onNodeWithTag("box").fetch<JComponent>()

            val text = onNodeWithTag("text").fetch<JTextArea>()
            assertWrapped(text, "the first validation")
            assertEquals(text.size, box.size, "the box holds the area")
            assertEquals(box.size, column.size, "and the column holds the box")
            withRecordedRepaints { recorder ->
                settleWithPaint()
                recorder.assertAskedForNoLayout(column, "the paint")
            }
        }

    @Test
    fun aContainerUnderAStockParentWhoseHeightDoesNotFollowItsWidthIsLaidOutOnce() =
        runComposeSwingTest {
            var measures = 0
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(WIDTH, HEIGHT)) {
                    Column(modifier = SwingModifier.north().testTag("column")) {
                        Label("First", modifier = SwingModifier.countingMeasures { measures++ })
                        Label("Second")
                    }
                }
            }
            val column = onNodeWithTag("column").fetch<JComponent>()
            val laidOut = column.bounds
            val passes = measures

            withRecordedRepaints { recorder ->
                settleWithPaint()
                assertFalse(column in recorder.relayouts, "the column asks for no validation after the paint")
            }
            assertEquals(laidOut, column.bounds, "the column keeps the bounds of its one layout pass")
            assertEquals(passes, measures, "and is measured no more")
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
    fun htmlTextTakesItsWrappedHeightInTheValidationThatGrantsItsWidth() {
        for ((kind, wrapping) in WrappingTexts - "TextArea") {
            runComposeSwingTest {
                var width by mutableIntStateOf(WIDTH + 80)
                setContent {
                    Panel(PanelLayout.Grid(), modifier = SwingModifier.preferredSize(2 * width, HEIGHT)) {
                        Panel(PanelLayout.Border()) {
                            wrapping(SwingModifier.testTag("reference").north())
                        }
                        Panel(PanelLayout.Border()) {
                            Column(modifier = SwingModifier.center().testTag("column")) {
                                wrapping(SwingModifier.testTag("text").fillMaxWidth())
                            }
                        }
                    }
                }
                assertWrapped(onNodeWithTag("text").fetch<JComponent>(), "$kind: the first validation")
                settleWithPaint()
                val column = onNodeWithTag("column").fetch<JComponent>()
                withRecordedRepaints { recorder ->
                    width = WIDTH
                    awaitIdle()
                    assertFalse(column in recorder.relayouts, "$kind: the column asks for no validation of its own")
                }
                val text = onNodeWithTag("text").fetch<JComponent>()
                val laidOut = text.bounds

                assertWrapped(text, "$kind: a single validation")
                settleWithPaint()
                assertEquals(laidOut, text.bounds, "$kind: the paint that follows lays nothing out again")
                // Swing's own BorderLayout lays an HTML editor pane out with no height, so it is no reference for one.
                if (kind != "HTML EditorPane") {
                    assertEquals(
                        onNodeWithTag("reference").fetch<JComponent>().size,
                        text.size,
                        "$kind: as many lines as Swing's own layout reaches once it has painted",
                    )
                }
            }
        }
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

            assertWrapped(label, "a single validation")
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
                assertEquals(text.height, containers.getValue("inner").height, "$name: the inner box holds the area")
            }
        }
    }

    @Test
    fun aComponentGrantedTheWidthItHoldsIsLeftWhereItWas() =
        runComposeSwingTest {
            var label by mutableStateOf("Before")
            val text = ResizeCountingTextArea()
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(WIDTH, HEIGHT)) {
                    Column(modifier = SwingModifier.center().testTag("column")) {
                        SwingNode(factory = { text }, modifier = SwingModifier.fillMaxWidth())
                        Label(label)
                    }
                }
            }
            val laidOut = text.bounds
            val column = onNodeWithTag("column").fetch<JComponent>()
            text.resizes = 0

            withRecordedRepaints { recorder ->
                label = "After"
                awaitIdle()
                assertFalse(column in recorder.relayouts, "the column asks for no validation of its own")
            }

            assertEquals(laidOut, text.bounds, "a sibling's change leaves the area where it was")
            assertEquals(0, text.resizes, "and the area, granted the width it holds, is not resized")
        }

    @Test
    fun aComponentGrantedANewWidthIsResizedOnceAndAskedWhatItPrefersThreeTimesInTheValidation() =
        runComposeSwingTest {
            var width by mutableIntStateOf(WIDTH)
            val panel = ResizeCountingPanel()
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(width, HEIGHT)) {
                    Column(modifier = SwingModifier.north()) {
                        SwingNode(factory = { panel }, modifier = SwingModifier.fillMaxWidth())
                    }
                }
            }
            settleWithPaint()
            panel.queries = 0
            panel.resizes = 0

            width = WIDTH + 80
            awaitIdle()

            assertEquals(WIDTH + 80, panel.width, "the panel takes the new width")
            assertEquals(1, panel.resizes, "resized once, as a Swing parent resizes it")
            assertEquals(
                3,
                panel.queries,
                "asked what it prefers once as the column answers its width, once as it answers its height at the " +
                    "width granted, and once as the column is laid out",
            )
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

    /** Where the view's height, which follows its width, needs the viewport's vertical scroll bar. */
    private enum class ScrollBarCase {
        AtBothWidths,
        AtTheWideWidthOnly,
        AtNeither,
        ;

        /** The viewport height for a view [tallAtWide] at the viewport's width and [tallAtNarrow] beside the bar. */
        fun extent(
            tallAtWide: Int,
            tallAtNarrow: Int,
        ): Int =
            when (this) {
                AtBothWidths -> tallAtNarrow - 10
                AtTheWideWidthOnly -> (tallAtNarrow + tallAtWide) / 2
                AtNeither -> tallAtWide + 10
            }
    }

    private companion object {
        const val WIDTH = 320
        const val HEIGHT = 600

        /** The height a 16:9 view takes at this width. */
        val Int.atRatio: Int get() = (this * 9f / 16f).roundToInt()

        /**
         * Views tracking the viewport's width, each holding a label tagged "label" whose height follows that width, and
         * laid out again whenever the state they are handed changes, with no change to their size.
         */
        val ChangingViews: Map<String, @Composable (MutableIntState) -> Unit> =
            mapOf(
                "a sibling's text changing" to { change ->
                    Box(modifier = SwingModifier.testTag("view")) {
                        Label("Preview", modifier = SwingModifier.testTag("label").fillMaxWidth().aspectRatio(16f / 9f))
                        Label(if (change.intValue % 2 == 0) "Even" else "Odd")
                    }
                },
                "its placement changing" to { change ->
                    Layout(
                        content = {
                            Label(
                                "Preview",
                                modifier = SwingModifier.testTag("label").fillMaxWidth().aspectRatio(16f / 9f),
                            )
                        },
                        modifier = SwingModifier.testTag("view"),
                        measurePolicy = { measurables, constraints ->
                            val placeable = measurables.single().measure(Constraints(maxWidth = constraints.maxWidth))
                            layout(placeable.width, placeable.height) { placeable.place(change.intValue % 2, 0) }
                        },
                    )
                },
            )

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
    }
}

/** Paints the tree and lets the validation any paint asks for run, as a window's next frame would. */
internal suspend fun ComposeSwingTest.settleWithPaint() {
    captureToImage()
    awaitIdle()
}

/** Lays its one child out at the height it prefers, filling the container's width while [fills] holds. */
private class FillingOrPreferredLayout : LayoutManager {
    var fills = true

    override fun layoutContainer(parent: Container) {
        val child = parent.getComponent(0)
        val preferred = child.preferredSize
        child.setBounds(0, 0, if (fills) parent.width else preferred.width, preferred.height)
    }

    override fun preferredLayoutSize(parent: Container): Dimension = parent.getComponent(0).preferredSize

    override fun minimumLayoutSize(parent: Container): Dimension = parent.getComponent(0).minimumSize

    override fun addLayoutComponent(
        name: String?,
        component: Component,
    ) = Unit

    override fun removeLayoutComponent(component: Component) = Unit
}

/** A panel holding a label, counting how often it is asked what it prefers and how often it is resized. */
private class ResizeCountingPanel : JPanel(BorderLayout()) {
    var queries = 0
    var resizes = 0

    init {
        add(JLabel("Counted"))
        addComponentListener(
            object : ComponentAdapter() {
                override fun componentResized(event: ComponentEvent) {
                    resizes++
                }
            },
        )
    }

    override fun getPreferredSize(): Dimension {
        queries++
        return super.getPreferredSize()
    }
}
