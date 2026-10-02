package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertAskedForNoLayout
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.withRecordedRepaints
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.LayoutManager
import java.awt.Shape
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.JViewport
import javax.swing.event.DocumentEvent
import javax.swing.plaf.basic.BasicHTML
import javax.swing.plaf.basic.BasicTextAreaUI
import javax.swing.text.Element
import javax.swing.text.FlowView
import javax.swing.text.View
import javax.swing.text.ViewFactory
import javax.swing.text.WrappedPlainView
import javax.swing.text.html.BlockView
import javax.swing.text.html.ParagraphView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A constraint-based container in a Swing layout-manager slot, compared with a wrapping text area in the same slot.
 * The container lays its content out at the bounds the parent grants and does not correct the grant during painting.
 * A parent
 * that sets the container's width before reading preferences, or its height after the first layout, has it at that size
 * at once.
 */
class SwingParentParityTest {
    @Test
    fun aRowOfIntrinsicHeightHoldingAWrappingTextAreaCostsNoMoreThanTheSameRowWithoutItInEachSlot() {
        for ((name, slot) in Slots) {
            val rows =
                listOf(false, true).map { intrinsic ->
                    settlingOf(name, slot) { modifier, area ->
                        Column(modifier = modifier) {
                            val height = if (intrinsic) SwingModifier.height(IntrinsicSize.Min) else SwingModifier
                            Row(modifier = height.testTag("row")) {
                                area(SwingModifier.weight(1f))
                                Label("|", modifier = SwingModifier.fillMaxHeight())
                            }
                        }
                    }
                }
            val column =
                settlingOf(name, slot) { modifier, area ->
                    Column(modifier = modifier) { area(SwingModifier.fillMaxWidth()) }
                }
            val raw = settlingOf(name, slot) { modifier, area -> area(modifier) }
            assertEquals(raw.map { it.name }, column.map { it.name }, "$name: raw comparison steps")
            assertEquals(rows[0].map { it.name }, rows[1].map { it.name }, "$name: intrinsic comparison steps")
            for ((control, actual) in raw.zip(column)) {
                val step = "$name, ${control.name}"
                assertTrue(actual.cycles <= control.cycles, "$step: $actual vs raw $control")
                assertEquals(control.height, actual.height, "$step: raw height")
            }
            assertTrue(
                rows[1][0].lineBreaks <= column[0].lineBreaks,
                "$name: first intrinsic row ${rows[1][0]} vs Column ${column[0]}",
            )
            for ((plain, intrinsic) in rows[0].zip(rows[1])) {
                val step = "$name, ${plain.name}"
                assertTrue(intrinsic.lineBreaks <= plain.lineBreaks, "$step: $intrinsic vs plain $plain")
                assertTrue(intrinsic.cycles <= plain.cycles, "$step: $intrinsic vs plain $plain")
                assertEquals(plain.areaHeight, intrinsic.areaHeight, "$step: area height")
                assertEquals(intrinsic.areaHeight, intrinsic.rowHeight, "$step: row holds area")
            }
        }
    }

    @Test
    fun widthIndependentChildrenSettleWithoutPaintOrRepeatedPreferredSizeQueries() {
        for ((name, slot) in Slots) {
            runComposeSwingTest {
                var width by mutableIntStateOf(WIDTH)
                val label = CountingLabel()
                val panel = CountingSwingPanel(JLabel("Counted"))
                val policy = RecordingMeasurePolicy()
                setContent {
                    slot(width) { modifier ->
                        Column(modifier = modifier.testTag("container")) {
                            Layout(
                                content = { SwingNode(factory = { label }, modifier = SwingModifier.fillMaxWidth()) },
                                measurePolicy = policy,
                            )
                            SwingNode(factory = { panel }, modifier = SwingModifier.fillMaxWidth())
                        }
                    }
                }
                val container = onNodeWithTag("container").fetch<JComponent>()
                val initialBounds = container.bounds
                val initialMeasures = policy.measures
                assertEquals(0, cyclesUntilStable(container), "$name: first show needs no corrective validation")
                assertEquals(initialBounds, container.bounds, "$name: first paint keeps bounds")
                assertEquals(initialMeasures, policy.measures, "$name: first paint measures nothing")
                for (step in 1..5) {
                    width = WIDTH - 20 * step
                    awaitIdle()
                    if (name.startsWith("BorderLayout")) {
                        assertEquals(label.height + panel.height, container.height, "$name: combined height")
                    }
                    val bounds = container.bounds
                    val passes = policy.measures
                    label.resetCounts()
                    policy.resetQueries()
                    panel.resetCounts()
                    assertEquals(0, cyclesUntilStable(container), "$name: no corrective paint at $width")
                    assertEquals(bounds, container.bounds, "$name: paint keeps bounds")
                    if (name in WidthSettingSlots) {
                        assertEquals(passes, policy.measures, "$name: first paint measures nothing")
                        assertFirstPaintSizeReads(name, policy, label, panel)
                        assertSettledPaintSizeReads(name, container, policy, label, panel)
                    }
                }
            }
        }
    }

