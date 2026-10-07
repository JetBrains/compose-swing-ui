package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.mutableIntStateOf
import org.jetbrains.compose.swing.assertAskedForLayout
import org.jetbrains.compose.swing.assertAskedForNoLayout
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.withRecordedRepaints
import org.junit.jupiter.api.DisplayName
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager2
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A container under a `LayoutManager2` written for the test, which reads the container's size the way it likes and
 * uses the preferences the way it likes. The container returns results as a wrapping `JTextArea` does, with its heights
 * at the
 * width it holds, and lays its content out at the bounds the parent gives it.
 */
class CustomLayoutManagerParentTest {
    @Test
    fun aParentReadingPreferencesAtTwoWidthsAndGrantingTheSecondLaysContentOutAtThatWidthsHeightAndKeepsIt() =
        runComposeSwingTest {
            val layout = Parent.ReadsPreferencesAtTwoWidths()
            setContent {
                SwingNode(
                    factory = { JPanel(layout) },
                    modifier = SwingModifier.testTag("host").preferredSize(320, 600),
                ) {
                    Box(modifier = SwingModifier.testTag("content")) {
                        Label("Preview", modifier = SwingModifier.testTag("label").fillMaxWidth().aspectRatio(16f / 9f))
                    }
                }
            }
            val host = onNodeWithTag("host").fetch<JComponent>()
            val content = onNodeWithTag("content").fetch<JComponent>()
            val label = onNodeWithTag("label").fetch<JComponent>()
            awaitIdle()

            for (revalidations in 0..3) {
                assertEquals(320 * 2 / 3, content.width, "revalidated $revalidations times: the granted width")
                val atRatio = (content.width * 9f / 16f).roundToInt()
                assertEquals(atRatio, content.height, "revalidated $revalidations times: the height for the width")
                assertEquals(Dimension(content.width, atRatio), label.size, "revalidated $revalidations times: content")
                assertEquals(0, cyclesUntilStable(content), "revalidated $revalidations times: a paint requests none")
                host.revalidate()
                awaitIdle()
            }
        }

    @Test
    fun aPreferredHeightReadBeforeADifferentWidthIsGrantedSettlesAfterOnePaint() =
        runComposeSwingTest {
            val layout = Parent.ReadsPreferredSizeBeforeGrantingWidth()
            setContent {
                SwingNode(
                    factory = { JPanel(layout) },
                    modifier = SwingModifier.testTag("host").preferredSize(320, 600),
                ) {
                    Column(modifier = SwingModifier.testTag("content")) {
                        TextArea(
                            WRAPPING_TEXT,
                            {},
                            modifier = SwingModifier.testTag("text").fillMaxWidth(),
                            lineWrap = true,
                            wrapStyleWord = true,
                        )
                    }
                }
            }
            val host = onNodeWithTag("host").fetch<JPanel>()
            val content = onNodeWithTag("content").fetch<JComponent>()
            val text = onNodeWithTag("text").fetch<JTextArea>()
            settleWithPaint()
            assertTrue(content.isValid)

            for (width in listOf(160, 320)) {
                val oldHeight = content.height
                val settledLayouts = layout.layouts
                layout.grantedWidth = width
                host.invalidate()
                root.validate()
                assertEquals(Dimension(width, oldHeight), content.size, "the height returned at the held width")
                assertEquals(width, text.width, "the leaf is laid out at the actual width")
                val expectedHeight = text.preferredSize.height
                if (width == 160) {
                    assertTrue(expectedHeight > oldHeight, "narrowing wraps onto more lines")
                } else {
                    assertTrue(expectedHeight < oldHeight, "widening wraps onto fewer lines")
                }

                settleWithPaint()

                assertEquals(Dimension(width, expectedHeight), content.size)
                assertEquals(expectedHeight, text.height)
                assertEquals(settledLayouts + 2, layout.layouts, "one corrective validation settles the new width")
                assertEquals(0, cyclesUntilStable(content), "a settled paint requests no validation")
            }
        }

