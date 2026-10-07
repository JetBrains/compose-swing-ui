package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.layout.SplitPane
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.foundation.graphics.blurOutsets
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.emptyBorder
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.maximumSize
import org.jetbrains.compose.swing.modifier.layout.minimumSize
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Color
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JSplitPane
import javax.swing.JTextArea
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A container receives two argument-less size reads - its preferred size, and the least it can occupy - and
 * both run its measure policy rather than its layout pass. The policy writes one body; which of the two
 * is being read decides only which extent a child that cannot perform a constrained query returns
 * with.
 *
 * This stands in for androidx `foundation-layout`'s own `IntrinsicTest`. An intrinsic query carries its extent
 * through every container of this library, as androidx's does, and stops at the first Swing widget:
 * `getPreferredSize()` and `getMinimumSize()` take no argument. The cases here pin the queries this tree performs,
 * which
 * of the policy's constrained and four intrinsic entry points each of them reaches, and the extent each carries.
 */
class IntrinsicTest {
    @Test
    fun aRowsMinimumSizeIncludesBothChildren() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    ShrinkableChild(0, prefers = 80, shrinksTo = 20)
                    ShrinkableChild(1, prefers = 80, shrinksTo = 30)
                }
            }

            assertEquals(Dimension(50, 30), containerMinimumSize())
            assertEquals(Dimension(160, 80), containerPreferredSize())
        }

    @Test
    fun aNestedRowsMinimumSizeIncludesBothChildren() =
        runComposeSwingTest {
            setContent {
                Column(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Row {
                        ShrinkableChild(0, prefers = 80, shrinksTo = 20)
                        ShrinkableChild(1, prefers = 80, shrinksTo = 30)
                    }
                }
            }

            assertEquals(Dimension(50, 30), containerMinimumSize())
        }

    @Test
    fun aBoxsMinimumSizeIncludesItsLargestChild() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    ShrinkableChild(0, prefers = 80, shrinksTo = 20)
                    ShrinkableChild(1, prefers = 80, shrinksTo = 30)
                }
            }

            assertEquals(Dimension(30, 30), containerMinimumSize())
        }

    @Test
    fun aWeightedRowsMinimumSizeIncludesBothChildren() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    ShrinkableChild(0, prefers = 80, shrinksTo = 20)
                    ShrinkableChild(1, prefers = 80, shrinksTo = 30, modifier = SwingModifier.weight(1f))
                }
            }

            assertEquals(Dimension(50, 30), containerMinimumSize())
        }

    @Test
    fun aBoxsPreferredSizeRespectsANonMatchingChildsMaximum() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Child(0, width = 200, height = 160, modifier = SwingModifier.maximumSize(50, 40))
                }
            }

            assertEquals(
                Dimension(50, 40),
                containerPreferredSize(),
                "a box must not ask for more than its non-matching child explicitly accepts",
            )
        }

    @Test
    fun aWeightedIntrinsicRoundsTheUnitShareBeforeItMultipliesIt() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Label("child", modifier = SwingModifier.weight(2f).preferredSize(1, 1))
                }
            }

            assertEquals(
                Dimension(2, 1),
                containerPreferredSize(),
                "a child one unit wide and weighted 2f needs one rounded unit per weight unit, so its row asks for two",
            )
        }

    @Test
    fun intrinsicQueriesAndLayoutUseDifferentPolicyEntryPoints() {
        for (queryOnly in listOf(true, false)) {
            runComposeSwingTest {
                var measured = 0
                var intrinsicHeight = 0
                val policy =
                    object : MeasurePolicy {
                        override fun MeasureScope.measure(
                            measurables: List<Measurable>,
                            constraints: Constraints,
                        ): MeasureResult {
                            measured++
                            return layout(0, 0) {}
                        }

                        override fun IntrinsicMeasureScope.minIntrinsicWidth(
                            measurables: List<IntrinsicMeasurable>,
                            height: Int,
                        ): Int = 0.also { intrinsicHeight++ }

                        override fun IntrinsicMeasureScope.maxIntrinsicWidth(
                            measurables: List<IntrinsicMeasurable>,
                            height: Int,
                        ): Int = 0.also { intrinsicHeight++ }

                        override fun IntrinsicMeasureScope.minIntrinsicHeight(
                            measurables: List<IntrinsicMeasurable>,
                            width: Int,
                        ): Int = 0.also { intrinsicHeight++ }

                        override fun IntrinsicMeasureScope.maxIntrinsicHeight(
                            measurables: List<IntrinsicMeasurable>,
                            width: Int,
                        ): Int = 0.also { intrinsicHeight++ }
                    }
                setContent {
                    Column(
                        modifier = if (queryOnly) SwingModifier.testTag(CONTAINER_TAG) else containerModifier(200, 200),
                    ) { Layout(measurePolicy = policy) }
                }
                if (queryOnly) {
                    measured = 0
                    intrinsicHeight = 0
                    containerPreferredSize()
                    containerMinimumSize()
                    assertEquals(0, measured, "intrinsic queries must not measure a nested container")
                    assertTrue(intrinsicHeight > 0, "the nested container performs its own intrinsic reads")
                } else {
                    assertTrue(measured > 0, "a layout pass performs a constrained query")
                }
            }
        }
    }

    @Test
    fun aNestedContainerReportsItsIntrinsicHeightAtTheQueriedWidth() =
        runComposeSwingTest {
            var intrinsicHeight = -1
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(320, 600)) {
                    Layout(
                        content = {
                            Column { Label("Preview", modifier = SwingModifier.fillMaxWidth().aspectRatio(2f)) }
                        },
                        modifier = SwingModifier.center(),
                        measurePolicy = { measurables, constraints ->
                            intrinsicHeight = measurables.single().maxIntrinsicHeight(320)
                            val placeable = measurables.single().measure(Constraints(maxWidth = constraints.maxWidth))
                            layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
                        },
                    )
                }
            }

            assertEquals(
                160,
                intrinsicHeight,
                "the column returns a result for its ratio child at the specified width, as androidx does",
            )
        }

    @Test
    fun anIntrinsicHeightReachesARatioChildThroughANestedContainer() =
        runComposeSwingTest {
            setContent {
                CenteredColumn(320) {
                    Box(modifier = SwingModifier.testTag("direct").fillMaxWidth().height(IntrinsicSize.Max)) {
                        Label("Preview", modifier = SwingModifier.fillMaxWidth().aspectRatio(2f))
                    }
                    Box(modifier = SwingModifier.testTag("nested").fillMaxWidth().height(IntrinsicSize.Max)) {
                        Column { Label("Preview", modifier = SwingModifier.fillMaxWidth().aspectRatio(2f)) }
                    }
                }
            }

            assertEquals(
                Rectangle(0, 0, 320, 160),
                onNodeWithTag("direct").fetch<JComponent>().bounds,
                "a box holding the ratio child takes the ratio's height at its width",
            )
            assertEquals(
                Rectangle(0, 160, 320, 160),
                onNodeWithTag("nested").fetch<JComponent>().bounds,
                "each box takes its content's height at its width, so the column stacks them, as androidx does",
            )
        }

    @Test
    fun aRowReadsAnUnweightedChildsHeightAtItsMaxIntrinsicWidthAsAndroidxRowColumnImplDoes() {
        for (size in IntrinsicSize.entries) {
            runComposeSwingTest {
                setContent {
                    CenteredColumn(400) {
                        Box(modifier = SwingModifier.width(320).height(size)) {
                            Row {
                                Column(modifier = SwingModifier.padding(4)) {
                                    Label(
                                        "Preview",
                                        modifier = SwingModifier.testTag("label").fillMaxWidth().aspectRatio(2f),
                                    )
                                }
                            }
                        }
                        Label("After", modifier = SwingModifier.testTag("after"))
                    }
                }
                val labelHeight = onNodeWithTag("label").fetch<JComponent>().preferredSize.height

                assertEquals(
                    labelHeight + 8,
                    onNodeWithTag("after").fetch<JComponent>().y,
                    "$size: androidx RowColumnImpl's intrinsicCrossAxisSize reads the unweighted column's height at " +
                        "its max intrinsic width, twice the label's height of $labelHeight, so the box is that " +
                        "height plus the padding rather than the ratio's height at the 320 it offers",
                )
            }
        }
    }

    @Test
    fun aMinIntrinsicWidthReachesARatioChildThroughANestedContainer() =
        runComposeSwingTest {
            setContent {
                CenteredColumn(320) {
                    Box(modifier = SwingModifier.testTag("box").height(80).width(IntrinsicSize.Min)) {
                        Box { Label("Preview", modifier = SwingModifier.fillMaxHeight().aspectRatio(2f)) }
                    }
                }
            }

            assertEquals(
                Rectangle(0, 0, 160, 80),
                onNodeWithTag("box").fetch<JComponent>().bounds,
                "the box takes its content's min intrinsic width at its height, the ratio's, as androidx does",
            )
        }

    @Test
    fun aLayoutModifierQueryingADefaultHookNarrowerThanTheOfferReportsThatSize() {
        for (size in IntrinsicSize.entries) {
            runComposeSwingTest {
                setContent {
                    CenteredColumn(320) {
                        Box(modifier = SwingModifier.testTag("box").fillMaxWidth().height(size)) {
                            TextArea(
                                WRAPPING_TEXT,
                                {},
                                SwingModifier.testTag("text").fillMaxWidth() then HorizontalInsetElement,
                                lineWrap = true,
                                wrapStyleWord = true,
                            )
                        }
                    }
                }
                val text = onNodeWithTag("text").fetch<JTextArea>()
                val atWidth = if (size == IntrinsicSize.Max) text.preferredSize else text.minimumSize

                assertEquals(312, text.width, "$size: the area fills the box inside its inset")
                assertEquals(
                    atWidth.height,
                    onNodeWithTag("box").fetch<JComponent>().height,
                    "$size: the box takes the area's height at the width the inset reads a default hook at",
                )
            }
        }
    }

    @Test
    fun aBaselineReadThroughAWrapContentHeightPlacesAWrappingTextAreaAtItsHeightAtTheShare() =
        runComposeSwingTest {
            setContent {
                CenteredColumn(320) {
                    Row(modifier = SwingModifier.testTag("row").fillMaxWidth().height(IntrinsicSize.Max)) {
                        SwingNode(
                            factory = {
                                JTextArea(WRAPPING_TEXT).apply {
                                    columns = 10
                                    lineWrap = true
                                    wrapStyleWord = true
                                }
                            },
                            modifier =
                                SwingModifier
                                    .testTag("text")
                                    .weight(1f)
                                    .alignByBaseline()
                                    .height(200)
                                    .wrapContentHeight(Alignment.Bottom),
                        )
                        Label("Side", modifier = SwingModifier.alignByBaseline().padding(top = 300))
                    }
                }
            }
            val text = onNodeWithTag("text").fetch<JTextArea>()

            assertEquals(text.preferredSize.height, text.height, "the area takes its height at its share")
            assertEquals(
                text.y + text.height,
                onNodeWithTag("row").fetch<JComponent>().height,
                "the row ends where the area does: its baseline read puts the area at its share's height",
            )
        }

    @Test
    fun aDividerInARowAtItsMinIntrinsicHeightSpansItsTallestNestedContainer() =
        runComposeSwingTest {
            setContent {
                CenteredColumn(321) {
                    Row(modifier = SwingModifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        Column(modifier = SwingModifier.testTag("start").weight(1f)) {
                            Label("Preview", modifier = SwingModifier.fillMaxWidth().aspectRatio(4f))
                        }
                        Box(modifier = SwingModifier.testTag("divider").fillMaxHeight().width(1)) {}
                        Column(modifier = SwingModifier.weight(1f)) { Label("End") }
                    }
                }
            }

            assertEquals(
                Rectangle(0, 0, 160, 40),
                onNodeWithTag("start").fetch<JComponent>().bounds,
                "the weighted column takes its share of the row, and its ratio child's height at that share",
            )
            assertEquals(
                Rectangle(160, 0, 1, 40),
                onNodeWithTag("divider").fetch<JComponent>().bounds,
                "the row is as tall as the column's min intrinsic height at its share, so the divider spans it",
            )
        }

    @Test
    fun aConstrainableLeafSuppliesItsColumnsIntrinsicSizes() =
        runComposeSwingTest {
            val leaf = HalfAsTallAsWide()
            setContent {
                Panel(PanelLayout.Flow()) {
                    Column(modifier = SwingModifier.testTag(CONTAINER_TAG)) { SwingNode(factory = { leaf }) }
                }
            }

            assertEquals(
                Dimension(HalfAsTallAsWide.WIDTH, HalfAsTallAsWide.WIDTH / 2),
                containerPreferredSize(),
                "the column prefers the leaf's intrinsic width, and the leaf's height at that width",
            )
            assertEquals(
                Rectangle(0, 0, HalfAsTallAsWide.WIDTH, HalfAsTallAsWide.WIDTH / 2),
                leaf.bounds,
                "the column lays the leaf out at the size it prefers",
            )
        }

    @Test
    fun aConstrainableLeafIsMeasuredOnceForItsHeightAtTheWidthItIsGranted() =
        runComposeSwingTest {
            val leaf = HalfAsTallAsWide()
            setContent {
                CenteredColumn(320) {
                    Box(modifier = SwingModifier.testTag("box").fillMaxWidth().height(IntrinsicSize.Max)) {
                        Column { SwingNode(factory = { leaf }, modifier = SwingModifier.fillMaxWidth()) }
                    }
                }
            }
            leaf.measures = 0

            onNodeWithTag("box").fetch<JComponent>().revalidate()
            awaitIdle()

            assertEquals(
                Rectangle(0, 0, 320, 160),
                onNodeWithTag("box").fetch<JComponent>().bounds,
                "the box takes the leaf's intrinsic height at the width it fills",
            )
            assertEquals(Rectangle(0, 0, 320, 160), leaf.bounds, "the leaf takes its height at the width it is granted")
            assertEquals(1, leaf.measures, "in the one measure of the pass that lays it out")
        }

    @Test
    fun anExplicitContainerSizeAlsoDeterminesItsConstrainedSize() =
        runComposeSwingTest {
            setContent {
                Row(modifier = containerModifier(400, 100)) {
                    Row(modifier = SwingModifier.preferredSize(200, 60)) { SizedChild(0) }
                }
            }

            assertEquals(
                listOf(Rectangle(0, 0, 200, 60)),
                childBounds(),
                "a size set on a container returns a result for it whatever the query, so the inner row occupies " +
                    "the size it was given rather than the one its own policy works out for its child",
            )
        }

    @Test
    fun aSplitPaneTakesItsDividerLimitFromTheMinimumTheRowsPolicyWorksOut() =
        runComposeSwingTest {
            setContent {
                SplitPane(modifier = SwingModifier.testTag(CONTAINER_TAG).preferredSize(400, 100)) {
                    Row(modifier = SwingModifier.first()) {
                        ShrinkableChild(0, prefers = 80, shrinksTo = 20)
                        ShrinkableChild(1, prefers = 80, shrinksTo = 30)
                    }
                    Label(text = "second", modifier = SwingModifier.second())
                }
            }

            val pane = onNodeWithTag(CONTAINER_TAG).fetch<JSplitPane>()
            assertEquals(
                50,
                pane.minimumDividerLocation - pane.insets.left,
                "a split pane holds its divider clear of what the side it splits can shrink to, and that " +
                    "extent is what the row's own policy works out in its minimum mode",
            )
        }

    @Test
    fun aContainersSwingSizesCarryThePaintOutsetsOfItsContentAndItsIntrinsicSizesLeaveThemOut() =
        runComposeSwingTest {
            setContent {
                Row {
                    Box(modifier = SwingModifier.testTag("box").emptyBorder(3)) {
                        Box(modifier = SwingModifier.size(100, 50).shadow(6, Color.BLACK))
                    }
                }
            }
            val box = onNodeWithTag("box").fetch<JComponent>() as Constrainable
            val outsets = 2 * (blurOutsets(6) - 3)

            assertEquals(106, box.maxIntrinsicWidth(Constraints.Infinity), "the content's width plus the border")
            assertEquals(56, box.maxIntrinsicHeight(106), "the content's height plus the border")
            assertEquals(106, box.minIntrinsicWidth(Constraints.Infinity), "the content's width plus the border")
            assertEquals(56, box.minIntrinsicHeight(106), "the content's height plus the border")
            assertEquals(
                Dimension(106 + outsets, 56 + outsets),
                (box as JComponent).preferredSize,
                "the preferred size is the intrinsic size grown by the shadow's reach past the border",
            )
            assertEquals(
                Dimension(106 + outsets, 56 + outsets),
                box.minimumSize,
                "the minimum size is the intrinsic size grown by the shadow's reach past the border",
            )
        }

    @Test
    fun aBorderedContainersIntrinsicReadAtAnUnboundedExtentReadsItsContentAtAnUnboundedExtent() =
        runComposeSwingTest {
            setContent {
                Row {
                    Box(modifier = SwingModifier.testTag("box").emptyBorder(3)) {
                        SwingNode(factory = { HalfAsTallAsWide() })
                    }
                }
            }
            val box = onNodeWithTag("box").fetch<JComponent>() as Constrainable

            assertEquals(
                HalfAsTallAsWide.WIDTH / 2 + 6,
                box.maxIntrinsicHeight(Constraints.Infinity),
                "the content's height where nothing bounds its width, plus the border",
            )
            assertEquals(
                HalfAsTallAsWide.WIDTH + 6,
                box.maxIntrinsicWidth(Constraints.Infinity),
                "the content's width where nothing bounds its height, plus the border",
            )
        }

    @Test
    fun theIntrinsicHeightInsideAContainersPaintOutsetsPlusThemIsTheHeightItTakesAtItsWidth() =
        runComposeSwingTest {
            setContent {
                Row {
                    Box(modifier = SwingModifier.testTag("box").width(200).emptyBorder(3)) {
                        Box(modifier = SwingModifier.fillMaxWidth().aspectRatio(2f).shadow(6, Color.BLACK))
                    }
                }
            }
            val box = onNodeWithTag("box").fetch<JComponent>()
            val insets = box.insets
            val border = box.border.getBorderInsets(box)
            val horizontalOutsets = insets.left + insets.right - border.left - border.right
            val verticalOutsets = insets.top + insets.bottom - border.top - border.bottom

            assertEquals(
                2 * (blurOutsets(6) - 3),
                horizontalOutsets,
                "the content's shadow gives the box paint outsets of its reach past the border",
            )
            assertEquals(
                box.height,
                (box as Constrainable).maxIntrinsicHeight(box.width - horizontalOutsets) + verticalOutsets,
                "the intrinsic height at the width less the paint outsets, plus them, is the height the box takes",
            )
        }

    @Test
    fun aContainerReadsTheHeightOfAValidNestedContainerAtTheWidthItPrefersFromThePreferredSizeSwingHolds() =
        runComposeSwingTest {
            var sibling by mutableStateOf("a")
            val intrinsicHeightWidths = ArrayList<Int>()
            val nested =
                object : MeasurePolicy {
                    override fun MeasureScope.measure(
                        measurables: List<Measurable>,
                        constraints: Constraints,
                    ): MeasureResult {
                        val placeable = measurables.single().measure(constraints)
                        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }

                    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
                        measurables: List<IntrinsicMeasurable>,
                        width: Int,
                    ): Int {
                        intrinsicHeightWidths += width
                        return measurables.single().maxIntrinsicHeight(width)
                    }
                }
            setContent {
                Panel(PanelLayout.Flow()) {
                    Box {
                        Layout(
                            content = { Label("The widest child") },
                            modifier = SwingModifier.testTag("nested"),
                            measurePolicy = nested,
                        )
                        Box { Label(sibling) }
                    }
                }
            }
            captureToImage()
            awaitIdle()
            val nestedWidth = onNodeWithTag("nested").fetch<JComponent>().width
            intrinsicHeightWidths.clear()

            sibling = "b"
            awaitIdle()

            assertEquals(
                1,
                intrinsicHeightWidths.count { it == nestedWidth },
                "the nested policy works out its height at the width it prefers once, for the preferred size Swing " +
                    "holds, which the box's queries at that width read; it was read at $intrinsicHeightWidths",
            )
        }

    @Test
    fun aValidNestedContainersIntrinsicHeightUnderAMinimumWidthUsesThatWidthRatherThanItsPreferredSize() =
        runComposeSwingTest {
            var required by mutableStateOf(false)
            setContent {
                Panel(PanelLayout.Flow()) {
                    Box(modifier = SwingModifier.testTag("outer")) {
                        Box(
                            modifier =
                                SwingModifier
                                    .testTag("nested")
                                    .then(if (required) SwingModifier.requiredWidthIn(min = 200) else SwingModifier),
                            propagateMinConstraints = true,
                        ) {
                            Box(modifier = SwingModifier.width(200))
                            TextArea(
                                WRAPPING_TEXT,
                                {},
                                SwingModifier.testTag("text"),
                                lineWrap = true,
                                wrapStyleWord = true,
                            )
                        }
                    }
                }
            }
            captureToImage()
            awaitIdle()
            val text = onNodeWithTag("text").fetch<JTextArea>()
            assertTrue(text.width < 200, "the area holds a width narrower than the 200 the nested box prefers")

            required = true
            awaitIdle()

            assertEquals(200, text.width, "the least width the nested box is given reaches the area")
            assertEquals(text.preferredSize.height, text.height, "which takes the height it prefers at that width")
            assertEquals(
                text.height,
                onNodeWithTag("outer").fetch<JComponent>().height,
                "and the outer box, reading the nested one its height under that least width, is as tall",
            )
        }
}