    private fun assertFirstPaintSizeReads(
        name: String,
        policy: RecordingMeasurePolicy,
        label: CountingLabel,
        panel: CountingSwingPanel,
    ) {
        if (name == "BorderLayout NORTH" || name == "BorderLayout SOUTH") {
            assertEquals(0, policy.preferredHeightQueries, "$name: first paint reads no nested preferred height")
            assertEquals(0, label.preferredSizeQueries, "$name: first paint reads no label preferred size")
        } else {
            assertTrue(
                policy.preferredHeightQueries <= 1,
                "$name: first paint reads nested preferred height at most once " +
                    "(got ${policy.preferredHeightQueries})",
            )
            assertTrue(
                label.preferredSizeQueries <= 1,
                "$name: first paint reads label preferred size at most once " +
                    "(got ${label.preferredSizeQueries})",
            )
        }
        assertEquals(0, policy.preferredWidthQueries, "$name: first paint reads no nested preferred width")
        assertEquals(0, panel.queries, "$name: first paint reads no panel preferred size")
    }

    private suspend fun ComposeSwingTest.assertSettledPaintSizeReads(
        name: String,
        container: JComponent,
        policy: RecordingMeasurePolicy,
        label: CountingLabel,
        panel: CountingSwingPanel,
    ) {
        val bounds = container.bounds
        label.resetCounts()
        policy.resetQueries()
        panel.resetCounts()
        val settledMeasures = policy.measures
        assertEquals(0, cyclesUntilStable(container), "$name: settled paint needs no corrective validation")
        assertEquals(bounds, container.bounds, "$name: settled paint keeps bounds")
        assertEquals(settledMeasures, policy.measures, "$name: settled paint measures nothing")
        assertEquals(0, policy.preferredWidthQueries, "$name: settled paint reads no nested preferred width")
        assertEquals(0, policy.preferredHeightQueries, "$name: settled paint reads no nested preferred height")
        assertEquals(0, label.preferredSizeQueries, "$name: settled paint reads no label preferred size")
        assertEquals(0, panel.queries, "$name: settled paint reads no panel preferred size")
    }