    @Test
    fun paintReconcilesOnlyTheHeightPreferencesReadByTheParentAndSettledPaintsReadNone() {
        for (reads in listOf("minimum", "preferred", "both")) {
            runComposeSwingTest {
                val minimumWidths = mutableListOf<Int>()
                val preferredWidths = mutableListOf<Int>()
                val parent =
                    ScriptedStockLayout(sizeOf = { Dimension(320, 600) }) { child, _ ->
                        val minimum = if (reads != "preferred") child.minimumSize.height else 0
                        val preferred = if (reads != "minimum") child.preferredSize.height else minimum
                        child.setBounds(0, 0, grantedWidth ?: 160, preferred)
                    }
                val policy =
                    ScriptedMeasurePolicy(
                        minimumWidth = { 160 },
                        preferredWidth = { 160 },
                        minimumHeight = {
                            minimumWidths += it
                            it / 8
                        },
                        preferredHeight = {
                            preferredWidths += it
                            it / 4
                        },
                    )
                setStockContent(JPanel(parent)) {
                    Layout(policy, SwingModifier.testTag("content"))
                }
                val content = onNodeWithTag("content").fetch<JComponent>()
                val initialHeight = if (reads == "minimum") 20 else 40
                onNodeWithTag("content").assertProperty(Dimension(160, initialHeight)) { size }
                assertSettledPaints(content, minimumWidths, preferredWidths, "$reads: stable")

                parent.grantedWidth = 240
                onNodeWithTag("host").fetch<JComponent>().invalidate()
                root.validate()
                onNodeWithTag("content").assertProperty(Dimension(240, initialHeight)) { size }
                val layoutsBeforePaint = parent.layouts
                minimumWidths.clear()
                preferredWidths.clear()
                withRecordedRepaints { recorder ->
                    onNodeWithTag("content").captureToImage()
                    assertEquals(1, recorder.relayouts.count { it === content }, "$reads: corrective layout request")
                }
                assertEquals(
                    if (reads == "preferred") emptyList() else listOf(240),
                    minimumWidths,
                    "$reads: minimum height queries at the granted width",
                )
                assertEquals(
                    if (reads == "minimum") emptyList() else listOf(240),
                    preferredWidths,
                    "$reads: preferred height queries at the granted width",
                )
                awaitIdle()
                onNodeWithTag("content").assertProperty(Dimension(240, if (reads == "minimum") 30 else 60)) { size }
                assertEquals(layoutsBeforePaint + 1, parent.layouts, "$reads: one corrective validation")
                assertSettledPaints(content, minimumWidths, preferredWidths, "$reads: corrected")
            }
        }
    }

    private fun ComposeSwingTest.assertSettledPaints(
        content: JComponent,
        minimumWidths: MutableList<Int>,
        preferredWidths: MutableList<Int>,
        description: String,
    ) {
        minimumWidths.clear()
        preferredWidths.clear()
        withRecordedRepaints { recorder ->
            repeat(3) { onNodeWithTag("content").captureToImage() }
            recorder.assertAskedForNoLayout(content, "$description paints")
        }
        assertEquals(emptyList(), minimumWidths, "$description minimum height queries")
        assertEquals(emptyList(), preferredWidths, "$description preferred height queries")
    }

    @Test
    fun aStateReadEstablishedByPaintReconciliationRemainsObservedWhenTheHeightDidNotChange() {
        for (minimum in listOf(false, true)) {
            runComposeSwingTest {
                val height = mutableIntStateOf(40)
                val widthsRead = mutableListOf<Int>()
                val heightAt: (Int) -> Int = { width ->
                    widthsRead += width
                    if (width == 240) height.intValue else 40
                }
                val parent =
                    ScriptedStockLayout(sizeOf = { Dimension(320, 600) }) { child, _ ->
                        val preferred = if (minimum) child.minimumSize else child.preferredSize
                        child.setBounds(0, 0, grantedWidth ?: 160, preferred.height)
                    }
                val policy =
                    ScriptedMeasurePolicy(
                        minimumWidth = { 160 },
                        preferredWidth = { 160 },
                        minimumHeight = heightAt,
                        preferredHeight = heightAt,
                    )
                setStockContent(JPanel(parent)) {
                    Layout(policy, SwingModifier.testTag("content"))
                }
                val host = onNodeWithTag("host").fetch<JComponent>()
                val content = onNodeWithTag("content").fetch<JComponent>()
                parent.grantedWidth = 240
                host.invalidate()
                root.validate()
                onNodeWithTag("content").assertProperty(Dimension(240, 40)) { size }
                assertTrue(240 !in widthsRead, "minimum=$minimum: the state was not read before paint")
                widthsRead.clear()
                withRecordedRepaints { recorder ->
                    onNodeWithTag("content").captureToImage()
                    recorder.assertAskedForNoLayout(content, "minimum=$minimum: the checked height stayed equal")
                }
                assertEquals(listOf(240), widthsRead, "minimum=$minimum: paint established the state read")

                withRecordedRepaints { recorder ->
                    height.intValue = 80
                    awaitIdle()
                    recorder.assertAskedForLayout(content, "minimum=$minimum: the state read during paint changed")
                }
                onNodeWithTag("content").assertProperty(Dimension(240, 80)) { size }
                assertEquals(
                    80,
                    if (minimum) content.minimumSize.height else content.preferredSize.height,
                    "minimum=$minimum: the parent consumes the changed height",
                )
            }
        }
    }

