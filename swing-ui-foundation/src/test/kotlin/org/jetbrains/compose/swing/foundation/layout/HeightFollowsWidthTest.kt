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
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.withRecordedRepaints
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JEditorPane
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
 * A stock wrapping component uses its granted bounds. A Foundation container uses the width a stock parent supplies
 * when it queries the container's preferred size. If the parent later grants a different width, the container gets
 * that width's height when the parent validates again.
 */
class HeightFollowsWidthTest {
    private fun ComposeSwingTest.assertInitialViewportState(
        case: ScrollBarCase,
        name: String,
        scrolls: Boolean,
        narrow: Int,
        wide: Int,
    ) {
        onNodeOfType<JScrollPane>().assertProperty(scrolls, "$name: scrollbar visibility") {
            verticalScrollBar.isVisible
        }
        onNodeWithTag("view").assertProperty(if (scrolls) narrow else wide, "$name: viewport width") { width }
        if (case == ScrollBarCase.AtNeither) {
            onNodeWithTag("label").assertProperty(wide.atRatio, "$name: ratio") { height }
        }
    }

    @Test
    fun stockBorderRegionsGrantTheRatioHeightInEachValidation() {
        assertWrappingScenario("Ratio", "Box", "BorderLayout NORTH")
        assertWrappingScenario("Ratio", "Box", "BorderLayout SOUTH")
        assertWrappingScenario("Ratio", "nested Box", "BorderLayout NORTH")
    }

    @Test
    fun aViewUsesTheWidthChosenByAnAlwaysVisibleVerticalScrollBar() {
        val views: Map<String, @Composable (MutableIntState) -> Unit> =
            mapOf(
                "a sibling's text changing" to
                    { change -> TrackingViewportView({ change.intValue }, movesContent = false) },
                "its placement changing" to { change ->
                    TrackingViewportView({ change.intValue }, movesContent = true)
                },
            )
        for ((changing, view) in views) {
            runComposeSwingTest {
                val reference = JScrollPane()
                val expectedWidth =
                    WIDTH - reference.insets.run { left + right } - reference.verticalScrollBar.preferredSize.width
                val change = mutableIntStateOf(0)
                setContent {
                    ScrollPane(
                        modifier = SwingModifier.preferredSize(WIDTH, WIDTH),
                        verticalScrollbar = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                    ) {
                        Viewport { view(change) }
                    }
                }
                awaitIdle()
                val component = onNodeWithTag("view").fetch<JComponent>()
                assertEquals(expectedWidth, component.width, "$changing: the explicit bar owns the viewport width")
                onNodeOfType<JScrollPane>().assertProperty(true, "$changing: the bar remains visible") {
                    verticalScrollBar.isVisible
                }
                val bounds = component.bounds
                val answers = mutableListOf<Int>()
                repeat(4) { changeIndex ->
                    change.intValue++
                    awaitIdle()
                    component.preferredSize = null
                    val answer = component.preferredSize.height
                    answers += answer
                    assertEquals(expectedWidth.atRatio, answer, "$changing: finite preferred height at current width")
                    assertEquals(bounds, component.bounds, "$changing: size queries keep the viewport grant")
                    withRecordedRepaints { recorder ->
                        component.captureToImage()
                        recorder.assertAskedForNoLayout(component, "$changing: paint has no size-query work")
                    }
                    assertEquals(
                        bounds,
                        component.bounds,
                        "$changing: paint keeps the viewport grant after change $changeIndex",
                    )
                }
                assertEquals(
                    List(4) { expectedWidth.atRatio },
                    answers,
                    "$changing: query results have no width history",
                )
            }
        }
    }

