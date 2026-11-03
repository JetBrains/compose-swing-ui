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
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
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
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.JViewport
import javax.swing.event.DocumentEvent
import javax.swing.plaf.basic.BasicTextAreaUI
import javax.swing.text.Element
import javax.swing.text.View
import javax.swing.text.ViewFactory
import javax.swing.text.WrappedPlainView
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
        val policy = RecordingMeasurePolicy()
        setContent {
            slot(width) { modifier ->
                Column(modifier = modifier.testTag("container")) {
                    Layout(
                        content = {
                            SwingNode(
                                factory = { CountingLabel() },
                                modifier = SwingModifier.fillMaxWidth(),
                            )
                        },
                        measurePolicy = policy,
                    )
                    SwingNode(
                        factory = { CountingSwingPanel(JLabel("Counted")) },
                        modifier = SwingModifier.fillMaxWidth(),
                    )
                }
            }
        }
        val container = onNodeWithTag("container").fetch<JComponent>()
        val label = onNodeOfType<CountingLabel>().fetch()
        val panel = onNodeOfType<CountingSwingPanel>().fetch()
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
        setContent {
            slot(width) { modifier ->
                Column(modifier = modifier.testTag("column")) {
                    SwingNode(
                        factory = { CountingLabel() },
                        modifier = if (padded) SwingModifier.padding(4) else SwingModifier,
                    )
                    Label(sibling)
                }
            }
        }
        val column = onNodeWithTag("column").fetch<JComponent>()
        val label = onNodeOfType<CountingLabel>().fetch()
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
        fun preferredWidthHistories(): List<Arguments> {
            val width = 320
            return listOf(BorderLayout.NORTH, BorderLayout.SOUTH).flatMap { region ->
                listOf(listOf(100, 160, width), listOf(160, width)).map {
                    Arguments.of(region, Named.of(it.joinToString(" then "), it))
                }
            }
        }

        private fun settlingOf(
            slotName: String,
            slot: Slot,
            afterSettling: ComposeSwingTest.(stepName: String, areaHeight: Int) -> Unit = { _, _ -> },
            subject: @Composable (SwingModifier, @Composable (SwingModifier) -> Unit) -> Unit,
        ): List<Settled> {
            val steps = mutableListOf<Settled>()
            runComposeSwingTest {
                var width by mutableIntStateOf(WIDTH)
                var text by mutableStateOf(WRAPPING_TEXT)
                setContent {
                    SettlingContent(slot, width, text, subject)
                }
                val component = onNodeWithTag("subject").fetch<JComponent>()
                val area = onNodeOfType<LineBreakCountingTextArea>().fetch()
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
) {
    slot(width) { modifier ->
        subject(modifier.testTag("subject")) { areaModifier ->
            SwingNode(
                factory = { LineBreakCountingTextArea() },
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
