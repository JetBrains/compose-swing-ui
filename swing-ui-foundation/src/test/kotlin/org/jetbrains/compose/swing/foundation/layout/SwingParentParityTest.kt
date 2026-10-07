package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.layout.PanelScope
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.SwingNodeUpdater
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.withRecordedRepaints
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.LayoutManager
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.JViewport
import javax.swing.plaf.basic.BasicTextAreaUI
import javax.swing.text.Element
import javax.swing.text.View
import javax.swing.text.WrappedPlainView
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A constraint-based container in a slot of a Swing layout manager, against a wrapping text area in that same slot: the
 * container needs no more validate-and-paint cycles to settle than the text area does, and settles at the same height.
 * Where the slot stretches the container, the container lays its content out only at the height it settles at. A parent
 * that sets the container's width before asking, or its height after the first layout, has it at that size at once.
 */
class SwingParentParityTest {
    @Test
    fun aContainerHoldingAWrappingTextAreaSettlesNoLaterThanTheTextAreaInTheSameSlot() {
        for ((name, slot) in Slots) {
            val raw = settlingOf(slot) { modifier, area -> area(modifier) }
            val foundation =
                settlingOf(slot) { modifier, area ->
                    Column(modifier = modifier) { area(SwingModifier.fillMaxWidth()) }
                }
            for ((step, rawStep) in raw.withIndex()) {
                val foundationStep = foundation[step]
                assertTrue(
                    foundationStep.cycles <= rawStep.cycles,
                    "$name, ${rawStep.name}: the container settles in ${foundationStep.cycles} cycles, " +
                        "the text area in ${rawStep.cycles}",
                )
                assertEquals(rawStep.height, foundationStep.height, "$name, ${rawStep.name}: at the same height")
            }
        }
    }

    @Test
    fun aRowOfIntrinsicHeightHoldingAWrappingTextAreaCostsNoMoreThanTheSameRowWithoutItInEachSlot() {
        for ((name, slot) in Slots) {
            val rows =
                listOf(false, true).map { intrinsic ->
                    settlingOf(slot, counted = true) { modifier, area ->
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
                settlingOf(slot, counted = true) { modifier, area ->
                    Column(modifier = modifier) { area(SwingModifier.fillMaxWidth()) }
                }
            assertTrue(
                rows[1][0].lineBreaks <= column[0].lineBreaks,
                "$name: on first show the area in the row of intrinsic height works out ${rows[1][0].lineBreaks} " +
                    "line breaks, in a column ${column[0].lineBreaks}",
            )
            for ((step, plainStep) in rows[0].withIndex()) {
                val intrinsicStep = rows[1][step]
                val what = "$name, ${plainStep.name}"
                assertTrue(
                    intrinsicStep.lineBreaks <= plainStep.lineBreaks,
                    "$what: the area in the row of intrinsic height works out ${intrinsicStep.lineBreaks} line " +
                        "breaks, in the plain row ${plainStep.lineBreaks}",
                )
                assertTrue(
                    intrinsicStep.cycles <= plainStep.cycles,
                    "$what: the row of intrinsic height settles in ${intrinsicStep.cycles} cycles, " +
                        "the plain row in ${plainStep.cycles}",
                )
                assertEquals(plainStep.areaHeight, intrinsicStep.areaHeight, "$what: the area takes the same height")
                assertEquals(intrinsicStep.areaHeight, intrinsicStep.rowHeight, "$what: the row holds the area")
            }
        }
    }

    @Test
    fun aRowOfIntrinsicHeightResizesEachOfTwoWeightedAreasAtMostOncePerStepOfADragAcrossOddAndEvenWidths() =
        runComposeSwingTest {
            var width by mutableIntStateOf(WIDTH)
            val areas = List(2) { WidthLoggingTextArea() }
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(width, 600)) {
                    Column(modifier = SwingModifier.north().testTag("column")) {
                        Row(modifier = SwingModifier.height(IntrinsicSize.Min)) {
                            for (area in areas) SwingNode(factory = { area }, modifier = SwingModifier.weight(1f))
                        }
                    }
                }
            }
            val column = onNodeWithTag("column").fetch<JComponent>()
            cyclesUntilStable(column)
            for (step in 1..5) {
                width = WIDTH + step
                areas.forEach { it.widths.clear() }
                awaitIdle()
                cyclesUntilStable(column)
                for ((index, area) in areas.withIndex()) {
                    assertTrue(area.widths.size <= 1, "at $width, area $index takes the widths ${area.widths}")
                }
                assertEquals(width, areas.sumOf { it.width }, "at $width, the two areas share the row")
            }
        }

