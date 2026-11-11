// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.withRecordedRepaints
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
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
 * A parent that sets the container's width before reading preferences, or its height after the first layout, has it at
 * that size at once.
 */
class SwingParentParityTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("slots")
    fun aRowOfIntrinsicHeightHoldingAWrappingTextAreaCostsNoMoreThanTheSameRowWithoutItInEachSlot(name: String) {
        val slot = Slots.getValue(name)
        val rows =
            listOf(false, true).map { intrinsic ->
                settlingOf(
                    name,
                    slot,
                    afterSettling = { stepName, areaHeight ->
                        if (intrinsic) {
                            onNodeWithTag("row")
                                .assertProperty(areaHeight, "$name, $stepName: row holds area") { height }
                        }
                    },
                ) { modifier, area ->
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
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("paintReadSlots")
    fun widthIndependentChildrenSettleWithoutPaintOrRepeatedPreferredSizeQueries(
        name: String,
        firstPaintPreferredReads: Int,
        widthChangePreferredReads: Int,
    ) = runComposeSwingTest {
        val setsWidth = name in WidthSettingSlots
        val stacksHeights = name.startsWith("BorderLayout")
        val slot = Slots.getValue(name)
        var width by mutableIntStateOf(WIDTH)
        val label = CountingLabel()
        val panel = CountingSwingPanel(JLabel("Counted"))
        val policy = RecordingMeasurePolicy()
        setContent {
            slot(width) { modifier ->
                Column(modifier = modifier.testTag("container")) {
                    Layout(
                        content = {
                            SwingNode(
                                factory = { label },
                                modifier = SwingModifier.fillMaxWidth(),
                            )
                        },
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
        label.resetCounts()
        for (step in 1..5) {
            width = WIDTH - 20 * step
            awaitIdle()
            assertTrue(
                label.preferredSizeQueries <= widthChangePreferredReads,
                "$name: width $width reads label preferred size at most $widthChangePreferredReads times " +
                    "(got ${label.preferredSizeQueries})",
            )
            if (stacksHeights) {
                assertEquals(label.height + panel.height, container.height, "$name: combined height")
            }
            val bounds = container.bounds
            val passes = policy.measures
            label.resetCounts()
            policy.resetQueries()
            panel.resetCounts()
            assertEquals(0, cyclesUntilStable(container), "$name: no corrective paint at $width")
            assertEquals(bounds, container.bounds, "$name: paint keeps bounds")
            if (setsWidth) {
                assertEquals(passes, policy.measures, "$name: first paint measures nothing")
                policy.assertPaintSizeReads(name, label, panel, "first", allowed = firstPaintPreferredReads)
                assertSettledPaintSizeReads(name, container, policy, label, panel)
            }
        }
    }

    private fun RecordingMeasurePolicy.assertPaintSizeReads(
        name: String,
        label: CountingLabel,
        panel: CountingSwingPanel,
        phase: String,
        allowed: Int,
    ) {
        assertTrue(
            preferredHeightQueries <= allowed,
            "$name: $phase paint reads nested preferred height at most $allowed times (got $preferredHeightQueries)",
        )
        assertTrue(
            label.preferredSizeQueries <= allowed,
            "$name: $phase paint reads label preferred size at most $allowed times (got ${label.preferredSizeQueries})",
        )
        assertEquals(0, preferredWidthQueries, "$name: $phase paint reads no nested preferred width")
        assertEquals(0, panel.queries, "$name: $phase paint reads no panel preferred size")
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
        policy.assertPaintSizeReads(name, label, panel, phase = "settled", allowed = 0)
    }

    @ParameterizedTest(name = "{0}, padded={1}")
    @MethodSource("paddedSlots")
    fun aStockChildAColumnDoesNotStretchIsNeitherMovedNorResizedWhenItsSiblingOrItsSlotChanges(
        name: String,
        padded: Boolean,
    ) = runComposeSwingTest {
        val slot = Slots.getValue(name)
        val case = if (padded) "$name, padded" else name
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

    /**
     * The stock parent reads its preferred size before it grants the slot width. The child lays out at the grant;
     * paint does not query preferences or revise the parent's chosen bounds.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("grantCases")
    internal fun aContainerUnderAStockParentKeepsTheParentGrantUntilItIsValidatedAgain(case: GrantCase) =
        runComposeSwingTest {
            val policy = RecordingMeasurePolicy()
            setContent {
                case.slot(WIDTH) { modifier ->
                    Layout(
                        content = {
                            Label("<html>$WRAPPING_TEXT</html>", modifier = case.textModifier(this).testTag("text"))
                        },
                        modifier = modifier.testTag("container"),
                        measurePolicy = policy,
                    )
                }
            }
            val container = onNodeWithTag("container").fetch<JComponent>()
            val initialHeight = container.height
            assertEquals(listOf(initialHeight), policy.heights.distinct(), "${case.name}: laid out at the first bounds")
            assertEquals(Dimension(WIDTH, initialHeight), container.size, "${case.name}: the parent grant")
            if (case.followsRatio) {
                onNodeWithTag("text").assertProperty(
                    WIDTH / 2,
                    "${case.name}: the child follows its ratio at the granted width",
                ) { height }
            }
            val bounds = container.bounds
            withRecordedRepaints { recorder ->
                container.captureToImage()
                recorder.assertAskedForNoLayout(container, "${case.name}: paint does not correct the parent grant")
            }
            assertEquals(bounds, container.bounds, "${case.name}: paint preserves the parent grant")
            assertEquals(
                listOf(initialHeight),
                policy.heights.distinct(),
                "${case.name}: paint does not remeasure",
            )
        }

    /**
     * This parent can read preferences or grant its own size. The container measures each grant at the bounds it
     * receives; painting does not revise the parent's choice.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("heightGrants")
    fun aContainerAStockParentSetsTheHeightOfAfterItsFirstLayoutIsLaidOutAtThatHeight(
        name: String,
        granted: Int,
        readsPreferences: Boolean,
    ) = runComposeSwingTest {
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

    private fun scripted(
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

    /**
     * The container prefers its width at a 16:9 ratio. Laid out once at the region's width when it prefers that
     * width, it is still read at the region's width once it prefers more.
     */
    @ParameterizedTest(name = "BorderLayout {0}, preferring {1}")
    @MethodSource("preferredWidthHistories")
    fun aContainerWhoseStockParentSetsItsWidthBeforeReadingPreferencesProvidesItsHeightThereInTheSameValidation(
        region: String,
        widths: List<Int>,
    ) = runComposeSwingTest {
        val name = "BorderLayout $region, preferring ${widths.joinToString(" then ")}"
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
        for (width in widths) {
            preferred = width
            awaitIdle()
            onNodeWithTag("container").assertProperty(
                Dimension(160, 90),
                "$name: preferring $width, the ratio at the region's width in the first validation",
            ) { size }
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
    @ParameterizedTest(name = "{0}")
    @MethodSource("eastLeaves")
    fun wrappingContentUnderBorderLayoutEastFollowsNativeWidths(kind: String) {
        val leaf = EastLeaves.getValue(kind)
        val plain = eastCycles(leaf, inColumn = false).drop(1)
        val column = eastCycles(leaf, inColumn = true).drop(1)
        assertTrue(plain.all { it.paintRequestsLayout }, "$kind: each raw paint requests layout")
        assertTrue(
            plain.zipWithNext().all { (before, after) -> before.width != after.width },
            "$kind: raw widths change",
        )
        assertEquals(plain.map { it.width }, column.map { it.width }, "$kind: same widths as raw Swing")
        assertEquals(
            plain.map { it.paintRequestsLayout },
            column.map { it.paintRequestsLayout },
            "$kind: same paint requests",
        )
    }

    @Test
    fun htmlUnderBorderLayoutEastPreservesNativePaintRequestsAndValidationCost() {
        val lazy = eastCycles(::RowCountingHtmlLabel, inColumn = false, samePass = false)
        val completed = eastCycles(::RowCountingHtmlLabel, inColumn = false, samePass = true)
        val column = eastCycles(::RowCountingHtmlLabel, inColumn = true, samePass = true)
        assertEquals(completed.map { it.width }, lazy.map { it.width }, "lazy raw widths match native completion")
        assertEquals(completed.map { it.width }, column.map { it.width }, "Column widths match native completion")
        assertEquals(
            List(PrescribedWidths.lastIndex) { false },
            column.drop(1).map { it.paintRequestsLayout },
            "Column paints do not reconcile size preferences",
        )
        assertEquals(
            List(PrescribedWidths.lastIndex) { true },
            lazy.drop(1).map { it.paintRequestsLayout },
            "lazy raw paint requests",
        )
        assertEquals(
            List(PrescribedWidths.lastIndex) { false },
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
        assertEquals(
            completed.last().rows,
            column.last().rows,
            "Column completes the native rows at the final width",
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("htmlRegions")
    internal fun htmlCompletionAtPrescribedWidthsCostsNoMoreRowsThanNative(
        region: String,
        completesNativeRows: Boolean,
        nativeAtWidths: (SwingParentParityTest, List<Int>) -> List<MarkupCycle>,
        columnAtWidths: (SwingParentParityTest, List<Int>) -> List<MarkupCycle>,
    ) {
        val native = nativeAtWidths(this, PrescribedWidths)
        val column = columnAtWidths(this, PrescribedWidths)
        assertEquals(PrescribedWidths, native.map { it.width }, "native $region grants the prescribed widths")
        assertEquals(PrescribedWidths, column.map { it.width }, "Column $region grants the prescribed widths")
        var columnRows = 0
        var nativeRows = 0
        for ((actual, control) in column.zip(native)) {
            assertTrue(
                actual.rows - columnRows <= control.rows - nativeRows,
                "row cost through paint and resize to ${control.width}: $actual vs native $control",
            )
            assertEquals(control.markupHeight, actual.markupHeight, "native $region completion at ${control.width}")
            columnRows = actual.rows
            nativeRows = control.rows
        }
        if (completesNativeRows) {
            assertEquals(
                native.last().rows,
                column.last().rows,
                "Column completes the native rows at the final width",
            )
        }
    }

    @Test
    fun scriptedPreferredSizeQueriesFollowTheEntirePrescribedWidthHistory() {
        val leaf = {
            object : JComponent() {
                override fun getPreferredSize(): Dimension {
                    val nextWidth =
                        when (width) {
                            0 -> PrescribedWidths[0]
                            PrescribedWidths[0] -> PrescribedWidths[1]
                            PrescribedWidths[1] -> PrescribedWidths[2]
                            PrescribedWidths[2] -> PrescribedWidths[3]
                            PrescribedWidths[3] -> PrescribedWidths[4]
                            else -> error("Unexpected granted width: $width")
                        }
                    return Dimension(1, nextWidth / 2)
                }
            }
        }
        assertEquals(
            PrescribedWidths,
            eastCycles(leaf, inColumn = false).map { it.width },
            "precondition: native EAST grants the scripted widths",
        )
        assertEquals(
            PrescribedWidths,
            eastCycles(leaf, inColumn = true).map { it.width },
            "Column: all granted widths",
        )
    }

    /**
     * One step of a scenario: the cycles it took to settle, the height the subject settled at, the height of the text
     * area, and the line breaks a counted text area worked out.
     */
    private data class Settled(
        val name: String,
        val cycles: Int,
        val height: Int,
        val areaHeight: Int,
        val lineBreaks: Int,
    )

    internal data class MarkupCycle(
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

    private fun nativeEastHtmlAtWidths(widths: List<Int>): List<MarkupCycle> =
        eastHtmlAtWidths(widths) { modifier, width, child ->
            SwingNode(factory = {
                JPanel(
                    SwingParentLayout(preferred = { Dimension() }) { parent ->
                        val component = parent.getComponent(0)
                        component.setBounds(0, 0, parent.width, parent.width / 2)
                        component.getBaseline(component.width, component.height)
                    },
                ).apply { add(child) }
            }, modifier = modifier.styleable { preferredSize(width, width / 2) })
        }

    private fun columnEastHtmlAtWidths(widths: List<Int>): List<MarkupCycle> =
        eastHtmlAtWidths(widths) { modifier, width, child ->
            Column(modifier = modifier.styleable { preferredSize(width, width / 2) }) {
                SwingNode(factory = { child }, modifier = SwingModifier.fillMaxWidth().aspectRatio(2f))
            }
        }

    private fun eastHtmlAtWidths(
        widths: List<Int>,
        subject: @Composable (SwingModifier, Int, RowCountingHtmlLabel) -> Unit,
    ): List<MarkupCycle> {
        val cycles = mutableListOf<MarkupCycle>()
        runComposeSwingTest {
            val child = RowCountingHtmlLabel()
            var width by mutableIntStateOf(widths.first())
            setContent {
                Panel(PanelLayout.Border(), style = { preferredSize(1000, 600) }) {
                    subject(
                        SwingModifier
                            .layoutConstraint(BorderLayout.EAST)
                            .testTag("subject"),
                        width,
                        child,
                    )
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

    /** The native control is a standalone label sized at each width, as an independent oracle. */
    private fun nativeNorthHtmlAtWidths(widths: List<Int>): List<MarkupCycle> {
        val cycles = mutableListOf<MarkupCycle>()
        runComposeSwingTest {
            val label = RowCountingHtmlLabel()
            for (width in widths) {
                label.setSize(width, width / 2)
                label.getBaseline(width, width / 2)
                cycles += MarkupCycle(label.width, false, 0, label.rows, label.markup.getPreferredSpan(View.Y_AXIS))
            }
        }
        return cycles
    }

    private fun columnNorthHtmlAtWidths(widths: List<Int>): List<MarkupCycle> {
        val cycles = mutableListOf<MarkupCycle>()
        runComposeSwingTest {
            val label = RowCountingHtmlLabel()
            var width by mutableIntStateOf(widths.first())
            setContent {
                borderLayoutSlot(BorderLayout.NORTH)(width) { modifier ->
                    Column(modifier = modifier) {
                        SwingNode(factory = { label }, modifier = SwingModifier.fillMaxWidth().aspectRatio(2f))
                    }
                }
            }
            for (offered in widths) {
                width = offered
                awaitIdle()
                cycles += MarkupCycle(label.width, false, 0, label.rows, label.markup.getPreferredSpan(View.Y_AXIS))
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
                SwingNode(
                    factory = { host },
                    modifier = SwingModifier.styleable { preferredSize(320, 600) },
                ) {
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
            repeat(PrescribedWidths.lastIndex) {
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

    companion object {
        /** The text needs the bar at every width in a viewport 40 or 60 high, and at none from 150 up. */
        private val Slots: Map<String, Slot> = swingParentSlots(listOf(40, 60, 150, 175, 200))

        private val PrescribedWidths = listOf(32, 864, 64, 800, 64)

        private val EastLeaves: Map<String, () -> JComponent> =
            mapOf(
                "JTextArea" to ::wrappingTextArea,
                "JPanel holding a JTextArea" to { JPanel(BorderLayout()).apply { add(wrappingTextArea()) } },
                "JEditorPane showing HTML" to { JEditorPane("text/html", "<html>$WRAPPING_TEXT</html>") },
            )

        private fun wrappingTextArea() =
            JTextArea(WRAPPING_TEXT).apply {
                lineWrap = true
                wrapStyleWord = true
            }

        @JvmStatic
        fun slots(): List<Arguments> = Slots.keys.map { Arguments.of(it) }

        @JvmStatic
        fun paddedSlots(): List<Arguments> =
            Slots.keys.flatMap { name -> listOf(false, true).map { Arguments.of(name, it) } }

        /**
         * Per slot: how often the label's preferred size may be read during a first paint and while a width change
         * settles. Slots that do not set the width only bound the reads of a width change.
         */
        @JvmStatic
        fun paintReadSlots(): List<Arguments> =
            Slots.keys.map { name ->
                when (name) {
                    "BorderLayout NORTH", "BorderLayout SOUTH" -> Arguments.of(name, 0, 3)
                    else -> Arguments.of(name, 1, 2)
                }
            }

        @JvmStatic
        fun htmlRegions(): List<Arguments> =
            listOf(
                Arguments.of(
                    BorderLayout.EAST,
                    true,
                    SwingParentParityTest::nativeEastHtmlAtWidths,
                    SwingParentParityTest::columnEastHtmlAtWidths,
                ),
                Arguments.of(
                    BorderLayout.NORTH,
                    false,
                    SwingParentParityTest::nativeNorthHtmlAtWidths,
                    SwingParentParityTest::columnNorthHtmlAtWidths,
                ),
            )

        @JvmStatic
        fun grantCases(): List<Arguments> {
            val squeezedHeight =
                JLabel("<html>$WRAPPING_TEXT</html>").run {
                    setSize(minimumSize.width, SLOT_HEIGHT)
                    getBaseline(width, height)
                    preferredSize.height
                }
            val weightedGridBag: Slot = { width, subject ->
                Panel(PanelLayout.Border(), style = { preferredSize(width, SLOT_HEIGHT) }) {
                    Panel(
                        PanelLayout.GridBag,
                        modifier = SwingModifier.north(),
                        style = { preferredSize(width, squeezedHeight) },
                    ) {
                        subject(SwingModifier.item(weightx = 1.0, weighty = 1.0, fill = GridBagConstraints.HORIZONTAL))
                    }
                }
            }
            val fillWidth: ConstrainedScope.() -> SwingModifier = { SwingModifier.fillMaxWidth() }
            val ratio: ConstrainedScope.() -> SwingModifier = { SwingModifier.aspectRatio(2f) }
            val gridBagFill = Slots.getValue("GridBagLayout fill HORIZONTAL")
            return listOf(
                GrantCase("BoxLayout Y_AXIS", Slots.getValue("BoxLayout Y_AXIS"), fillWidth, followsRatio = false),
                GrantCase("GridBagLayout, fill", gridBagFill, fillWidth, followsRatio = false),
                GrantCase("weighted GridBagLayout, fill", weightedGridBag, fillWidth, followsRatio = false),
                GrantCase("GridBagLayout, ratio", gridBagFill, ratio, followsRatio = true),
                GrantCase("weighted GridBagLayout, ratio", weightedGridBag, ratio, followsRatio = true),
            ).map { Arguments.of(Named.of(it.name, it)) }
        }

        @JvmStatic
        fun heightGrants(): List<Arguments> =
            listOf(
                Arguments.of("without reading preferences, the minimum height returned", 40, false),
                Arguments.of("without reading preferences, the parent's preferred height", 50, false),
                Arguments.of("reading preferences, below the minimum", 30, true),
            )

        @JvmStatic
        fun preferredWidthHistories(): List<Arguments> =
            listOf(BorderLayout.NORTH, BorderLayout.SOUTH).flatMap { region ->
                listOf(listOf(100, 160, WIDTH), listOf(160, WIDTH)).map {
                    Arguments.of(region, Named.of(it.joinToString(" then "), it))
                }
            }

        @JvmStatic
        fun eastLeaves(): List<Arguments> = EastLeaves.keys.map { Arguments.of(it) }

        private fun settlingOf(
            slotName: String,
            slot: Slot,
            afterSettling: ComposeSwingTest.(stepName: String, areaHeight: Int) -> Unit = { _, _ -> },
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
                    afterSettling(name, area.height)
                    steps += Settled(name, cycles, component.height, area.height, area.lineBreaks)
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

/** A slot, and the modifier a text in it declares; [followsRatio] when that modifier sets the text's aspect ratio. */
internal class GrantCase(
    val name: String,
    val slot: Slot,
    val textModifier: ConstrainedScope.() -> SwingModifier,
    val followsRatio: Boolean,
)

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