    @Test
    fun aViewTrackingTheViewportWidthKeepsStableAsNeededScrollbarOutcomes() {
        val views: Map<String, @Composable (MutableIntState) -> Unit> =
            mapOf(
                "a sibling's text changing" to
                    { change -> TrackingViewportView({ change.intValue }, movesContent = false) },
                "its placement changing" to { change ->
                    TrackingViewportView({ change.intValue }, movesContent = true)
                },
            )
        for ((changing, view) in views) {
            for (case in listOf(ScrollBarCase.AtBothWidths, ScrollBarCase.AtNeither)) {
                runComposeSwingTest {
                    val reference = JScrollPane()
                    val wide = WIDTH - reference.insets.run { left + right }
                    val narrow = wide - reference.verticalScrollBar.preferredSize.width
                    val change = mutableIntStateOf(0)
                    setContent {
                        val extent = if (case == ScrollBarCase.AtBothWidths) narrow.atRatio - 10 else wide.atRatio + 10
                        ScrollPane(
                            modifier =
                                SwingModifier.preferredSize(
                                    WIDTH,
                                    extent + reference.insets.run { top + bottom },
                                ),
                        ) {
                            Viewport { view(change) }
                        }
                    }
                    settleWithPaint()
                    val name = "$case, $changing"
                    val bounds = onNodeWithTag("view").fetch<JComponent>().bounds
                    val scrolls = case == ScrollBarCase.AtBothWidths
                    assertInitialViewportState(case, name, scrolls, narrow, wide)

                    repeat(4) { index ->
                        change.intValue++
                        awaitIdle()
                        settleWithPaint()
                        onNodeWithTag("view").assertProperty(
                            bounds,
                            "$name: view bounds after change $index",
                        ) { bounds }
                        onNodeOfType<JScrollPane>().assertProperty(scrolls, "$name: scrollbar after change $index") {
                            verticalScrollBar.isVisible
                        }
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @CsvSource("release, false", "release, true", "queried release, false", "queried release, true")
    fun aStockParentReleasesWidthUsingItsCurrentHeight(
        history: String,
        native: Boolean,
    ) = runComposeSwingTest {
        val fixture = stockParentFixture(native)
        val box = fixture.box
        val naturalWidth = fixture.naturalWidth
        assertEquals(
            Dimension(WIDTH, naturalWidth.atRatio),
            box.size,
            "$history, native=$native: the first parent grant uses the natural preferred width",
        )
        (box.parent as JComponent).revalidate()
        root.validate()
        awaitIdle()
        assertEquals(Dimension(WIDTH, WIDTH.atRatio), box.size, "$history, native=$native: current width")
        fixture.fills = false
        if (history == "queried release") box.preferredSize
        (box.parent as JComponent).revalidate()
        root.validate()
        awaitIdle()
        assertEquals(
            Dimension(naturalWidth, WIDTH.atRatio),
            box.size,
            "$history, native=$native: the next parent query uses its current width before releasing the grant",
        )
        (box.parent as JComponent).revalidate()
        root.validate()
        awaitIdle()
        assertEquals(
            Dimension(naturalWidth, naturalWidth.atRatio),
            box.size,
            "$history, native=$native: explicit validation at natural width",
        )
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun aStockParentRestoresWidthUsingItsCurrentHeight(native: Boolean) =
        runComposeSwingTest {
            val fixture = stockParentFixture(native)
            val box = fixture.box
            val naturalWidth = fixture.naturalWidth
            assertEquals(
                Dimension(WIDTH, naturalWidth.atRatio),
                box.size,
                "native=$native: the first parent grant uses the natural preferred width",
            )
            (box.parent as JComponent).revalidate()
            root.validate()
            awaitIdle()
            assertEquals(Dimension(WIDTH, WIDTH.atRatio), box.size, "native=$native: current width")
            fixture.width.intValue = WIDTH / 2
            awaitIdle()
            (box.parent as JComponent).revalidate()
            root.validate()
            awaitIdle()
            assertEquals(Dimension(WIDTH / 2, (WIDTH / 2).atRatio), box.size, "narrow height after explicit validation")
            fixture.width.intValue = WIDTH
            awaitIdle()
            assertEquals(WIDTH, box.width, "restored grant")
            (box.parent as JComponent).revalidate()
            root.validate()
            awaitIdle()
            assertEquals(Dimension(WIDTH, WIDTH.atRatio), box.size, "restored height after explicit validation")
        }

    private class StockParentFixture(
        val width: MutableIntState,
    ) {
        var fills: Boolean = true
        lateinit var box: JComponent
        var naturalWidth: Int = 0
    }

    private suspend fun ComposeSwingTest.stockParentFixture(native: Boolean): StockParentFixture {
        val fixture = StockParentFixture(mutableIntStateOf(WIDTH))
        val raw =
            object : JComponent() {
                private val naturalWidth = JLabel("Preview").preferredSize.width

                override fun getPreferredSize(): Dimension =
                    Dimension(naturalWidth, (width.takeIf { it > 0 } ?: naturalWidth).atRatio)

                override fun getMinimumSize(): Dimension = Dimension(0, 0)
            }
        val layout =
            SwingParentLayout(
                preferred = { it.getComponent(0).preferredSize },
                minimum = { it.getComponent(0).minimumSize },
            ) { parent ->
                val child = parent.getComponent(0)
                val preferred = child.preferredSize
                child.setBounds(0, 0, if (fixture.fills) parent.width else preferred.width, preferred.height)
            }
        setContent {
            SwingNode(
                factory = { JPanel(layout) },
                modifier = SwingModifier.preferredSize(fixture.width.intValue, 600),
            ) {
                if (native) {
                    SwingNode(factory = { raw }, modifier = SwingModifier.testTag("box"))
                } else {
                    Box(modifier = SwingModifier.testTag("box")) {
                        RatioLabel(SwingModifier.testTag("label").fillMaxWidth())
                    }
                }
            }
        }
        fixture.box = onNodeWithTag("box").fetch<JComponent>()
        awaitIdle()
        fixture.naturalWidth =
            if (native) raw.preferredSize.width else onNodeWithTag("label").fetch<JComponent>().preferredSize.width
        return fixture
    }

    @Test
    fun wrappingChildrenTakeTheirInitialGrantedHeightBeforePaint() {
        for ((kind, shape) in listOf(
            "TextArea" to "Column",
            "TextArea" to "nested Box",
            "HTML Label" to "Column",
            "HTML EditorPane" to "Column",
        )) {
            assertWrappingScenario(kind, shape, "BorderLayout NORTH")
        }
        assertWrappingScenario("TextArea", "Column", "FlowLayout")
    }

    @Test
    fun wrappingChildrenTakeANewGrantedHeightBeforePaint() {
        for (shape in listOf("Column", "Box", "Layout")) assertWrappingScenario("TextArea", shape, "CENTER")
        for (kind in listOf("HTML Label", "HTML EditorPane")) assertWrappingScenario(kind, "Column", "CENTER")
    }

    private fun assertWrappingScenario(
        kind: String,
        shape: String,
        parent: String,
    ) {
        val case = WrappingCase(kind, shape, parent)
        val name = case.name
        val slot =
            (swingParentSlots(emptyList()) + ("CENTER" to borderLayoutSlot(BorderLayout.CENTER))).getValue(parent)
        runComposeSwingTest {
            var width by mutableIntStateOf(
                if (parent == "CENTER" && kind != "unbounded TextArea") WIDTH + 80 else WIDTH,
            )
            var measures = 0
            setContent {
                WrappingScene(case, width, slot) { measures++ }
            }
            val host = onNodeWithTag("host").fetch<JComponent>()
            val text = onNodeWithTag("text").fetch<JComponent>()
            val offers =
                when {
                    kind == "Ratio" -> (0..5).map { WIDTH - 20 * it }
                    parent == "CENTER" && kind != "unbounded TextArea" -> listOf(width, WIDTH)
                    else -> listOf(width)
                }
            for (offered in offers) {
                withRecordedRepaints { recorder ->
                    width = offered
                    awaitIdle()
                    assertFalse(host in recorder.relayouts, "$name: no corrective validation")
                }
                assertInitialWrappingBounds(case, offered, host, text)
                val bounds = text.bounds
                val passes = measures
                withRecordedRepaints { recorder ->
                    settleWithPaint()
                    recorder.assertAskedForNoLayout(host, "$name: paint")
                }
                assertEquals(bounds, text.bounds, "$name: paint preserves bounds")
                assertEquals(passes, measures, "$name: paint measures nothing")
                if (kind == "Ratio") {
                    val expected = Dimension(offered, offered.atRatio)
                    assertEquals(expected, host.size, "$name: unchanged after paint")
                    if (shape == "nested Box") {
                        onNodeWithTag("box").assertProperty(expected, "$name: box after paint") { size }
                    }
                    assertEquals(0, cyclesUntilStable(host), "$name: no descendant correction")
                }
                if (kind in listOf("TextArea", "HTML Label", "HTML EditorPane")) {
                    assertEquals(
                        onNodeWithTag("referenceHost").fetch<JComponent>().getComponent(0).size,
                        text.size,
                        "$name: raw Swing size at $offered",
                    )
                }
            }
        }
    }

    @Composable
    private fun WrappingScene(
        case: WrappingCase,
        width: Int,
        slot: Slot,
        onMeasure: () -> Unit,
    ) {
        Panel(PanelLayout.Grid(), modifier = SwingModifier.preferredSize(2 * width, 600)) {
            if (case.kind == "Ratio") {
                Panel(PanelLayout.Border()) {
                    Label("Preview", modifier = SwingModifier.north())
                }
            } else {
                slot(width) { modifier ->
                    SwingNode(
                        factory = { nativeWrappingReference(case.kind) },
                        modifier = modifier.testTag("referenceHost"),
                    )
                }
            }
            slot(width) { modifier ->
                WrappingContainer(case.shape, modifier.testTag("host")) {
                    val grant =
                        when (case.kind) {
                            "unbounded TextArea" -> SwingModifier.wrapContentWidth(unbounded = true)
                            "Ratio" -> SwingModifier.fillMaxWidth().aspectRatio(16f / 9f)
                            else -> SwingModifier.fillMaxWidth()
                        }
                    if (case.kind == "Ratio") {
                        Label("Preview", modifier = grant.testTag("text").countingMeasures { onMeasure() })
                    } else {
                        WrappingTexts.getValue(if (case.kind == "unbounded TextArea") "TextArea" else case.kind)(
                            grant.testTag("text").countingMeasures { onMeasure() },
                        )
                    }
                }
            }
        }
    }

    private fun nativeWrappingReference(kind: String): JPanel {
        val reference =
            when (kind) {
                "HTML Label" -> JLabel("<html>$WRAPPING_TEXT</html>")
                "HTML EditorPane" -> JEditorPane("text/html", "<html>$WRAPPING_TEXT</html>")
                else -> wrappingTextArea()
            }
        return object : JPanel(BorderLayout()) {
            override fun doLayout() {
                reference.setSize(width - insets.left - insets.right, height)
                reference.getBaseline(reference.width, reference.height)
                super.doLayout()
            }
        }.apply { add(reference, BorderLayout.NORTH) }
    }

    private fun ComposeSwingTest.assertInitialWrappingBounds(
        case: WrappingCase,
        width: Int,
        host: JComponent,
        text: JComponent,
    ) {
        val (kind, shape, parent) = case
        val name = case.name
        if (kind == "Ratio") {
            val expected = Dimension(width, width.atRatio)
            assertEquals(expected, host.size, "$name: container at $width")
            assertEquals(Rectangle(Point(), expected), text.bounds, "$name: actual child at $width")
            if (shape == "Box") {
                host.preferredSize = null
                host.minimumSize = null
                val preferred = host.preferredSize
                val minimum = host.minimumSize
                assertEquals(
                    preferred.width.atRatio,
                    minimum.height,
                    "$name: minimum height at natural preferred width",
                )
            }
        } else if (kind == "unbounded TextArea") {
            assertEquals(text.preferredSize, text.size, "$name: first validation at its preferred size")
        } else {
            assertWrapped(text, "$name: first validation at $width")
            if (parent != "CENTER") assertEquals(text.height, host.height, "$name: host holds wrapped height")
        }
        if (shape == "nested Box") {
            assertEquals(text.size, host.size, "$name: column holds the nested box")
            onNodeWithTag("box").assertProperty(text.size, name) { size }
        }
        if (parent == "FlowLayout") assertEquals(text.size, host.size, "$name: preferred size grant")
    }

    private data class WrappingCase(
        val kind: String,
        val shape: String,
        val parent: String,
    ) {
        val name: String get() = "$kind, $shape, $parent"
    }

    @Test
    fun aWrappingTextAreaOfferedAnUnboundedWidthTakesTheHeightForTheWidthItTakesInOneValidation() {
        assertWrappingScenario("unbounded TextArea", "Column", "CENTER")
    }

    @Test
    fun wrappingChildrenSharingARowTakeTheirWrappedHeightInOneValidation() {
        for (baseline in listOf(false, true)) {
            runComposeSwingTest {
                var width by mutableIntStateOf(if (baseline) WIDTH else WIDTH + 80)
                setContent {
                    borderLayoutSlot(BorderLayout.CENTER)(width) { modifier ->
                        Column(modifier = modifier) {
                            Row(modifier = SwingModifier.testTag("row")) {
                                val alignment = if (baseline) SwingModifier.alignByBaseline() else SwingModifier
                                WrappingTexts.getValue(if (baseline) "HTML Label" else "TextArea")(
                                    alignment.testTag("text").weight(1f),
                                )
                                Label("Side", modifier = alignment.testTag("side"))
                            }
                            if (!baseline) Label("After", modifier = SwingModifier.testTag("after"))
                        }
                    }
                }
                val text = onNodeWithTag("text").fetch<JComponent>()
                val row = onNodeWithTag("row").fetch<JComponent>()
                assertWrapped(text, "the first validation, baseline=$baseline")
                val bounds = text.bounds
                settleWithPaint()
                if (baseline) {
                    settleWithPaint()
                    assertEquals(bounds, text.bounds, "the paints that follow lay nothing out again")
                } else {
                    withRecordedRepaints { recorder ->
                        width = WIDTH
                        awaitIdle()
                        assertFalse(row in recorder.relayouts, "the row requests no validation of its own")
                    }
                    val side = onNodeWithTag("side").fetch<JComponent>()
                    assertEquals(WIDTH - side.width, text.width, "the weighted area takes what the label leaves")
                    assertWrapped(text, "a single validation")
                    assertEquals(text.height, row.height, "the row holds the area")
                    assertEquals(
                        text.height,
                        onNodeWithTag("after").fetch<JComponent>().y,
                        "the column places what follows below the wrapped area",
                    )
                }
            }
        }
    }

    @ParameterizedTest(name = "HTML={0}")
    @ValueSource(booleans = [true, false])
    fun textChangesAtHeldWidthUpdateHtmlAndWrappingTextAreaHeights(html: Boolean) {
        runComposeSwingTest {
            val shortText = "Short"
            val longText = "A sentence with enough words to wrap at a fixed width. ".repeat(12)
            var text by mutableStateOf(shortText)
            setContent {
                borderLayoutSlot(BorderLayout.NORTH)(200) { modifier ->
                    Column(modifier.testTag("column")) {
                        if (html) {
                            Label("<html>$text</html>", SwingModifier.fillMaxWidth().testTag("text"))
                        } else {
                            TextArea(
                                text,
                                {},
                                SwingModifier.fillMaxWidth().testTag("text"),
                                lineWrap = true,
                                wrapStyleWord = true,
                            )
                        }
                        Label("After", SwingModifier.testTag("after"))
                    }
                }
            }
            val leaf = onNodeWithTag("text").fetch<JComponent>()
            val after = onNodeWithTag("after").fetch<JComponent>()
            val initialHeight = leaf.height
            assertEquals(200, leaf.width)

            text = longText
            awaitIdle()
            val longExpected = nativeHeight(html, longText, leaf.width)
            assertTrue(longExpected > initialHeight)
            assertEquals(longExpected, leaf.height)
            assertEquals(leaf.height, after.y)

            text = shortText
            awaitIdle()
            val shortExpected = nativeHeight(html, shortText, leaf.width)
            assertEquals(shortExpected, leaf.height)
            assertEquals(leaf.height, after.y)
        }
    }

    @Test
    fun aWrappingTextAreaInValidContainersNestedInOneItsParentNarrowsTakesItsWrappedHeightInOneValidation() {
        for (readsIntrinsicHeight in listOf(false, true)) {
            val name = if (readsIntrinsicHeight) "the outer box read its intrinsic height" else "the boxes measured"
            runComposeSwingTest {
                var width by mutableIntStateOf(WIDTH)
                setContent {
                    borderLayoutSlot(BorderLayout.CENTER)(width) { modifier ->
                        Column(modifier = modifier.testTag("column")) {
                            val outer =
                                if (readsIntrinsicHeight) SwingModifier.height(IntrinsicSize.Max) else SwingModifier
                            Box(modifier = outer.testTag("outer")) {
                                Box(modifier = SwingModifier.testTag("inner")) {
                                    WrappingTexts.getValue("TextArea")(SwingModifier.testTag("text").fillMaxWidth())
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
                        "$name: the boxes, resizing the area as the column lays them out, request no layout pass",
                    )
                }

                assertEquals(WIDTH / 2, text.width, "$name: the area takes the narrower width")
                assertWrapped(text, "$name: the validation that narrows its containers")
                assertTrue(text.height > wide, "$name: into more lines than it took at the wider width")
                assertEquals(text.height, containers.getValue("inner").height, "$name: the inner box holds the area")
            }
        }
    }

    private fun nativeHeight(
        html: Boolean,
        text: String,
        width: Int,
    ): Int {
        val reference =
            if (html) {
                JLabel("<html>$text</html>")
            } else {
                wrappingTextArea().apply { this.text = text }
            }
        reference.setSize(width, 1000)
        reference.getBaseline(width, 1000)
        return reference.preferredSize.height
    }

    /** Where the view's height, which follows its width, needs the viewport's vertical scroll bar. */
    private enum class ScrollBarCase {
        AtBothWidths,
        AtNeither,
    }

    @Composable
    private fun TrackingViewportView(
        change: () -> Int,
        movesContent: Boolean,
    ) {
        if (movesContent) {
            Layout(
                content = { RatioLabel(SwingModifier.testTag("label").fillMaxWidth()) },
                modifier = SwingModifier.testTag("view"),
                measurePolicy = { measurables, constraints ->
                    val child = measurables.single().measure(Constraints(maxWidth = constraints.maxWidth))
                    layout(child.width, child.height) { child.place(change() % 2, 0) }
                },
            )
        } else {
            Box(modifier = SwingModifier.testTag("view")) {
                RatioLabel(SwingModifier.testTag("label").fillMaxWidth())
                Label(if (change() % 2 == 0) "Even" else "Odd")
            }
        }
    }

    @Composable
    private fun WrappingContainer(
        shape: String,
        modifier: SwingModifier,
        content: @Composable ConstrainedScope.() -> Unit,
    ) {
        when (shape) {
            "Column" -> {
                Column(modifier = modifier, content = content)
            }

            "Box" -> {
                Box(modifier = modifier, content = content)
            }

            "nested Box" -> {
                Column(modifier = modifier) {
                    Box(modifier = SwingModifier.testTag("box"), content = content)
                }
            }

            "Layout" -> {
                Layout(modifier = modifier, content = content, measurePolicy = { measurables, constraints ->
                    val child =
                        measurables.single().measure(
                            Constraints(constraints.maxWidth, constraints.maxWidth, 0, constraints.maxHeight),
                        )
                    layout(child.width, child.height) { child.place(0, 0) }
                })
            }

            else -> {
                error("Unknown container: $shape")
            }
        }
    }

    @Composable
    context(scope: ConstrainedScope)
    private fun RatioLabel(modifier: SwingModifier) {
        Label("Preview", modifier = modifier.aspectRatio(16f / 9f))
    }

    private companion object {
        /** The height a 16:9 view takes at this width. */
        val Int.atRatio: Int get() = (this * 9f / 16f).roundToInt()
    }
}

/** Paints the tree and lets the validation any paint requests run, as a window's next frame would. */
internal suspend fun ComposeSwingTest.settleWithPaint() {
    captureToImage()
    awaitIdle()
}

private const val WIDTH = 320