    @Test
    @DisplayName("Column cost matches raw leaf preference reads; accepted stretch cost: 95 vs 25 line breaks")
    fun aContainerUnderEachParentCostsNoMoreValidationsAndLineBreaksThanAWrappingTextAreaInTheSameSlot() {
        val parents =
            mapOf<String, () -> Parent>(
                "reads preferences at two widths and grants the second" to { Parent.ReadsPreferencesAtTwoWidths() },
                "stretches without reading preferences again (accepted cost: 95 vs 25 line breaks)" to
                    { Parent.StretchesWithoutReadingPreferencesAgain() },
                "grants a fixed rectangle and never reads preferences" to { Parent.FixedRectangle() },
                "grants the preferred width less one" to { Parent.PreferredWidthLessOne() },
            )
        val dearer = mutableListOf<String>()
        for ((name, parent) in parents) {
            val area = costOf(parent(), inContainer = false)
            val containerParent = parent()
            val container = costOf(containerParent, inContainer = true)
            val matched =
                if (containerParent is Parent.StretchesWithoutReadingPreferencesAgain) {
                    costOf(
                        Parent.StretchesWithoutReadingPreferencesAgain(readAtPreferredWidth = true),
                        inContainer = false,
                    ).also {
                        assertEquals(25, area.lineBreaks, "$name: original raw control")
                        assertEquals(101, assertNotNull(it.firstPreferredSize).width, "$name: natural width")
                        assertTrue(
                            assertNotNull(it.firstPreferredSize).height > assertNotNull(area.firstPreferredSize).height,
                            "$name: the natural-width read wraps the text",
                        )
                        assertEquals(
                            it.firstPreferredSize,
                            container.firstPreferredSize,
                            "$name: identical natural-width preference read",
                        )
                        assertEquals(it.lineBreaks, container.lineBreaks, "$name: matched raw preference reads")
                        assertTrue(container.lineBreaks <= 95, "$name: accepted Column cost limit")
                        assertTrue(container.lineBreaks > area.lineBreaks, "$name: extra natural-width wrapping")
                        assertTrue(area.settled, "$name: the raw control keeps its bounds")
                        assertTrue(container.settled, "$name: the container keeps its bounds")
                        assertTrue(it.settled, "$name: the matched control keeps its bounds")
                    }
                } else {
                    area
                }
            if (containerParent is Parent.FixedRectangle) {
                assertTrue(area.lineBreaks in 1..35, "$name: raw control cost limit")
                assertEquals(area.lineBreaks, container.lineBreaks, "$name: fixed-width wrapping")
            }
            if (container.layouts > matched.layouts) {
                dearer += "$name: ${container.layouts} validations, ${matched.layouts}"
            }
            if (container.lineBreaks > matched.lineBreaks) {
                dearer += "$name: ${container.lineBreaks} line breaks, ${matched.lineBreaks}"
            }
        }
        assertEquals(emptyList(), dearer, "what a container costs beyond a wrapping text area")
    }

    @Test
    fun aParentReadingMinimumSizeAtHalfTheSlotAndGrantingTheSlotCostsNoMoreValidationsThanForATextArea() {
        val area = costOf(Parent.ReadsMinimumAtHalfWidthThenGrantsTheSlot(), inContainer = false)
        val column = costOf(Parent.ReadsMinimumAtHalfWidthThenGrantsTheSlot(), inContainer = true)

        assertTrue(
            column.layouts <= area.layouts,
            "the column needs ${column.layouts} validations, a wrapping text area ${area.layouts}",
        )
        assertTrue(
            column.lineBreaks <= area.lineBreaks,
            "the column breaks the text ${column.lineBreaks} times, a wrapping text area ${area.lineBreaks}",
        )
        assertTrue(area.settled, "a wrapping text area keeps its bounds through a further validation")
        assertTrue(column.settled, "a column keeps its bounds through a further validation")
    }

    @Test
    fun aMinimumSizeReadAfterPaintUsesTheWidthTheContainerWasPaintedAt() =
        runComposeSwingTest {
            val ratio = 8
            setContent {
                SwingNode(
                    factory = { JPanel(Parent.FixedRectangle()) },
                    modifier = SwingModifier.testTag("host").preferredSize(320, 600),
                ) {
                    Box(modifier = SwingModifier.testTag("content")) {
                        Label("Preview", modifier = SwingModifier.fillMaxWidth().aspectRatio(ratio.toFloat()))
                    }
                }
            }
            awaitIdle()
            captureToImage()
            val content = onNodeWithTag("content").fetch<JComponent>()
            assertEquals(content.width / ratio, content.minimumSize.height, "a wrapping text area formats at its paint")
        }

    /**
     * What a parent took over the first show and four paints: its validations and the text's line breaks, and whether
     * its child kept its bounds through one more validation.
     */
    private class Cost(
        val layouts: Int,
        val lineBreaks: Int,
        val firstPreferredSize: Dimension?,
        val settled: Boolean,
    )

