package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertAskedForNoLayout
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.layout.ScrollPane
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
 * Content whose height follows its width. A stock component inside a Foundation container, such as a wrapping text
 * area, takes the height it reports for the width it is granted in the validation that grants it, and the paint that
 * follows finds nothing to lay out again. A Foundation container under a stock parent that sets its width before
 * reading preferences
 * its height takes the height for that width in the same validation; under a parent that stretches it to another width
 * than it prefers, it takes that height one validation later, as Swing's own wrapping components do.
 */
class HeightFollowsWidthTest {
    @Test
    fun stockBorderRegionsGrantTheRatioHeightInEachValidation() {
        assertWrappingScenario("Ratio", "Box", "BorderLayout NORTH")
        assertWrappingScenario("Ratio", "Box", "BorderLayout SOUTH")
        assertWrappingScenario("Ratio", "nested Box", "BorderLayout NORTH")
    }

    @Test
    fun aViewTrackingTheViewportWidthSettlesWhereItsScrollBarChangesItsWidth() {
        val views: Map<String, @Composable (MutableIntState) -> Unit> =
            mapOf(
                "a sibling's text changing" to
                    { change -> TrackingViewportView({ change.intValue }, movesContent = false) },
                "its placement changing" to { change ->
                    TrackingViewportView({ change.intValue }, movesContent = true)
                },
            )
        for ((changing, view) in views) {
            for (case in ScrollBarCase.entries) {
                runComposeSwingTest {
                    val reference = JScrollPane()
                    val wide = WIDTH - reference.insets.run { left + right }
                    val narrow = wide - reference.verticalScrollBar.preferredSize.width
                    val change = mutableIntStateOf(0)
                    setContent {
                        val extent =
                            when (case) {
                                ScrollBarCase.AtBothWidths -> narrow.atRatio - 10
                                ScrollBarCase.AtTheWideWidthOnly -> (narrow.atRatio + wide.atRatio) / 2
                                ScrollBarCase.AtNeither -> wide.atRatio + 10
                            }
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
                    val pane = onNodeOfType<JScrollPane>().fetch()
                    val settled = onNodeWithTag("view").fetch<JComponent>()
                    settleWithPaint()
                    val name = "$case, $changing"
                    val bounds = settled.bounds
                    val scrolls = case != ScrollBarCase.AtNeither
                    assertEquals(scrolls, pane.verticalScrollBar.isVisible, "$name: the view settles on the scroll bar")
                    assertEquals(if (scrolls) narrow else wide, bounds.width, "$name: at the width it leaves")
                    if (case ==
                        ScrollBarCase.AtTheWideWidthOnly
                    ) {
                        assertEquals(wide.atRatio, bounds.height, "$name: wide height")
                    }
                    if (case == ScrollBarCase.AtNeither) {
                        assertEquals(wide.atRatio, onNodeWithTag("label").fetch<JComponent>().height, "$name: ratio")
                    }

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
    fun aStockParentReleasingOrRestoringAWidthUsesTheHeightForThatWidth() {
        for (history in listOf("release", "queried release", "restore")) {
            runComposeSwingTest {
                var width by mutableIntStateOf(WIDTH)
                var fills = true
                val layout =
                    SwingParentLayout(
                        preferred = { it.getComponent(0).preferredSize },
                        minimum = { it.getComponent(0).minimumSize },
                    ) { parent ->
                        val child = parent.getComponent(0)
                        val preferred = child.preferredSize
                        child.setBounds(0, 0, if (fills) parent.width else preferred.width, preferred.height)
                    }
                setContent {
                    SwingNode(factory = { JPanel(layout) }, modifier = SwingModifier.preferredSize(width, 600)) {
                        Box(modifier = SwingModifier.testTag("box")) {
                            RatioLabel(SwingModifier.testTag("label").fillMaxWidth())
                        }
                    }
                }
                val box = onNodeWithTag("box").fetch<JComponent>()
                settleWithPaint()
                assertEquals(Dimension(WIDTH, WIDTH.atRatio), box.size, "$history: initial filled height")
                if (history == "restore") {
                    width = WIDTH / 2
                    awaitIdle()
                    settleWithPaint()
                    settleWithPaint()
                    assertEquals(Dimension(width, width.atRatio), box.size, "narrow height after paint")
                    width = WIDTH
                    awaitIdle()
                    assertEquals(width, box.width, "restored grant")
                    assertEquals(width.atRatio, box.preferredSize.height, "preferred height at restored width")
                    assertEquals(width.atRatio, box.minimumSize.height, "minimum height at restored width")
                } else {
                    fills = false
                    (box.parent as JComponent).revalidate()
                    awaitIdle()
                    if (history == "queried release") box.preferredSize
                    settleWithPaint()
                    val preferredWidth = onNodeWithTag("label").fetch<JComponent>().preferredSize.width
                    assertEquals(
                        Dimension(preferredWidth, preferredWidth.atRatio),
                        box.size,
                        "$history: natural width and ratio height",
                    )
                    assertEquals(box.preferredSize, box.size, "$history: preferred size after paint")
                }
            }
        }
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
                        assertEquals(expected, onNodeWithTag("box").fetch<JComponent>().size, "$name: box after paint")
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
            if (shape == "Box") assertEquals(expected.height, host.minimumSize.height, "$name: minimum at $width")
        } else if (kind == "unbounded TextArea") {
            assertEquals(text.preferredSize, text.size, "$name: first validation at its preferred size")
        } else {
            assertWrapped(text, "$name: first validation at $width")
            if (parent != "CENTER") assertEquals(text.height, host.height, "$name: host holds wrapped height")
        }
        if (shape == "nested Box") {
            assertEquals(text.size, host.size, "$name: column holds the nested box")
            assertEquals(text.size, onNodeWithTag("box").fetch<JComponent>().size, name)
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

    /** Where the view's height, which follows its width, needs the viewport's vertical scroll bar. */
    private enum class ScrollBarCase {
        AtBothWidths,
        AtTheWideWidthOnly,
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