/** A [Column] centered in a border panel [width] wide and 600 high. */
@Composable
private fun CenteredColumn(
    width: Int,
    content: @Composable ColumnScope.() -> Unit,
) {
    Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(width, 600)) {
        Column(modifier = SwingModifier.center(), content = content)
    }
}

/** A component of its own returning constrained and intrinsic queries with a height half its width. */
internal class HalfAsTallAsWide :
    JComponent(),
    Constrainable {
    var measures = 0

    override var constrainedWidth: Int = 0
        private set

    override var constrainedHeight: Int = 0
        private set

    override fun measure(constraints: Constraints) {
        measures++
        constrainedWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else WIDTH
        constrainedHeight = constraints.constrainHeight(constrainedWidth / 2)
    }

    override fun minIntrinsicWidth(height: Int): Int = maxIntrinsicWidth(height)

    override fun maxIntrinsicWidth(height: Int): Int = if (height == Constraints.Infinity) WIDTH else height * 2

    override fun minIntrinsicHeight(width: Int): Int = maxIntrinsicHeight(width)

    override fun maxIntrinsicHeight(width: Int): Int = (if (width == Constraints.Infinity) WIDTH else width) / 2

    companion object {
        /** The width it takes where nothing bounds it. */
        const val WIDTH = 100
    }
}