    /**
     * Shows [parent] holding a wrapping text area, in a `Column` where [inContainer] holds, and paints four times, each
     * paint followed by the validation it requests. A parent that never settles costs more with each paint.
     */
    private fun costOf(
        parent: Parent,
        inContainer: Boolean,
    ): Cost {
        lateinit var cost: Cost
        runComposeSwingTest {
            val area =
                LineBreakCountingTextArea().apply {
                    text = WRAPPING_TEXT
                    lineWrap = true
                    wrapStyleWord = true
                }
            setContent {
                SwingNode(
                    factory = { JPanel(parent).apply { if (!inContainer) add(area) } },
                    modifier = SwingModifier.testTag("host").preferredSize(320, 600),
                ) {
                    if (inContainer) Column { SwingNode(factory = { area }, modifier = SwingModifier.fillMaxWidth()) }
                }
            }
            awaitIdle()
            repeat(4) {
                captureToImage()
                awaitIdle()
            }
            val host = onNodeWithTag("host").fetch<JComponent>()
            val counted = Cost(parent.layouts, area.lineBreaks, parent.firstPreferredSize, settled = false)
            val bounds = host.getComponent(0).bounds
            host.revalidate()
            awaitIdle()
            cost =
                Cost(
                    counted.layouts,
                    counted.lineBreaks,
                    counted.firstPreferredSize,
                    host.getComponent(0).bounds == bounds,
                )
        }
        return cost
    }

    private sealed class Parent : LayoutManager2 {
        var layouts = 0
        var firstPreferredSize: Dimension? = null

        /** Sizes and places the one [child] of a host [width] wide. */
        abstract fun place(
            child: Component,
            width: Int,
        )

        final override fun layoutContainer(parent: Container) {
            layouts++
            place(parent.getComponent(0), parent.width)
        }

        override fun preferredLayoutSize(parent: Container): Dimension = parent.getComponent(0).preferredSize

        override fun minimumLayoutSize(parent: Container): Dimension = parent.getComponent(0).minimumSize

        override fun maximumLayoutSize(target: Container): Dimension = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)

        override fun getLayoutAlignmentX(target: Container): Float = 0f

        override fun getLayoutAlignmentY(target: Container): Float = 0f

        override fun invalidateLayout(target: Container) = Unit

        override fun addLayoutComponent(
            comp: Component,
            constraints: Any?,
        ) = Unit

        override fun addLayoutComponent(
            name: String?,
            comp: Component,
        ) = Unit

        override fun removeLayoutComponent(comp: Component) = Unit

        /**
         * Sets the child to the host's width, reads its size, sets it to two thirds of that width and reads preferences
         * again.
         */
        class ReadsPreferencesAtTwoWidths : Parent() {
            override fun place(
                child: Component,
                width: Int,
            ) {
                child.setSize(width, child.height)
                child.preferredSize
                child.setSize(width * 2 / 3, child.height)
                child.setBounds(0, 0, width * 2 / 3, child.preferredSize.height)
            }
        }

        /** Sets the child to half the host's width, reads its minimum size and grants it the host's width. */
        class ReadsMinimumAtHalfWidthThenGrantsTheSlot : Parent() {
            override fun place(
                child: Component,
                width: Int,
            ) {
                child.setSize(width / 2, child.height)
                val sizePreference = child.minimumSize
                child.setBounds(0, 0, width, sizePreference.height)
            }
        }

        /** Reads the preferred size before it sets any width, then stretches the child to the host's width. */
        class StretchesWithoutReadingPreferencesAgain(
            private val readAtPreferredWidth: Boolean = false,
        ) : Parent() {
            override fun place(
                child: Component,
                width: Int,
            ) {
                if (readAtPreferredWidth && child.width == 0) {
                    val natural = child.preferredSize
                    child.setSize(natural.width, natural.height)
                }
                val preferred = child.preferredSize
                if (firstPreferredSize == null) firstPreferredSize = preferred
                child.setBounds(0, 0, width, preferred.height)
            }
        }

        class ReadsPreferredSizeBeforeGrantingWidth : Parent() {
            var grantedWidth = 320

            override fun place(
                child: Component,
                width: Int,
            ) {
                val height = child.preferredSize.height
                child.setBounds(0, 0, grantedWidth, height)
            }
        }

        class FixedRectangle : Parent() {
            override fun place(
                child: Component,
                width: Int,
            ) = child.setBounds(0, 0, 200, 100)

            override fun preferredLayoutSize(parent: Container): Dimension = Dimension(200, 100)

            override fun minimumLayoutSize(parent: Container): Dimension = Dimension(200, 100)
        }

        class PreferredWidthLessOne : Parent() {
            override fun place(
                child: Component,
                width: Int,
            ) {
                val preferred = child.preferredSize
                child.setBounds(0, 0, preferred.width - 1, preferred.height)
            }
        }
    }
}