    @Test
    fun aStockChildAColumnDoesNotStretchIsNeitherMovedNorResizedWhenItsSiblingOrItsSlotChanges() {
        for ((name, slot) in Slots) {
            for (padded in listOf(false, true)) {
                val case = if (padded) "$name, padded" else name
                runComposeSwingTest {
                    var width by mutableIntStateOf(WIDTH)
                    var sibling by mutableStateOf("1")
                    val label = CountingLabel()
                    setContent {
                        slot(width) { modifier ->
                            Column(modifier = modifier.testTag("column")) {
                                SwingNode(
                                    factory = { label },
                                    modifier = if (padded) SwingModifier.padding(4) else SwingModifier,
                                )
                                Label(sibling)
                            }
                        }
                    }
                    val column = onNodeWithTag("column").fetch<JComponent>()
                    cyclesUntilStable(column)
                    val laidOut = label.bounds

                    for ((step, change) in listOf<() -> Unit>({ sibling = "2" }, { width -= 20 }).withIndex()) {
                        label.resetCounts()
                        change()
                        awaitIdle()
                        cyclesUntilStable(column)
                        val what = if (step == 0) "a sibling's change" else "a narrower slot"
                        assertEquals(laidOut, label.bounds, "$case, $what: the label stays where it was")
                        assertEquals(0, label.resizes, "$case, $what: the label is never resized")
                        assertTrue(
                            generateSequence<Component>(column) { it.parent }.all { it.isValid },
                            "$case, $what: the paint leaves the column and its ancestors valid",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun aContainerUnderAStockParentKeepsTheParentGrantUntilItIsValidatedAgain() {
        // The stock parent reads its preferred size before it grants the slot width. The child lays out at the grant;
        // paint does not query preferences or revise the parent's chosen bounds.
        val squeezedHeight =
            JLabel("<html>$WRAPPING_TEXT</html>").run {
                setSize(minimumSize.width, SLOT_HEIGHT)
                getBaseline(width, height)
                preferredSize.height
            }
        val weightedGridBag: Slot = { width, subject ->
            Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(width, SLOT_HEIGHT)) {
                Panel(PanelLayout.GridBag, modifier = SwingModifier.north().preferredSize(width, squeezedHeight)) {
                    subject(SwingModifier.item(weightx = 1.0, weighty = 1.0, fill = GridBagConstraints.HORIZONTAL))
                }
            }
        }
        val fillWidth: ConstrainedScope.() -> SwingModifier = { SwingModifier.fillMaxWidth() }
        val cases: List<Triple<String, Slot, ConstrainedScope.() -> SwingModifier>> =
            listOf(
                Triple("BoxLayout Y_AXIS", Slots.getValue("BoxLayout Y_AXIS"), fillWidth),
                Triple("GridBagLayout, fill", Slots.getValue("GridBagLayout fill HORIZONTAL"), fillWidth),
                Triple("weighted GridBagLayout, fill", weightedGridBag, fillWidth),
            ) +
                listOf(
                    "GridBagLayout, ratio" to Slots.getValue("GridBagLayout fill HORIZONTAL"),
                    "weighted GridBagLayout, ratio" to weightedGridBag,
                ).map { (name, slot) -> Triple(name, slot, { SwingModifier.aspectRatio(2f) }) }
        for ((name, slot, textModifier) in cases) {
            runComposeSwingTest {
                val policy = RecordingMeasurePolicy()
                setContent {
                    slot(WIDTH) { modifier ->
                        Layout(
                            content = {
                                Label("<html>$WRAPPING_TEXT</html>", modifier = textModifier().testTag("text"))
                            },
                            modifier = modifier.testTag("container"),
                            measurePolicy = policy,
                        )
                    }
                }
                val container = onNodeWithTag("container").fetch<JComponent>()
                val initialHeight = container.height
                assertEquals(listOf(initialHeight), policy.heights.distinct(), "$name: laid out at the first bounds")
                val text = onNodeWithTag("text").fetch<JComponent>()
                assertEquals(Dimension(WIDTH, initialHeight), container.size, "$name: the parent grant")
                if (name.endsWith("ratio")) {
                    assertEquals(WIDTH / 2, text.height, "$name: the child follows its ratio at the granted width")
                }
                val bounds = container.bounds
                withRecordedRepaints { recorder ->
                    container.captureToImage()
                    recorder.assertAskedForNoLayout(container, "$name: paint does not correct the parent grant")
                }
                assertEquals(bounds, container.bounds, "$name: paint preserves the parent grant")
                assertEquals(listOf(initialHeight), policy.heights.distinct(), "$name: paint does not remeasure")
            }
        }
    }

    @Test
    fun aContainerAStockParentSetsTheHeightOfAfterItsFirstLayoutIsLaidOutAtThatHeight() {
        // This parent can read preferences or grant its own size. The container measures each grant at the bounds it
        // receives; painting does not revise the parent's choice.
        fun scripted(
            granted: Dimension,
            readsPreferences: Boolean,
        ): LayoutManager =
            SwingParentLayout(preferred = { Dimension(granted) }) { parent ->
                for (child in parent.components) {
                    if (readsPreferences) {
                        child.preferredSize
                        child.minimumSize
                    }
                    child.setBounds(0, 0, granted.width, granted.height)
                }
            }
        val resizes =
            listOf(
                Triple("without reading preferences, the minimum height returned", 40, false),
                Triple("without reading preferences, the parent's preferred height", 50, false),
                Triple("reading preferences, below the minimum", 30, true),
            )
        for ((name, granted, readsPreferences) in resizes) {
            runComposeSwingTest {
                var layout by mutableStateOf(scripted(Dimension(100, 90), readsPreferences = true))
                val policy =
                    EmptySizePolicy(
                        preferredWidth = { 200 },
                        minimumWidth = { 50 },
                        preferredHeight = { 6000 / it },
                        minimumHeight = { 4000 / it },
                    )
                setContent {
                    SwingNode(factory = { JPanel() }, update = {
                        set(layout) {
                            this.layout = it
                            revalidate()
                        }
                    }) {
                        Layout(content = {}, modifier = SwingModifier.testTag("container"), measurePolicy = policy)
                    }
                }
                val container = onNodeWithTag("container").fetch<JComponent>()
                cyclesUntilStable(container)
                layout = scripted(Dimension(120, 90), readsPreferences = false)
                awaitIdle()
                cyclesUntilStable(container)
                policy.heights.clear()

                layout = scripted(Dimension(160, granted), readsPreferences)
                awaitIdle()
                cyclesUntilStable(container)
                assertEquals(Dimension(160, granted), container.size, "$name: precondition: the parent's own size")
                assertEquals(listOf(granted), policy.heights.distinct(), "$name: the only height laid out at")
            }
        }
    }

    @Test
    fun aContainerWhoseStockParentSetsItsWidthBeforeReadingPreferencesProvidesItsHeightThereInTheSameValidation() {
        // The container prefers its width at a 16:9 ratio. Laid out once at the region's width when it prefers that
        // width, it is still read at the region's width once it prefers more.
        for (region in listOf(BorderLayout.NORTH, BorderLayout.SOUTH)) {
            for (widths in listOf(listOf(100, 160, WIDTH), listOf(160, WIDTH))) {
                val name = "BorderLayout $region, preferring ${widths.joinToString(" then ")}"
                runComposeSwingTest {
                    var preferred by mutableIntStateOf(widths.first())
                    setContent {
                        borderLayoutSlot(region)(160) { modifier ->
                            Layout(
                                content = {},
                                modifier = modifier.testTag("container"),
                                measurePolicy =
                                    EmptySizePolicy(
                                        preferredWidth = { preferred },
                                        preferredHeight = { it * 9 / 16 },
                                        measuredHeight = { it.maxWidth * 9 / 16 },
                                    ),
                            )
                        }
                    }
                    val container = onNodeWithTag("container").fetch<JComponent>()
                    for (width in widths) {
                        preferred = width
                        awaitIdle()
                        assertEquals(
                            Dimension(160, 160 * 9 / 16),
                            container.size,
                            "$name: preferring $width, the ratio at the region's width in the first validation",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun aContainerWhosePreferredSizeIsReadWhileValidatingChildrenKeepsItsStockChildrenAtTheirLayoutWidth() =
        runComposeSwingTest {
            setContent {
                Panel(PanelLayout.Flow()) {
                    Column {
                        Label("A label wider than the narrowed one")
                        Label("Narrowed label", modifier = SwingModifier.testTag("narrowed").width(width = 30))
                        SwingNode(factory = {
                            JPanel(
                                SwingParentLayout(preferred = { Dimension(10, 10) }) { parent ->
                                    parent.parent.preferredSize
                                },
                            )
                        })
                    }
                }
            }
            awaitIdle()

            onNodeWithTag("narrowed").assertProperty(
                30,
                "the column's layout already ran, so the preference reads during its validation resize no stock child",
            ) { width }
        }

    /**
     * Pins the Swing height contract and its accepted validation cost. A wrapping component performs an unbounded
     * intrinsic height read at the width it holds. Under BorderLayout EAST, each granted width therefore changes the
     * Column's preferred size and can request another validation at paint time, including for an HTML JLabel.
     * Completing
     * the HTML in the layout pass keeps its row cost no higher than the same-pass raw Swing control.
     * AndroidX reads its interop View at an UNSPECIFIED width, so wrapping text measures on one line. Aligning that
     * intrinsic read with AndroidX turns this test red: the Column would keep twice one line's height as its width and
     * paint would request no validation. Preserve the Swing contract and the pinned cost when changing this path.
     */
    @Test
    fun aColumnSizedByTheHeightOfAWrappingComponentUnderBorderLayoutEastTakesAnotherWidthAsInPlainSwing() {
        fun wrappingTextArea() =
            JTextArea(WRAPPING_TEXT).apply {
                lineWrap = true
                wrapStyleWord = true
            }
        val leaves: Map<String, () -> JComponent> =
            mapOf(
                "JTextArea" to ::wrappingTextArea,
                "JPanel holding a JTextArea" to
                    { JPanel(BorderLayout()).apply { add(wrappingTextArea()) } },
                "JEditorPane showing HTML" to { JEditorPane("text/html", "<html>$WRAPPING_TEXT</html>") },
            )
        for ((kind, leaf) in leaves) {
            val plain = eastCycles(leaf, inColumn = false).drop(1)
            val column = eastCycles(leaf, inColumn = true).drop(1)
            assertTrue(plain.all { it.paintRequestsLayout }, "$kind: each raw paint requests layout")
            assertTrue(
                plain.zipWithNext().all { (before, after) ->
                    before.width != after.width
                },
                "$kind: raw widths change",
            )
            assertEquals(plain.map { it.width }, column.map { it.width }, "$kind: same widths as raw Swing")
            assertEquals(
                plain.map { it.paintRequestsLayout },
                column.map { it.paintRequestsLayout },
                "$kind: same paint requests",
            )
        }
    }

    @Test
    fun htmlUnderBorderLayoutEastPreservesNativePaintRequestsAndValidationCost() {
        val lazy = eastCycles(::RowCountingHtmlLabel, inColumn = false, samePass = false)
        val completed = eastCycles(::RowCountingHtmlLabel, inColumn = false, samePass = true)
        val column = eastCycles(::RowCountingHtmlLabel, inColumn = true, samePass = true)
        assertEquals(completed.map { it.width }, lazy.map { it.width }, "lazy raw widths match native completion")
        assertEquals(completed.map { it.width }, column.map { it.width }, "Column widths match native completion")
        assertEquals(
            List(CYCLES) { false },
            column.drop(1).map { it.paintRequestsLayout },
            "Column paints do not reconcile size preferences",
        )
        assertEquals(List(CYCLES) { true }, lazy.drop(1).map { it.paintRequestsLayout }, "lazy raw paint requests")
        assertEquals(
            List(CYCLES) { false },
            completed.drop(1).map { it.paintRequestsLayout },
            "same-pass raw paint requests",
        )
        for ((actual, control) in column.zip(lazy)) {
            assertTrue(actual.validations <= control.validations, "validations: $actual vs lazy $control")
        }
        assertEquals(5, lazy.last().validations, "lazy raw: validation entries")
        assertTrue(lazy.last().rows < completed.last().rows, "lazy raw leaves the final width pending")
        for ((actual, control) in column.zip(completed)) {
            assertTrue(actual.rows <= control.rows, "rows at ${control.width}: $actual vs native $control")
            assertEquals(control.markupHeight, actual.markupHeight, "native completion at ${control.width}")
        }
        assertEquals(completed.last().rows, column.last().rows, "Column completes the native rows at the final width")
    }

    @Test
    fun htmlUnderBorderLayoutEastCompletesEachPrescribedWidthWithNativeRowCost() {
        val widths = listOf(32, 864, 64, 800, 64)
        val native = eastHtmlAtWidths(widths, inColumn = false)
        val column = eastHtmlAtWidths(widths, inColumn = true)
        assertEquals(widths, native.map { it.width }, "native EAST grants the prescribed widths")
        assertEquals(widths, column.map { it.width }, "Column EAST grants the prescribed widths")
        var columnRows = 0
        var nativeRows = 0
        for ((actual, control) in column.zip(native)) {
            assertTrue(
                actual.rows - columnRows <= control.rows - nativeRows,
                "row cost through paint and resize to ${control.width}: $actual vs native $control",
            )
            assertEquals(control.markupHeight, actual.markupHeight, "native EAST completion at ${control.width}")
            columnRows = actual.rows
            nativeRows = control.rows
        }
        assertEquals(native.last().rows, column.last().rows, "Column completes the native rows at the final width")
    }

    @Test
    fun scriptedPreferredSizeQueriesFollowTheEntirePrescribedWidthHistory() {
        val widths = listOf(32, 864, 64, 800, 64)
        val leaf = {
            object : JComponent() {
                override fun getPreferredSize(): Dimension {
                    val nextWidth =
                        when (width) {
                            0 -> widths[0]
                            32 -> widths[1]
                            864 -> widths[2]
                            64 -> widths[3]
                            800 -> widths[4]
                            else -> error("Unexpected granted width: $width")
                        }
                    return Dimension(1, nextWidth / 2)
                }
            }
        }
        for (inColumn in listOf(false, true)) {
            val cycles = eastCycles(leaf, inColumn)
            assertEquals(widths, cycles.map { it.width }, "Column $inColumn: all granted widths")
        }
    }

    @Test
    fun htmlCompletionAtPrescribedWidthsCostsNoMoreThanAnIndependentNativeOracle() =
        runComposeSwingTest {
            val widths = listOf(32, 864, 64, 800, 64)
            val oracle = RowCountingHtmlLabel()
            val expected =
                widths.map { width ->
                    val before = oracle.rows
                    oracle.setSize(width, width / 2)
                    oracle.getBaseline(width, width / 2)
                    Triple(width, oracle.rows - before, oracle.markup.getPreferredSpan(View.Y_AXIS))
                }
            val label = RowCountingHtmlLabel()
            var width by mutableIntStateOf(widths.first())
            val initialRows = label.rows
            setContent {
                borderLayoutSlot(BorderLayout.NORTH)(width) { modifier ->
                    Column(modifier = modifier) {
                        SwingNode(factory = { label }, modifier = SwingModifier.fillMaxWidth().aspectRatio(2f))
                    }
                }
            }
            var previousRows = initialRows
            for ((offered, rows, height) in expected) {
                width = offered
                awaitIdle()
                assertEquals(offered, label.width, "HTML grant at $offered")
                assertTrue(label.rows - previousRows <= rows, "HTML completion rows at $offered")
                assertEquals(height, label.markup.getPreferredSpan(View.Y_AXIS), "HTML completion at $offered")
                previousRows = label.rows
            }
        }

    /**
     * One step of a scenario: the cycles it took to settle, the height the subject settled at, the heights of the text
     * area and of the container tagged "row", where there is one, and the line breaks a counted text area worked out.
     */
    private data class Settled(
        val name: String,
        val cycles: Int,
        val height: Int,
        val areaHeight: Int,
        val rowHeight: Int,
        val lineBreaks: Int,
    )

    private data class MarkupCycle(
        val width: Int,
        val paintRequestsLayout: Boolean,
        val validations: Int,
        val rows: Int,
        val markupHeight: Float,
    )

    /** Counts rows completed by Swing's flow strategy without changing its layout. */
    private class RowCountingHtmlLabel : JLabel(WRAPPING_TEXT) {
        var rows = 0
        val markup: View = BasicHTML.createHTMLView(this, "<html>$WRAPPING_TEXT</html>")

        init {
            putClientProperty(BasicHTML.propertyKey, markup)
            val root = markup.getView(0)
            val body = root.getView(1)
            assertEquals("body", body.element.name, "the HTML fixture has one body")
            val factory = markup.viewFactory
            root.replace(
                1,
                1,
                arrayOf(
                    object : BlockView(body.element, View.Y_AXIS) {
                        override fun getViewFactory(): ViewFactory =
                            ViewFactory { element ->
                                if (element.name == "p-implied") {
                                    object : ParagraphView(element) {
                                        init {
                                            strategy =
                                                object : FlowView.FlowStrategy() {
                                                    override fun layoutRow(
                                                        view: FlowView,
                                                        rowIndex: Int,
                                                        position: Int,
                                                    ): Int {
                                                        rows++
                                                        return super.layoutRow(view, rowIndex, position)
                                                    }
                                                }
                                        }
                                    }
                                } else {
                                    factory.create(element)
                                }
                            }
                    },
                ),
            )
            markup.setSize(markup.getPreferredSpan(View.X_AXIS), markup.getPreferredSpan(View.Y_AXIS))
        }
    }

    private fun eastHtmlAtWidths(
        widths: List<Int>,
        inColumn: Boolean,
    ): List<MarkupCycle> {
        val cycles = mutableListOf<MarkupCycle>()
        runComposeSwingTest {
            val child = RowCountingHtmlLabel()
            var width by mutableIntStateOf(widths.first())
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(1000, 600)) {
                    val modifier = SwingModifier.layoutConstraint(BorderLayout.EAST).testTag("subject")
                    if (inColumn) {
                        Column(modifier = modifier.preferredSize(width, width / 2)) {
                            SwingNode(factory = { child }, modifier = SwingModifier.fillMaxWidth().aspectRatio(2f))
                        }
                    } else {
                        SwingNode(factory = {
                            JPanel(
                                SwingParentLayout(preferred = { Dimension() }) { parent ->
                                    val component = parent.getComponent(0)
                                    component.setBounds(0, 0, parent.width, parent.width / 2)
                                    component.getBaseline(component.width, component.height)
                                },
                            ).apply { add(child) }
                        }, modifier = modifier.preferredSize(width, width / 2))
                    }
                }
            }
            val component = onNodeWithTag("subject").fetch<JComponent>()

            fun record() =
                MarkupCycle(
                    component.width,
                    false,
                    0,
                    child.rows,
                    child.markup.getPreferredSpan(View.Y_AXIS),
                )
            cycles += record()
            for (nextWidth in widths.drop(1)) {
                captureToImage()
                width = nextWidth
                component.revalidate()
                awaitIdle()
                cycles += record()
            }
        }
        return cycles
    }

    private fun eastCycles(
        leaf: () -> JComponent,
        inColumn: Boolean,
        samePass: Boolean = false,
    ): List<MarkupCycle> {
        val cycles = mutableListOf<MarkupCycle>()
        runComposeSwingTest {
            val child = leaf()
            var validations = 0
            val host =
                object : JPanel(BorderLayout()) {
                    override fun validateTree() {
                        validations++
                        super.validateTree()
                    }
                }
            setContent {
                SwingNode(factory = { host }, modifier = SwingModifier.preferredSize(320, 600)) {
                    val modifier = SwingModifier.layoutConstraint(BorderLayout.EAST).testTag("subject")
                    if (inColumn) {
                        Column(modifier = modifier) {
                            SwingNode(factory = { child }, modifier = SwingModifier.fillMaxWidth().aspectRatio(2f))
                        }
                    } else {
                        SwingNode(factory = {
                            JPanel(
                                SwingParentLayout(preferred = { parent ->
                                    val height = parent.getComponent(0).preferredSize.height
                                    Dimension(height * 2, height)
                                }) { parent ->
                                    val component = parent.getComponent(0)
                                    component.setBounds(0, 0, parent.width, parent.width / 2)
                                    if (samePass) component.getBaseline(component.width, component.height)
                                },
                            ).apply { add(child) }
                        }, modifier = modifier)
                    }
                }
            }
            val component = onNodeWithTag("subject").fetch<JComponent>()

            fun record(layoutRequested: Boolean) =
                MarkupCycle(
                    component.width,
                    layoutRequested,
                    validations,
                    (child as? RowCountingHtmlLabel)?.rows ?: 0,
                    (child as? RowCountingHtmlLabel)?.markup?.getPreferredSpan(View.Y_AXIS) ?: 0f,
                )
            cycles += record(false)
            repeat(CYCLES) {
                val layoutRequested =
                    withRecordedRepaints { recorder ->
                        captureToImage()
                        recorder.relayouts.any {
                            it === component || it.isAncestorOf(component) ||
                                component.isAncestorOf(it)
                        }
                    }
                component.revalidate()
                awaitIdle()
                cycles += record(layoutRequested)
            }
        }
        return cycles
    }

    private class EmptySizePolicy(
        private val preferredWidth: () -> Int,
        private val minimumWidth: () -> Int = preferredWidth,
        private val preferredHeight: (Int) -> Int,
        private val minimumHeight: (Int) -> Int = preferredHeight,
        private val measuredHeight: (Constraints) -> Int = { it.maxHeight },
    ) : MeasurePolicy {
        val heights = mutableListOf<Int>()

        override fun MeasureScope.measure(
            measurables: List<Measurable>,
            constraints: Constraints,
        ): MeasureResult {
            heights += constraints.maxHeight
            return layout(constraints.maxWidth, measuredHeight(constraints)) {}
        }

        override fun IntrinsicMeasureScope.minIntrinsicWidth(
            measurables: List<IntrinsicMeasurable>,
            height: Int,
        ): Int = minimumWidth()

        override fun IntrinsicMeasureScope.maxIntrinsicWidth(
            measurables: List<IntrinsicMeasurable>,
            height: Int,
        ): Int = preferredWidth()

        override fun IntrinsicMeasureScope.minIntrinsicHeight(
            measurables: List<IntrinsicMeasurable>,
            width: Int,
        ): Int = minimumHeight(width)

        override fun IntrinsicMeasureScope.maxIntrinsicHeight(
            measurables: List<IntrinsicMeasurable>,
            width: Int,
        ): Int = preferredHeight(width)
    }

    private class RecordingMeasurePolicy : MeasurePolicy {
        var measures = 0
            private set
        var preferredWidthQueries = 0
            private set
        var preferredHeightQueries = 0
            private set
        val heights = mutableListOf<Int>()

        fun resetQueries() {
            preferredWidthQueries = 0
            preferredHeightQueries = 0
        }

        override fun MeasureScope.measure(
            measurables: List<Measurable>,
            constraints: Constraints,
        ): MeasureResult {
            measures++
            if (constraints.hasBoundedHeight) heights += constraints.maxHeight
            val child = measurables.single().measure(constraints)
            return layout(child.width, child.height) { child.place(0, 0) }
        }

        override fun IntrinsicMeasureScope.minIntrinsicWidth(
            measurables: List<IntrinsicMeasurable>,
            height: Int,
        ): Int = measurables.single().minIntrinsicWidth(height)

        override fun IntrinsicMeasureScope.maxIntrinsicWidth(
            measurables: List<IntrinsicMeasurable>,
            height: Int,
        ): Int {
            preferredWidthQueries++
            return measurables.single().maxIntrinsicWidth(height)
        }

        override fun IntrinsicMeasureScope.minIntrinsicHeight(
            measurables: List<IntrinsicMeasurable>,
            width: Int,
        ): Int = measurables.single().minIntrinsicHeight(width)

        override fun IntrinsicMeasureScope.maxIntrinsicHeight(
            measurables: List<IntrinsicMeasurable>,
            width: Int,
        ): Int {
            preferredHeightQueries++
            return measurables.single().maxIntrinsicHeight(width)
        }
    }

    /** A label counting how often it has its preferred size read and how often it is resized. */
    private class CountingLabel : JLabel("Counted") {
        var preferredSizeQueries = 0
            private set
        var resizes = 0
            private set

        init {
            addComponentListener(
                object : ComponentAdapter() {
                    override fun componentResized(event: ComponentEvent) {
                        resizes++
                    }
                },
            )
        }

        override fun getPreferredSize(): Dimension {
            preferredSizeQueries++
            return super.getPreferredSize()
        }

        fun resetCounts() {
            preferredSizeQueries = 0
            resizes = 0
        }
    }

    private companion object {
        /** The text needs the bar at every width in a viewport 40 or 60 high, and at none from 150 up. */
        val Slots: Map<String, Slot> = swingParentSlots(listOf(40, 60, 150, 175, 200))

        const val CYCLES = 4

        fun settlingOf(
            slotName: String,
            slot: Slot,
            subject: @Composable (SwingModifier, @Composable (SwingModifier) -> Unit) -> Unit,
        ): List<Settled> {
            val steps = mutableListOf<Settled>()
            val area = LineBreakCountingTextArea()
            runComposeSwingTest {
                var width by mutableIntStateOf(WIDTH)
                var text by mutableStateOf(WRAPPING_TEXT)
                setContent {
                    SettlingContent(slot, width, text, subject, area)
                }
                val component = onNodeWithTag("subject").fetch<JComponent>()
                val explicitParentRevalidation =
                    slotName.startsWith("BoxLayout") || slotName.startsWith("GridBagLayout")
                if (explicitParentRevalidation) {
                    assertInitialSizeMismatch(slotName, component, area)
                    component.parent.revalidate()
                    root.validate()
                    awaitIdle()
                    assertEquals(
                        area.height,
                        component.height,
                        "$slotName: explicit validation uses current content height",
                    )
                }

                suspend fun step(name: String) {
                    val cycles = cyclesUntilStable(component)
                    val rowHeight =
                        if (onAllNodesWithTag("row").fetchSize() == 0) {
                            area.height
                        } else {
                            onNodeWithTag("row").fetch<JComponent>().height
                        }
                    steps += Settled(name, cycles, component.height, area.height, rowHeight, area.lineBreaks)
                    area.resetLineBreaks()
                }
                step("first show")
                for (step in 1..5) {
                    width = WIDTH - 20 * step
                    awaitIdle()
                    if (explicitParentRevalidation) {
                        component.parent.revalidate()
                        root.validate()
                        awaitIdle()
                    }
                    step("drag to $width")
                }
                width += 20
                awaitIdle()
                if (explicitParentRevalidation) {
                    component.parent.revalidate()
                    root.validate()
                    awaitIdle()
                }
                step("drag back to $width")
                text += " One more sentence, long enough to add a line or two to the wrapped text."
                awaitIdle()
                step("text edit")
                // A text area a viewport stretches to its height takes that height instead.
                if (area.parent !is JViewport || area.height < area.parent.height) {
                    assertEquals(area.preferredSize.height, area.height, "the area settles at its height for its width")
                }
            }
            return steps
        }
    }
}

private fun assertInitialSizeMismatch(
    slotName: String,
    component: JComponent,
    area: LineBreakCountingTextArea,
) {
    if (component.height != area.height) {
        assertTrue(area.width in 1..component.width, "$slotName: content fits the initial parent grant")
        assertTrue(component.height > area.height, "$slotName: initial wrapper keeps the parent grant")
    }
}

@Composable
private fun SettlingContent(
    slot: Slot,
    width: Int,
    text: String,
    subject: @Composable (SwingModifier, @Composable (SwingModifier) -> Unit) -> Unit,
    area: LineBreakCountingTextArea,
) {
    slot(width) { modifier ->
        subject(modifier.testTag("subject")) { areaModifier ->
            SwingNode(
                factory = { area },
                modifier = areaModifier,
                update = {
                    set(text) { this.text = it }
                    set(true) {
                        lineWrap = it
                        wrapStyleWord = it
                    }
                },
            )
        }
    }
}

/**
 * A text area counting the line breaks it works out while it wraps its text: each line when it is first laid out,
 * and again on every resize and edit. A break the view rebuilds without either one is not counted.
 */
internal class LineBreakCountingTextArea : JTextArea() {
    var lineBreaks = 0
        private set

    fun resetLineBreaks() {
        lineBreaks = 0
    }

    override fun updateUI() {
        setUI(
            object : BasicTextAreaUI() {
                override fun create(element: Element): View =
                    if (!lineWrap) {
                        super.create(element)
                    } else {
                        object : WrappedPlainView(element, wrapStyleWord) {
                            private val laidOutStarts = mutableSetOf<Int>()
                            private var inLayout = false

                            override fun calculateBreakPosition(
                                start: Int,
                                end: Int,
                            ): Int {
                                val firstLayout = laidOutStarts.add(start)
                                if (firstLayout || inLayout) lineBreaks++
                                return super.calculateBreakPosition(start, end)
                            }

                            override fun setSize(
                                width: Float,
                                height: Float,
                            ) = layingOut { super.setSize(width, height) }

                            override fun insertUpdate(
                                e: DocumentEvent,
                                a: Shape?,
                                f: ViewFactory,
                            ) = edited { super.insertUpdate(e, a, f) }

                            override fun removeUpdate(
                                e: DocumentEvent,
                                a: Shape?,
                                f: ViewFactory,
                            ) = edited { super.removeUpdate(e, a, f) }

                            override fun changedUpdate(
                                e: DocumentEvent,
                                a: Shape?,
                                f: ViewFactory,
                            ) = edited { super.changedUpdate(e, a, f) }

                            private inline fun edited(block: () -> Unit) {
                                laidOutStarts.clear()
                                layingOut(block)
                            }

                            private inline fun layingOut(block: () -> Unit) {
                                val wasInLayout = inLayout
                                inLayout = true
                                try {
                                    block()
                                } finally {
                                    inLayout = wasInLayout
                                }
                            }
                        }
                    }
            },
        )
    }
}

private const val WIDTH = 320

internal class CountingSwingPanel(
    child: JComponent,
) : JPanel(BorderLayout()) {
    var queries = 0
        private set
    var layouts = 0
        private set

    init {
        add(child)
    }

    fun resetCounts() {
        queries = 0
        layouts = 0
    }

    override fun getPreferredSize(): Dimension {
        queries++
        return super.getPreferredSize()
    }

    override fun doLayout() {
        layouts++
        super.doLayout()
    }
}