/**
 * Takes [HorizontalInsetNode.INSET] off the width its child is offered, and returns its height queries through a
 * plain node's default hooks at the width the child gets.
 */
private data object HorizontalInsetElement : LayoutModifierNodeElement<HorizontalInsetNode>() {
    override fun create(): HorizontalInsetNode = HorizontalInsetNode()

    override fun update(node: HorizontalInsetNode): Unit = Unit
}

private class HorizontalInsetNode : LayoutModifierNode() {
    private val plain =
        object : LayoutModifierNode() {
            override fun MeasureScope.measure(
                measurable: Measurable,
                constraints: Constraints,
            ): MeasureResult {
                val placeable = measurable.measure(constraints)
                return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
        }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints.offset(horizontal = -INSET))
        return layout(placeable.width + INSET, placeable.height) { placeable.place(0, 0) }
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int = with(plain) { minIntrinsicHeight(measurable, width - INSET) }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int = with(plain) { maxIntrinsicHeight(measurable, width - INSET) }

    companion object {
        const val INSET = 8
    }
}

/** A child that prefers one extent on both axes and declares it can shrink to another. */
@Composable
private fun ShrinkableChild(
    index: Int,
    prefers: Int,
    shrinksTo: Int,
    modifier: SwingModifier = SwingModifier,
) {
    Label(
        "child $index",
        modifier = modifier.preferredSize(prefers, prefers).minimumSize(shrinksTo, shrinksTo),
    )
}
