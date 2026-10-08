package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.mutableIntStateOf
import org.jetbrains.compose.swing.assertAskedForLayout
import org.jetbrains.compose.swing.assertAskedForNoLayout
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.withRecordedRepaints
import java.awt.Dimension
import java.awt.Rectangle
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
    fun preferredSizeQueriesAcrossWidthsUseTheCurrentWidth() =
        runComposeSwingTest {
            val ratio = 8
            val layout = parentQueryingAtTwoWidths(grantsFirst = true)
            setContentOf(layout) { ratio.toFloat() }
            val content = onNodeWithTag("content").fetch<JComponent>()

            for (width in listOf(320, 160, 320)) {
                content.setSize(width, content.height)
                content.preferredSize = null
                assertEquals(width / ratio, content.preferredSize.height, "preferred height at $width")
            }
        }

    @Test
    fun alternatingPreferenceReadsUseTheCurrentWidth() =
        runComposeSwingTest {
            val ratio = 8
            val layout = parentQueryingAtTwoWidths()
            setContentOf(layout) { ratio.toFloat() }
            val content = onNodeWithTag("host").fetch<JComponent>().getComponent(0) as JComponent
            for (width in listOf(320, 213, 320)) {
                content.setSize(width, content.height)
                content.preferredSize = null
                assertEquals(
                    (width.toFloat() / ratio).roundToInt(),
                    content.preferredSize.height,
                    "preferred height at $width",
                )
            }
        }

    @Test
    fun alternatingMinimumSizeReadsUseNaturalWidth() =
        runComposeSwingTest {
            val ratio = 8
            val layout = parentQueryingAtTwoWidths(minimum = true)
            setContentOf(layout) { ratio.toFloat() }
            val content = onNodeWithTag("host").fetch<JComponent>().getComponent(0) as JComponent
            for (width in listOf(320, 213, 320)) {
                content.setSize(width, content.height)
                content.preferredSize = null
                content.minimumSize = null
                val preferred = content.preferredSize
                val minimum = content.minimumSize
                assertEquals(
                    (preferred.width.toFloat() / ratio).roundToInt(),
                    minimum.height,
                    "minimum height at held width $width uses natural preferred width",
                )
            }
        }

    private fun ComposeSwingTest.setContentOf(
        layout: ScriptedStockLayout,
        ratio: () -> Float,
    ) = setStockContent(JPanel(layout)) {
        Box(modifier = SwingModifier.testTag("content")) {
            Label(
                "Preview",
                modifier = SwingModifier.testTag("label").fillMaxWidth().aspectRatio(ratio()),
            )
        }
    }

    @Test
    fun preferredHeightUsesTheHeldWidthAndMinimumHeightUsesItsNaturalWidth() =
        runComposeSwingTest {
            val ratio = 8
            val layout = parentQueryingAtTwoWidths(grantsFirst = true)
            setContentOf(layout) { ratio.toFloat() }
            val content = onNodeWithTag("content").fetch<JComponent>()
            awaitIdle()
            withRecordedRepaints { recorder ->
                captureToImage()
                recorder.assertAskedForNoLayout(content, "painting does not reconcile size preferences")
            }
            awaitIdle()
            assertEquals(320, content.width)
            content.preferredSize = null
            assertEquals(content.width / ratio, content.preferredSize.height)
            content.minimumSize = null
            val preferred = content.preferredSize
            val minimum = content.minimumSize
            assertEquals(
                (preferred.width.toFloat() / ratio).roundToInt(),
                minimum.height,
                "minimum height uses natural preferred width",
            )
            assertEquals(320, content.width)
        }

    @Test
    fun aParentReadingPreferencesAtTwoWidthsAndGrantingTheSecondLaysContentOutAtThatWidthsHeightAndKeepsIt() =
        runComposeSwingTest {
            val layout = parentQueryingAtTwoWidths()
            setContentOf(layout) { 16f / 9f }
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
    fun aStockParentKeepsTheHeightItChoseBeforeChangingTheWidth() =
        runComposeSwingTest {
            val layout =
                ScriptedStockLayout { child, width ->
                    child.setBounds(0, 0, grantedWidth ?: width, child.preferredSize.height)
                }
            setWrappingContent(layout, inContainer = true)
            val host = onNodeWithTag("host").fetch<JPanel>()
            val content = host.getComponent(0) as JComponent
            val text = content.getComponent(0) as JTextArea
            root.validate()

            for (width in listOf(160, 320)) {
                content.preferredSize = null
                val heightChosenAtCurrentWidth = content.preferredSize.height
                val settledLayouts = layout.layouts
                layout.grantedWidth = width
                host.invalidate()
                root.validate()
                assertEquals(width, text.width, "the leaf is laid out at the actual width")
                assertEquals(Dimension(width, heightChosenAtCurrentWidth), content.size)
                assertEquals(settledLayouts + 1, layout.layouts, "the granted width is measured in its layout pass")

                withRecordedRepaints { recorder ->
                    content.captureToImage()
                    recorder.assertAskedForNoLayout(content, "paint does not reconcile size preferences")
                }
                assertEquals(
                    Dimension(width, heightChosenAtCurrentWidth),
                    content.size,
                    "painting keeps the granted bounds",
                )
                assertEquals(settledLayouts + 1, layout.layouts, "painting does not request another layout")
            }
        }

    @Test
    fun paintingDoesNotReconcileSizePreferencesForAStockParent() {
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
                    recorder.assertAskedForNoLayout(content, "$reads: paint does not query size preferences")
                }
                assertEquals(emptyList(), minimumWidths, "$reads: paint minimum height queries")
                assertEquals(emptyList(), preferredWidths, "$reads: paint preferred height queries")
                awaitIdle()
                onNodeWithTag("content").assertProperty(Dimension(240, initialHeight)) { size }
                assertEquals(layoutsBeforePaint, parent.layouts, "$reads: paint does not request validation")
                assertSettledPaints(content, minimumWidths, preferredWidths, "$reads: painted")
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
    fun aStateReadEstablishedByAPreferredSizeQueryRemainsObservedWhenTheHeightDidNotChange() =
        runComposeSwingTest {
            val height = mutableIntStateOf(40)
            val widthsRead = mutableListOf<Int>()
            val heightAt: (Int) -> Int = { width ->
                widthsRead += width
                if (width == 240) height.intValue else 40
            }
            val parent =
                ScriptedStockLayout(sizeOf = { Dimension(320, 600) }) { child, _ ->
                    val preferred = child.preferredSize
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
            assertTrue(240 !in widthsRead, "layout did not query preferred size at this width")
            widthsRead.clear()
            content.preferredSize = null
            assertEquals(40, content.preferredSize.height)
            assertEquals(listOf(240), widthsRead, "the preferred-size query reads state")

            withRecordedRepaints { recorder ->
                height.intValue = 80
                awaitIdle()
                recorder.assertAskedForLayout(content, "the observed preferred-size query triggers layout")
            }
            onNodeWithTag("content").assertProperty(Dimension(240, 80)) { size }
            assertEquals(80, content.preferredSize.height, "the parent consumes the changed height")
        }

    @Test
    fun aContainerCostsNoMoreThanAWrappingTextAreaInTheSameSlot() {
        val parents =
            mapOf<ParentSizeQueries, () -> ScriptedStockLayout>(
                ParentSizeQueries.TwoWidths to { parentQueryingAtTwoWidths() },
                ParentSizeQueries.Stretch to { stretchingParent() },
                ParentSizeQueries.FixedRectangle to { fixedRectangleParent() },
                ParentSizeQueries.PreferredWidthLessOne to {
                    ScriptedStockLayout { child, _ ->
                        val preferred = child.preferredSize
                        child.setBounds(0, 0, preferred.width - 1, preferred.height)
                    }
                },
            )
        val dearer = mutableListOf<String>()
        for ((name, parent) in parents) {
            val area = costOf(parent(), inContainer = false)
            val container =
                costOf(
                    parent(),
                    inContainer = true,
                    assertStableAfterQuery = name == ParentSizeQueries.Stretch,
                )
            val matched =
                if (name == ParentSizeQueries.Stretch) {
                    costOf(stretchingParent(readAtPreferredWidth = true), inContainer = false).also {
                        assertStretchLifecycle(name.name, area, container, it)
                    }
                } else {
                    area
                }
            if (name == ParentSizeQueries.FixedRectangle) {
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

    private fun assertStretchLifecycle(
        name: String,
        area: Cost,
        container: Cost,
        matched: Cost,
    ) {
        assertEquals(25, area.lineBreaks, "$name: original raw control")
        assertTrue(
            assertNotNull(matched.firstPreferredSize).height > assertNotNull(area.firstPreferredSize).height,
            "$name: the natural-width read wraps the text",
        )
        assertEquals(matched.firstPreferredSize, container.firstPreferredSize, "$name: identical natural preference")
        assertEquals(matched.lineBreaks, container.lineBreaks, "$name: matched raw preference reads")
        assertTrue(container.lineBreaks <= 95, "$name: accepted Column cost limit")
        assertTrue(container.lineBreaks > area.lineBreaks, "$name: extra natural-width wrapping")
        assertEquals(
            assertNotNull(matched.firstPreferredSize).height,
            matched.initialBounds.height,
            "$name: the matched parent initially grants its preferred height",
        )
        assertTrue(
            assertNotNull(container.firstPreferredSize).width < container.initialBounds.width,
            "$name: the slot stretches the natural preferred width",
        )
        assertTrue(
            container.initialBounds.height > container.initialLeafBounds.height,
            "$name: initial grant uses natural height while content uses slot width",
        )
        assertTrue(
            container.initialBounds != container.queriedBounds,
            "$name: explicit revalidation replaces the initial natural-size grant",
        )
        assertEquals(
            Dimension(container.queriedBounds.width, container.initialLeafBounds.height),
            container.queriedBounds.size,
            "$name: explicit validation reads the current-width height",
        )
    }

    @Test
    fun aParentReadingMinimumSizeAtHalfTheSlotAndGrantingTheSlotCostsNoMoreValidationsThanForATextArea() {
        val (area, column) =
            listOf(false, true).map { inContainer ->
                costOf(
                    ScriptedStockLayout { child, width ->
                        child.setSize(width / 2, child.height)
                        child.setBounds(0, 0, width, child.minimumSize.height)
                    },
                    inContainer,
                )
            }

        assertTrue(
            column.layouts <= area.layouts,
            "the column needs ${column.layouts} validations, a wrapping text area ${area.layouts}",
        )
        assertTrue(
            column.lineBreaks <= area.lineBreaks,
            "the column breaks the text ${column.lineBreaks} times, a wrapping text area ${area.lineBreaks}",
        )
    }

    @Test
    fun aMinimumSizeReadAfterPaintUsesItsNaturalWidth() =
        runComposeSwingTest {
            val ratio = 8
            setContentOf(fixedRectangleParent()) { ratio.toFloat() }
            awaitIdle()
            captureToImage()
            val content = onNodeWithTag("content").fetch<JComponent>()
            content.preferredSize = null
            content.minimumSize = null
            val preferred = content.preferredSize
            val minimum = content.minimumSize
            assertEquals(
                (preferred.width.toFloat() / ratio).roundToInt(),
                minimum.height,
                "minimum height uses natural preferred width",
            )
        }

    /**
     * The first-show and four-paint cost, plus bounds before and after a separate explicit validation.
     */
    private class Cost(
        val layouts: Int,
        val lineBreaks: Int,
        val firstPreferredSize: Dimension?,
        val initialBounds: Rectangle,
        val initialLeafBounds: Rectangle,
        val queriedBounds: Rectangle,
    )

    /**
     * Shows [parent] holding a wrapping text area, in a `Column` where [inContainer] holds, and records the first show
     * and four paints. Then records its bounds after an explicit validation.
     */
    private fun costOf(
        parent: ScriptedStockLayout,
        inContainer: Boolean,
        assertStableAfterQuery: Boolean = false,
    ): Cost {
        lateinit var cost: Cost
        runComposeSwingTest {
            val area =
                LineBreakCountingTextArea().apply {
                    text = WRAPPING_TEXT
                    lineWrap = true
                    wrapStyleWord = true
                }
            setWrappingContent(parent, inContainer, area)
            awaitIdle()
            val host = onNodeWithTag("host").fetch<JComponent>()
            val wrapper = host.getComponent(0)
            val initialBounds = wrapper.bounds
            val initialLeafBounds = area.bounds
            repeat(4) { settleWithPaint() }
            val layouts = parent.layouts
            val lineBreaks = area.lineBreaks
            host.revalidate()
            awaitIdle()
            val queriedBounds = wrapper.bounds
            host.revalidate()
            awaitIdle()
            if (assertStableAfterQuery) {
                assertEquals(queriedBounds, wrapper.bounds, "the next explicit validation is stable")
            }
            cost =
                Cost(
                    layouts,
                    lineBreaks,
                    parent.firstPreferredSize,
                    initialBounds,
                    initialLeafBounds,
                    queriedBounds,
                )
        }
        return cost
    }

    private enum class ParentSizeQueries { TwoWidths, Stretch, FixedRectangle, PreferredWidthLessOne }

    private fun parentQueryingAtTwoWidths(
        grantsFirst: Boolean = false,
        minimum: Boolean = false,
    ) = ScriptedStockLayout { child, width ->
        if (readsAtBothWidths) {
            child.setSize(width, child.height)
            if (minimum) child.minimumSize else child.preferredSize
        }
        child.setSize(width * 2 / 3, child.height)
        val sizePreference = if (minimum) child.minimumSize else child.preferredSize
        child.setBounds(0, 0, width * 2 / 3, sizePreference.height)
        if (grantsFirst) child.setBounds(0, 0, width, child.height)
    }

    private fun stretchingParent(readAtPreferredWidth: Boolean = false) =
        ScriptedStockLayout { child, width ->
            if (readAtPreferredWidth && child.width == 0) child.setSize(child.preferredSize)
            val preferred = child.preferredSize
            if (firstPreferredSize == null) firstPreferredSize = preferred
            child.setBounds(0, 0, width, preferred.height)
        }

    private fun fixedRectangleParent() =
        ScriptedStockLayout(sizeOf = { Dimension(200, 100) }) { child, _ ->
            child.setBounds(0, 0, 200, 100)
        }
}