    @Test
    fun aContainerHoldingARatioChildUnderBorderLayoutNorthIsLaidOutOncePerStepOfAResizeDrag() =
        runComposeSwingTest {
            var width by mutableIntStateOf(WIDTH)
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(width, 600)) {
                    Column(modifier = SwingModifier.north().testTag("column")) {
                        Box(modifier = SwingModifier.testTag("box")) {
                            Label(
                                "Preview",
                                modifier = SwingModifier.testTag("label").fillMaxWidth().aspectRatio(16f / 9f),
                            )
                        }
                    }
                }
            }
            val column = onNodeWithTag("column").fetch<JComponent>()
            val box = onNodeWithTag("box").fetch<JComponent>()
            val label = onNodeWithTag("label").fetch<JComponent>()
            for (step in 0..5) {
                width = WIDTH - 20 * step
                awaitIdle()
                val atRatio = Dimension(width, (width * 9f / 16f).roundToInt())
                assertEquals(atRatio, column.size, "the ratio at $width")
                assertEquals(atRatio, box.size, "to the box at $width")
                assertEquals(atRatio, label.size, "and to the label at $width")
                assertEquals(0, cyclesUntilStable(column), "and no validation after the paint at $width")
            }
        }

    @Test
    fun aContainerWhoseHeightDoesNotFollowItsWidthSettlesInEachSlotWithoutAPaint() {
        for ((name, slot) in Slots) {
            runComposeSwingTest {
                var width by mutableIntStateOf(WIDTH)
                val label = CountingLabel()
                val policy = HeightCountingPolicy()
                setContent {
                    slot(width) { modifier ->
                        Layout(
                            content = { SwingNode(factory = { label }, modifier = SwingModifier.fillMaxWidth()) },
                            modifier = modifier.testTag("container"),
                            measurePolicy = policy,
                        )
                    }
                }
                val container = onNodeWithTag("container").fetch<JComponent>()
                assertEquals(0, cyclesUntilStable(container), "$name: first show")
                for (step in 1..5) {
                    width = WIDTH - 20 * step
                    awaitIdle()
                    label.queries = 0
                    policy.questions = 0
                    assertEquals(0, cyclesUntilStable(container), "$name: drag to $width")
                    if (name in WidthSettingSlots) {
                        assertEquals(0, policy.questions, "$name: the paint at $width asks the container nothing")
                        assertEquals(0, label.queries, "$name: nor the label")
                    }
                }
            }
        }
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
                        label.resizes = 0
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
    fun aContainerAStockParentStretchesLaysItsContentOutOnlyAtTheHeightItSettlesAt() {
        // Asked before its first layout, the text is one line as wide as itself. BoxLayout grants the preferred height
        // that answer gives; GridBagLayout, short of the preferred width, the minimum height at the minimum width, or,
        // shorter than that minimum, a height between it and its own.
        val weightedGridBag: Slot = { width, subject ->
            Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(width, SLOT_HEIGHT)) {
                Panel(PanelLayout.GridBag, modifier = SwingModifier.north().preferredSize(width, SQUEEZED_HEIGHT)) {
                    subject(SwingModifier.item(weightx = 1.0, weighty = 1.0, fill = GridBagConstraints.HORIZONTAL))
                }
            }
        }
        val fillWidth: ConstrainedScope.() -> SwingModifier = { SwingModifier.fillMaxWidth() }
        val cases: List<Triple<String, Slot, ConstrainedScope.() -> SwingModifier>> =
            listOf(
                Triple("BoxLayout Y_AXIS, filling the width", Slots.getValue("BoxLayout Y_AXIS"), fillWidth),
                Triple(
                    "GridBagLayout fill HORIZONTAL, at a ratio",
                    Slots.getValue("GridBagLayout fill HORIZONTAL"),
                    { SwingModifier.aspectRatio(2f) },
                ),
                Triple(
                    "GridBagLayout fill HORIZONTAL, filling the width",
                    Slots.getValue("GridBagLayout fill HORIZONTAL"),
                    fillWidth,
                ),
                Triple("GridBagLayout fill HORIZONTAL $SQUEEZED_HEIGHT high, weighted", weightedGridBag, fillWidth),
            )
        for ((name, slot, textModifier) in cases) {
            runComposeSwingTest {
                val policy = HeightCountingPolicy()
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
                cyclesUntilStable(container)

                val text = onNodeWithTag("text").fetch<JComponent>()
                assertEquals(Dimension(WIDTH, text.height), container.size, "$name: the slot grants the text's height")
                assertEquals(listOf(text.height), policy.heights.distinct(), "$name: the only height laid out at")
            }
        }
    }

    @Test
    fun aContainerAStockParentSetsTheHeightOfAfterItsFirstLayoutIsLaidOutAtThatHeight() {
        // Answered at 100 wide, the container prefers 60 high and needs 40. Laid out at 120 wide without a question, it
        // has dropped those answers, and a paint finds it prefers 50 there. A height granted later at 160 wide without
        // a question is the parent's own, even where it equals one of those; so is a height below the minimum the
        // parent asks for, at the width the container holds when asked.
        val resizes =
            listOf(
                Triple("without asking, the minimum height answered", 40, false),
                Triple("without asking, the preferred height a paint found", 50, false),
                Triple("asking, below the minimum", 30, true),
            )
        for ((name, granted, asks) in resizes) {
            runComposeSwingTest {
                var layout by mutableStateOf(Scripted(Dimension(100, 90), asks = true))
                val policy = AreaPolicy()
                setContent {
                    Panel(layout) {
                        Layout(content = {}, modifier = SwingModifier.testTag("container"), measurePolicy = policy)
                    }
                }
                val container = onNodeWithTag("container").fetch<JComponent>()
                cyclesUntilStable(container)
                layout = Scripted(Dimension(120, 90), asks = false)
                awaitIdle()
                cyclesUntilStable(container)
                policy.heights.clear()

                layout = Scripted(Dimension(160, granted), asks)
                awaitIdle()
                cyclesUntilStable(container)
                assertEquals(Dimension(160, granted), container.size, "$name: precondition: the parent's own size")
                assertEquals(listOf(granted), policy.heights.distinct(), "$name: the only height laid out at")
            }
        }
    }

    @Test
    fun aContainerAStockParentSetsTheWidthOfBeforeAskingAnswersTheHeightThereInTheSameValidation() {
        // The container prefers its width at a 16:9 ratio. Laid out once at the region's width when it prefers that
        // width, it is still asked at the region's width once it prefers more.
        for (region in listOf("NORTH", "SOUTH")) {
            for (widths in listOf(listOf(100, NARROW, WIDTH), listOf(NARROW, WIDTH))) {
                val name = "BorderLayout $region, preferring ${widths.joinToString(" then ")}"
                runComposeSwingTest {
                    var preferred by mutableIntStateOf(widths.first())
                    setContent {
                        Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(NARROW, SLOT_HEIGHT)) {
                            Layout(
                                content = {},
                                modifier =
                                    (if (region == "NORTH") SwingModifier.north() else SwingModifier.south())
                                        .testTag("container"),
                                measurePolicy = remember(preferred) { RatioPolicy(preferred) },
                            )
                        }
                    }
                    val container = onNodeWithTag("container").fetch<JComponent>()
                    for (width in widths) {
                        preferred = width
                        awaitIdle()
                        assertEquals(
                            Dimension(NARROW, NARROW * 9 / 16),
                            container.size,
                            "$name: preferring $width, the ratio at the region's width in the first validation",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun aContainerAskedItsPreferredSizeWhileItValidatesItsChildrenKeepsItsStockChildrenAtTheWidthItsLayoutGaveThem() =
        runComposeSwingTest {
            setContent {
                Panel(PanelLayout.Flow()) {
                    Column {
                        Label("A label wider than the narrowed one")
                        Label("Narrowed label", modifier = SwingModifier.testTag("narrowed").width(NARROWED))
                        SwingNode(factory = { JPanel(ParentAskingLayout()) })
                    }
                }
            }
            awaitIdle()

            assertEquals(
                NARROWED,
                onNodeWithTag("narrowed").fetch<JComponent>().width,
                "the column's layout already ran, so a question its validation leads to resizes no stock child",
            )
        }

    /**
     * Pins behavior that differs from androidx. A Column answers its width from the height of a wrapping `JTextArea` at
     * an aspect ratio, asked at an unbounded width, which a stock component answers at the width it holds. So each
     * width `BorderLayout` east grants changes the answer, and each validation lays the Column out at another width, at
     * the widths the same layout takes in plain Swing. Each paint asks for that validation, as in plain Swing. androidx
     * asks an interop View that height with an `UNSPECIFIED` width spec (`AndroidViewHolder.android.kt` in
     * `compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/viewinterop`), and a wrapping text answers it on one
     * line. Aligning with androidx turns this test red; it should then assert that the Column keeps twice the height of
     * one line as its width, and that a paint asks for no validation.
     */
    @Test
    fun aColumnSizedByTheHeightOfAWrappingTextAreaUnderBorderLayoutEastTakesAnotherWidthAfterEachPaintAsInPlainSwing() =
        assertLaidOutAtTheWidthsOfPlainSwing(paintAsks = true) {
            JTextArea(WRAPPING_TEXT).apply {
                lineWrap = true
                wrapStyleWord = true
            }
        }

    /**
     * Pins behavior that differs from androidx. A Column answers its width from the height of a `JPanel` holding a
     * wrapping `JTextArea` at an aspect ratio, asked at an unbounded width, which a stock component answers at the
     * width it holds. So each width `BorderLayout` east grants changes the answer, and each validation lays the Column
     * out at another width, at the widths the same layout takes in plain Swing. Each paint asks for that validation, as
     * in plain Swing. androidx asks an interop View that height with an `UNSPECIFIED` width spec
     * (`AndroidViewHolder.android.kt` in `compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/viewinterop`), and a
     * wrapping text answers it on one line. Aligning with androidx turns this test red; it should then assert that the
     * Column keeps twice the height of one line as its width, and that a paint asks for no validation.
     */
    @Test
    fun aColumnSizedByTheHeightOfAPanelHoldingAWrappingTextAreaUnderBorderLayoutEastTakesAnotherWidthAfterEachPaint() =
        assertLaidOutAtTheWidthsOfPlainSwing(paintAsks = true) {
            JPanel(BorderLayout()).apply {
                add(
                    JTextArea(WRAPPING_TEXT).apply {
                        lineWrap = true
                        wrapStyleWord = true
                    },
                )
            }
        }

    /**
     * Pins behavior that differs from androidx. A Column answers its width from the height of a `JLabel` showing HTML
     * at an aspect ratio, asked at an unbounded width, which a stock component answers at the width it holds. So each
     * width `BorderLayout` east grants changes the answer, and each validation lays the Column out at another width, at
     * the widths the same layout takes in plain Swing. Answering that height lays the HTML out at the width the label
     * is then painted at, so its paint asks for no validation, where it does in plain Swing. androidx asks an interop
     * View that height with an `UNSPECIFIED` width spec (`AndroidViewHolder.android.kt` in
     * `compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/viewinterop`), and a wrapping text answers it on one
     * line. Aligning with androidx turns this test red; it should then assert that the Column keeps twice the height of
     * one line as its width, and that a paint asks for no validation.
     */
    @Test
    fun aColumnSizedByTheHeightOfAnHtmlLabelUnderBorderLayoutEastTakesAnotherWidthAtEachValidationAsInPlainSwing() =
        assertLaidOutAtTheWidthsOfPlainSwing(paintAsks = false) { JLabel("<html>$WRAPPING_TEXT</html>") }

    /**
     * Pins behavior that differs from androidx. A Column answers its width from the height of a `JEditorPane` showing
     * HTML at an aspect ratio, asked at an unbounded width, which a stock component answers at the width it holds. So
     * each width `BorderLayout` east grants changes the answer, and each validation lays the Column out at another
     * width, at the widths the same layout takes in plain Swing. Each paint asks for that validation, as in plain
     * Swing. androidx asks an interop View that height with an `UNSPECIFIED` width spec (`AndroidViewHolder.android.kt`
     * in `compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/viewinterop`), and a wrapping text answers it on one
     * line. Aligning with androidx turns this test red; it should then assert that the Column keeps twice the height of
     * one line as its width, and that a paint asks for no validation.
     */
    @Test
    fun aColumnSizedByTheHeightOfAnHtmlEditorPaneUnderBorderLayoutEastTakesAnotherWidthAfterEachPaintAsInPlainSwing() =
        assertLaidOutAtTheWidthsOfPlainSwing(paintAsks = true) {
            JEditorPane("text/html", "<html>$WRAPPING_TEXT</html>")
        }

    /**
     * One step of a scenario: the cycles it took to settle, the height the subject settled at, the heights of the text
     * area and of the container tagged "row", where there is one, and the line breaks a counted text area worked out.
     */
    private class Settled(
        val name: String,
        val cycles: Int,
        val height: Int,
        val areaHeight: Int,
        val rowHeight: Int,
        val lineBreaks: Int,
    )

    /**
     * Lays its one child out at the width it is offered, counting the intrinsic height questions it is asked and
     * recording each bounded height it is measured at.
     */
    private class HeightCountingPolicy : MeasurePolicy {
        var questions = 0
        val heights = mutableListOf<Int>()

        override fun MeasureScope.measure(
            measurables: List<Measurable>,
            constraints: Constraints,
        ): MeasureResult {
            if (constraints.hasBoundedHeight) heights += constraints.maxHeight
            val placeable = measurables.single().measure(constraints)
            return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }

        override fun IntrinsicMeasureScope.maxIntrinsicWidth(
            measurables: List<IntrinsicMeasurable>,
            height: Int,
        ): Int = measurables.single().maxIntrinsicWidth(height)

        override fun IntrinsicMeasureScope.maxIntrinsicHeight(
            measurables: List<IntrinsicMeasurable>,
            width: Int,
        ): Int {
            questions++
            return measurables.single().maxIntrinsicHeight(width)
        }
    }

    /**
     * An area of 6000 square pixels laid out at the size it is offered, recording each height it is measured at: it
     * prefers 200 wide and needs 50, and needs two thirds of the height it prefers at a width.
     */
    private class AreaPolicy : MeasurePolicy {
        val heights = mutableListOf<Int>()

        override fun MeasureScope.measure(
            measurables: List<Measurable>,
            constraints: Constraints,
        ): MeasureResult {
            heights += constraints.maxHeight
            return layout(constraints.maxWidth, constraints.maxHeight) {}
        }

        override fun IntrinsicMeasureScope.minIntrinsicWidth(
            measurables: List<IntrinsicMeasurable>,
            height: Int,
        ): Int = 50

        override fun IntrinsicMeasureScope.maxIntrinsicWidth(
            measurables: List<IntrinsicMeasurable>,
            height: Int,
        ): Int = 200

        override fun IntrinsicMeasureScope.minIntrinsicHeight(
            measurables: List<IntrinsicMeasurable>,
            width: Int,
        ): Int = 4000 / width

        override fun IntrinsicMeasureScope.maxIntrinsicHeight(
            measurables: List<IntrinsicMeasurable>,
            width: Int,
        ): Int = 6000 / width
    }

    /** Prefers [width], and fills the width it is offered at a 16:9 ratio. */
    private class RatioPolicy(
        private val width: Int,
    ) : MeasurePolicy {
        override fun MeasureScope.measure(
            measurables: List<Measurable>,
            constraints: Constraints,
        ): MeasureResult = layout(constraints.maxWidth, constraints.maxWidth * 9 / 16) {}

        override fun IntrinsicMeasureScope.minIntrinsicWidth(
            measurables: List<IntrinsicMeasurable>,
            height: Int,
        ): Int = width

        override fun IntrinsicMeasureScope.maxIntrinsicWidth(
            measurables: List<IntrinsicMeasurable>,
            height: Int,
        ): Int = width

        override fun IntrinsicMeasureScope.minIntrinsicHeight(
            measurables: List<IntrinsicMeasurable>,
            width: Int,
        ): Int = width * 9 / 16

        override fun IntrinsicMeasureScope.maxIntrinsicHeight(
            measurables: List<IntrinsicMeasurable>,
            width: Int,
        ): Int = width * 9 / 16
    }

    /**
     * Lays its one child out at [granted], asking the child's preferred and minimum sizes first where [asks] holds, as
     * `GridBagLayout` does, and asking nothing otherwise, as `BorderLayout` does its center child.
     */
    private data class Scripted(
        val granted: Dimension,
        val asks: Boolean,
    ) : PanelLayout<PanelScope>(object : PanelScope {}) {
        override fun <C : Container> installOn(updater: SwingNodeUpdater<C>) {
            updater.installManager({ ScriptedLayout(this@Scripted) }) { manager ->
                if (manager.script != this@Scripted) {
                    manager.script = this@Scripted
                    revalidate()
                }
            }
        }
    }

    private class ScriptedLayout(
        var script: Scripted,
    ) : LayoutManager {
        override fun addLayoutComponent(
            name: String?,
            comp: Component?,
        ) = Unit

        override fun removeLayoutComponent(comp: Component?) = Unit

        override fun preferredLayoutSize(parent: Container): Dimension = Dimension(script.granted)

        override fun minimumLayoutSize(parent: Container): Dimension = Dimension(script.granted)

        override fun layoutContainer(parent: Container) {
            for (child in parent.components) {
                if (script.asks) {
                    child.preferredSize
                    child.minimumSize
                }
                child.setBounds(0, 0, script.granted.width, script.granted.height)
            }
        }
    }

    /** Asks its container's parent for its preferred size as it lays the container out, and places nothing. */
    private class ParentAskingLayout : LayoutManager {
        override fun addLayoutComponent(
            name: String?,
            comp: Component?,
        ) = Unit

        override fun removeLayoutComponent(comp: Component?) = Unit

        override fun preferredLayoutSize(parent: Container): Dimension = Dimension(10, 10)

        override fun minimumLayoutSize(parent: Container): Dimension = Dimension(10, 10)

        override fun layoutContainer(parent: Container) {
            parent.parent.preferredSize
        }
    }

    /** Whether a paint asked for a validation, and the width the validation that followed laid the subject out at. */
    private data class PaintCycle(
        val paintAsks: Boolean,
        val width: Int,
    )

    /**
     * Lays its one child out at the width it is given and half that height, and prefers twice the height the child
     * prefers as its width, as a Column holding a child at an aspect ratio of 2 answers a stock parent.
     */
    private class RatioLayout : LayoutManager {
        override fun addLayoutComponent(
            name: String?,
            comp: Component?,
        ) = Unit

        override fun removeLayoutComponent(comp: Component?) = Unit

        override fun preferredLayoutSize(parent: Container): Dimension {
            val height = parent.getComponent(0).preferredSize.height
            return Dimension(height * 2, height)
        }

        override fun minimumLayoutSize(parent: Container): Dimension = preferredLayoutSize(parent)

        override fun layoutContainer(parent: Container) {
            parent.getComponent(0).setBounds(0, 0, parent.width, parent.width / 2)
        }
    }

    /** A label counting how often it is asked what it prefers and how often it is resized. */
    private class CountingLabel : JLabel("Counted") {
        var queries = 0
        var resizes = 0

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
            queries++
            return super.getPreferredSize()
        }
    }

    /** A text area wrapping its text at word boundaries, logging each width it is resized to. */
    private class WidthLoggingTextArea : JTextArea(WRAPPING_TEXT) {
        val widths = mutableListOf<Int>()

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
            if (width != this.width) widths += width
            super.setBounds(x, y, width, height)
        }
    }

    private companion object {
        const val WIDTH = 320
        const val NARROW = 160
        const val NARROWED = 30
        const val SQUEEZED_HEIGHT = 224

        /** The text needs the bar at every width in a viewport 40 or 60 high, and at none from 150 up. */
        val Slots: Map<String, Slot> = swingParentSlots(listOf(40, 60, 150, 175, 200))

        /** The slots whose parent sets a container's width before it asks the container anything. */
        val WidthSettingSlots: Set<String> =
            setOf("BorderLayout NORTH", "BorderLayout SOUTH").also { require(Slots.keys.containsAll(it)) }

        /** How many cycles of a paint and a validation [assertLaidOutAtTheWidthsOfPlainSwing] runs. */
        const val CYCLES = 4

        /**
         * Asserts that in `BorderLayout` east a Column holding the component [leaf] makes at an aspect ratio of 2 is
         * laid out at the widths a panel with a [RatioLayout] holding that component takes, another width at each
         * validation, and that a paint asks for a validation of the Column where [paintAsks] holds.
         */
        fun assertLaidOutAtTheWidthsOfPlainSwing(
            paintAsks: Boolean,
            leaf: () -> JComponent,
        ) {
            val plain =
                cyclesOf { modifier ->
                    SwingNode(factory = { JPanel(RatioLayout()).apply { add(leaf()) } }, modifier = modifier)
                }
            val column =
                cyclesOf { modifier ->
                    Column(modifier = modifier) {
                        SwingNode(factory = leaf, modifier = SwingModifier.fillMaxWidth().aspectRatio(2f))
                    }
                }
            assertEquals(List(CYCLES) { true }, plain.map { it.paintAsks }, "precondition: each plain paint asks")
            assertTrue(
                plain.zipWithNext().all { (before, after) -> before.width != after.width },
                "precondition: plain Swing lays the panel out at another width each time: $plain",
            )
            assertEquals(plain.map { it.width }, column.map { it.width }, "the widths the Column is laid out at")
            assertEquals(
                List(CYCLES) { paintAsks },
                column.map { it.paintAsks },
                "whether a paint asks for a validation of the Column",
            )
        }

        /**
         * Shows [subject] in `BorderLayout` east, and [CYCLES] times paints it and validates it: whether each paint
         * asks for a validation of it, and the width it is laid out at.
         */
        fun cyclesOf(subject: @Composable (SwingModifier) -> Unit): List<PaintCycle> {
            val cycles = mutableListOf<PaintCycle>()
            runComposeSwingTest {
                setContent { QuestionSlots.getValue("BorderLayout EAST")(WIDTH) { subject(it.testTag("subject")) } }
                val component = onNodeWithTag("subject").fetch<JComponent>()
                repeat(CYCLES) {
                    val asks =
                        withRecordedRepaints { recorder ->
                            captureToImage()
                            recorder.relayouts.any {
                                it === component || it.isAncestorOf(component) || component.isAncestorOf(it)
                            }
                        }
                    component.revalidate()
                    awaitIdle()
                    cycles += PaintCycle(asks, component.width)
                }
            }
            return cycles
        }

        /**
         * Shows [subject] in [slot], handing it a text area wrapping the text, a [LineBreakCountingTextArea] given its
         * text before it wraps, as [TextArea] is, where [counted] holds, which it places under a modifier. It drags the
         * slot from [WIDTH] to 100 pixels narrower in five steps and one step back, then edits the text so it takes
         * more lines: what each step took to settle.
         */
        fun settlingOf(
            slot: Slot,
            counted: Boolean = false,
            subject: @Composable (SwingModifier, @Composable (SwingModifier) -> Unit) -> Unit,
        ): List<Settled> {
            val steps = mutableListOf<Settled>()
            runComposeSwingTest {
                var width by mutableIntStateOf(WIDTH)
                var text by mutableStateOf(WRAPPING_TEXT)
                setContent {
                    slot(width) { modifier ->
                        subject(modifier.testTag("subject")) { areaModifier ->
                            if (counted) {
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
                            } else {
                                TextArea(
                                    text,
                                    {},
                                    areaModifier,
                                    lineWrap = true,
                                    wrapStyleWord = true,
                                )
                            }
                        }
                    }
                }
                val component = onNodeWithTag("subject").fetch<JComponent>()
                val area = onNodeOfType<JTextArea>().fetch()

                suspend fun step(name: String) {
                    val cycles = cyclesUntilStable(component)
                    val rowHeight =
                        if (onAllNodesWithTag("row").fetchSize() == 0) {
                            area.height
                        } else {
                            onNodeWithTag("row").fetch<JComponent>().height
                        }
                    val lineBreaks = (area as? LineBreakCountingTextArea)?.lineBreaks ?: 0
                    steps += Settled(name, cycles, component.height, area.height, rowHeight, lineBreaks)
                    (area as? LineBreakCountingTextArea)?.lineBreaks = 0
                }
                step("first show")
                for (step in 1..5) {
                    width = WIDTH - 20 * step
                    awaitIdle()
                    step("drag to $width")
                }
                width += 20
                awaitIdle()
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

/**
 * A text area counting the line breaks it works out while it wraps its text: it works out every line again each time
 * it lays its text out at another width.
 */
private class LineBreakCountingTextArea : JTextArea() {
    var lineBreaks = 0

    override fun updateUI() {
        setUI(
            object : BasicTextAreaUI() {
                override fun create(element: Element): View =
                    if (!lineWrap) {
                        super.create(element)
                    } else {
                        object : WrappedPlainView(element, wrapStyleWord) {
                            override fun calculateBreakPosition(
                                start: Int,
                                end: Int,
                            ): Int {
                                lineBreaks++
                                return super.calculateBreakPosition(start, end)
                            }
                        }
                    }
            },
        )
    }
}
