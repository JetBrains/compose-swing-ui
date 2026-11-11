// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.style.alignmentX
import org.jetbrains.compose.swing.style.alignmentY
import org.jetbrains.compose.swing.style.border
import org.jetbrains.compose.swing.style.minimumSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.style.visible
import org.jetbrains.compose.swing.test.interaction.assertIsOfType
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import javax.swing.border.EmptyBorder
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What a [Row], a [Column] or a [Box] serves the layout above it, which is everything a parent can ask
 * of a container before it decides where to put it: the smallest extent it can be laid out at, the
 * largest, the alignment its content lines up on, and the extent it prefers.
 *
 * A container's minimum carries weight beyond a parent that honors it: `SplitPane` clamps divider travel
 * against it, so a minimum read off the wrong extent either refuses the divider space it has or lets it
 * crush content that said it could not shrink.
 */
class RowColumnParentQueryTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("columnMinimumCases")
    fun aColumnsMinimumStacksItsChildrensMinimaIncludingWeightsAndArrangementGaps(
        name: String,
        arrangement: Arrangement.Vertical,
        children: List<Pair<Int, ColumnScope.() -> SwingModifier>>,
        expected: Dimension,
    ) = runComposeSwingTest {
        setContent {
            Column(
                modifier = SwingModifier.testTag(CONTAINER_TAG),
                verticalArrangement = arrangement,
            ) {
                for ((index, child) in children.withIndex()) {
                    SizedChild(index, child.second(this), style = { minimumSize(18, child.first) })
                }
            }
        }
        onNodeWithTag(CONTAINER_TAG).assertProperty(
            expected,
            "$name: the minimum stacks the children's minimum heights and keeps each arrangement gap",
        ) { minimumSize }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parentQueryContainerCases")
    fun aRowAColumnAndABoxTakeAnyExtentTheyAreOffered(
        name: String,
        content: @Composable (@Composable () -> Unit) -> Unit,
    ) = runComposeSwingTest {
        setContent { content { SizedChild(0) } }
        onNodeWithTag(name).assertProperty(
            Dimension(Int.MAX_VALUE, Int.MAX_VALUE),
            "'$name' must offer its parent no ceiling: it places whatever extent it is given rather " +
                "than refusing the surplus",
        ) { maximumSize }
    }

    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("firstDeclaredAlignmentCases")
    fun aContainerReportsItsFirstDeclaredChildsAlignmentOrCenteredAlignmentWhenEmpty(
        tag: String,
        scenario: String,
        children: List<Triple<Float, Float, Boolean>>,
        expectedX: Float,
        expectedY: Float,
    ) = runComposeSwingTest {
        val declaring = ParentQueryContainers.getValue(tag)
        setContent {
            declaring {
                for ((index, child) in children.withIndex()) {
                    val (x, y, hidden) = child
                    SizedChild(
                        index,
                        style = {
                            alignmentX(x)
                            alignmentY(y)
                            if (hidden) visible(false)
                        },
                    )
                }
            }
        }
        onNodeWithTag(tag).assertProperty(expectedX, "$tag, $scenario: parent x alignment") { alignmentX }
        onNodeWithTag(tag).assertProperty(expectedY, "$tag, $scenario: parent y alignment") { alignmentY }
    }

    /** A container's declaration order, and so the child a parent query asks, survives removal and insertion. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("containerTags")
    fun aContainerFollowsItsFirstDeclaredChildThroughRemovalAndInsertion(tag: String) =
        runComposeSwingTest {
            val declaring = ParentQueryContainers.getValue(tag)
            var step by mutableIntStateOf(0)
            setContent { declaring { OrderedChildren(presentA = step == 0, presentC = step == 2) } }

            val stages =
                listOf(
                    0f to "before A is removed",
                    1f to "once A is removed",
                    Component.CENTER_ALIGNMENT to "once C is declared first",
                )
            for ((index, stage) in stages.withIndex()) {
                step = index
                awaitIdle()
                val (alignment, state) = stage
                onNodeWithTag(tag).assertProperty(
                    alignment,
                    "$tag reports its first declared child's alignment $state",
                ) { alignmentX }
            }
        }

    /** Restacking a child by z-index changes the paint and hit-testing order, not which child a parent query asks. */
    @Test
    fun aBoxRestackedByAZIndexStillReportsItsFirstDeclaredChild() =
        runComposeSwingTest {
            var z by mutableFloatStateOf(0f)
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    SizedChild(
                        0,
                        SwingModifier.zIndex(z),
                        style = {
                            alignmentX(0f)
                            alignmentY(1f)
                        },
                    )
                    SizedChild(1, style = { alignmentX(Component.CENTER_ALIGNMENT) })
                    SizedChild(2, style = { alignmentX(1f) })
                }
            }

            for (zIndex in listOf(0f, 5f)) {
                z = zIndex
                awaitIdle()
                onNodeWithTag(CONTAINER_TAG).assertProperty(
                    0f to 1f,
                    "z-index=$zIndex: restacking keeps the first declared child's alignment on both axes",
                ) { alignmentX to alignmentY }
            }
        }

    /** A custom layout's parent query follows the declaration order of its content, not the order its policy places. */
    @Test
    fun aCustomLayoutReportsTheAlignmentItsFirstChildReports() =
        runComposeSwingTest {
            setContent {
                Layout(
                    modifier = SwingModifier.testTag(CONTAINER_TAG),
                    content = {
                        SizedChild(
                            0,
                            style = {
                                alignmentX(0f)
                                alignmentY(1f)
                            },
                        )
                        SizedChild(
                            1,
                            style = {
                                alignmentX(1f)
                                alignmentY(0f)
                            },
                        )
                    },
                    measurePolicy = { measurables, constraints ->
                        val placeables = measurables.map { it.measure(constraints) }
                        layout(CHILD_WIDTH, CHILD_HEIGHT) {
                            placeables[1].place(0, 0)
                            placeables[0].place(0, 0)
                        }
                    },
                )
            }

            onNodeWithTag(CONTAINER_TAG)
                .assertProperty(
                    0f,
                    "a custom layout must report its first declared child's x alignment",
                ) { alignmentX }
                .assertProperty(1f, "and its y alignment, not the order it placed them in") { alignmentY }
        }

    @Test
    fun aColumnsBorderInsetsReachBothTheExtentsItAsksFor() =
        runComposeSwingTest {
            setContent {
                Column(
                    modifier =
                        SwingModifier.testTag(CONTAINER_TAG).styleable {
                            border(EmptyBorder(5, 10, 15, 20))
                        },
                ) {
                    SizedChild(0, style = { minimumSize(18, 12) })
                }
            }

            onNodeWithTag(CONTAINER_TAG)
                .assertProperty(
                    Dimension(CHILD_WIDTH + 10 + 20, CHILD_HEIGHT + 5 + 15),
                    "the space the border takes must be added to the extent the column prefers, or its child is " +
                        "laid out in less width and height than it asked for",
                ) { preferredSize }
                .assertProperty(
                    Dimension(18 + 10 + 20, 12 + 5 + 15),
                    "and to the extent it can shrink to, which the border does not shrink with it",
                ) { minimumSize }
        }

    /**
     * Each [SIZING_MODIFIERS] entry on a child, with a real bound on the container and with none at all
     * (`wrapContentSize(unbounded = true)`, which is the only way this Swing-packed harness decouples a container's
     * real layout from its own preferred size - a single-axis `wrapContentWidth`/`wrapContentHeight` still leaves the
     * other axis tied to the window's pack-to-preferred-size real bound, so it never differs from the bounded case
     * here).
     * The container is laid out at the size it prefers, or, for an entry in the case's androidx sizes, at the sizes
     * listed there.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("preferredSizeCaseArguments")
    internal fun aContainerIsLaidOutAtItsPreferredSizeExceptWhereAndroidxLaysItOutElsewhere(
        case: PreferredSizeCase,
        declare: ConstrainedScope.() -> SwingModifier,
        bound: ConstrainedScope.() -> SwingModifier,
        key: String,
    ) = runComposeSwingTest {
        setContent { case.content(declare, bound) }
        val container = onNodeWithTag(CONTAINER_TAG)
        val androidxSizes = case.androidxSizes[key]
        if (androidxSizes == null) {
            val live = container.fetch()
            assertEquals(live.preferredSize, live.size, "$key: ${case.message}")
        } else {
            container.assertProperty(androidxSizes, "$key: ${case.message}") {
                PreferredAndLaidOut(preferredSize, size)
            }
        }
    }

    /** A row sizing itself reads a child's baseline where each kind of layout node moves it, as its layout does. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("baselineMovingModifierCases")
    fun aRowPrefersItsMeasuredHeightAndLinesUpBaselinesWhateverLayoutNodeMovesOne(
        name: String,
        declare: RowScope.() -> SwingModifier,
        expected: Pair<Int, Int>,
    ) = runComposeSwingTest {
        setContent {
            Column {
                Row(modifier = SwingModifier.testTag(name)) {
                    DecoratedBaselineChild(10, SwingModifier.alignByBaseline())
                    DecoratedBaselineChild(10, declare().alignByBaseline())
                }
            }
        }
        val (height, baseline) = expected
        onNodeWithTag(name).assertProperty(height, "$name: row height") { this.height }
        for (index in 0..1) {
            onNodeWithTag(name).assertIsOfType<Container>().assertProperty(baseline, "$name: child $index baseline") {
                val child = childrenInDeclarationOrder()[index]
                child.y + child.getBaseline(child.width, child.height)
            }
        }
    }

    /** Children whose first declared one changes as A is removed and C is inserted ahead of B. */
    @Composable
    private fun OrderedChildren(
        presentA: Boolean,
        presentC: Boolean,
    ) {
        if (presentC) SizedChild(2, style = { alignmentX(Component.CENTER_ALIGNMENT) })
        if (presentA) SizedChild(0, style = { alignmentX(0f) })
        SizedChild(1, style = { alignmentX(1f) })
    }

    companion object {
        private const val ROW_TAG = "row"
        private const val COLUMN_TAG = "column"
        private const val BOX_TAG = "box"

        private val ParentQueryContainers: Map<String, @Composable (@Composable () -> Unit) -> Unit> =
            mapOf(
                ROW_TAG to { content -> Row(modifier = SwingModifier.testTag(ROW_TAG)) { content() } },
                COLUMN_TAG to { content -> Column(modifier = SwingModifier.testTag(COLUMN_TAG)) { content() } },
                BOX_TAG to { content -> Box(modifier = SwingModifier.testTag(BOX_TAG)) { content() } },
            )

        @JvmStatic
        fun columnMinimumCases(): List<Arguments> {
            val plain: ColumnScope.() -> SwingModifier = { SwingModifier }
            val weighted: ColumnScope.() -> SwingModifier = { SwingModifier.weight(1f) }
            return listOf(
                Arguments.of("stacked", Arrangement.Top, List(CHILD_COUNT) { 12 to plain }, Dimension(18, 36)),
                Arguments.of(
                    "spaced",
                    Arrangement.spacedBy(8),
                    List(CHILD_COUNT) { 12 to plain },
                    Dimension(18, 52),
                ),
                Arguments.of(
                    "weighted",
                    Arrangement.Top,
                    listOf(12 to plain, 30 to weighted),
                    Dimension(18, 42),
                ),
            )
        }

        @JvmStatic
        fun parentQueryContainerCases(): List<Arguments> =
            ParentQueryContainers.map { (tag, content) -> Arguments.of(tag, content) }

        @JvmStatic
        fun containerTags(): List<Arguments> = ParentQueryContainers.keys.map { Arguments.of(it) }

        @JvmStatic
        fun firstDeclaredAlignmentCases(): List<Arguments> {
            val center = Component.CENTER_ALIGNMENT
            val scenarios =
                listOf(
                    Triple(
                        "first declared",
                        listOf(Triple(0f, 1f, false), Triple(1f, 0f, false)),
                        0f to 1f,
                    ),
                    Triple(
                        "hidden",
                        listOf(Triple(1f, 0f, true), Triple(0f, 1f, false)),
                        1f to 0f,
                    ),
                    Triple("empty", emptyList<Triple<Float, Float, Boolean>>(), center to center),
                )
            return ParentQueryContainers.keys.flatMap { tag ->
                scenarios.map { (scenario, children, expected) ->
                    Arguments.of(tag, scenario, children, expected.first, expected.second)
                }
            }
        }

        @JvmStatic
        fun preferredSizeCaseArguments(): List<Arguments> =
            preferredSizeCases().flatMap { case ->
                SIZING_MODIFIERS.flatMap { (name, declare) ->
                    listOf<Pair<String, ConstrainedScope.() -> SwingModifier>>(
                        "bounded" to { SwingModifier },
                        "unbounded" to { SwingModifier.wrapContentSize(unbounded = true) },
                    ).map { (extent, bound) ->
                        val key = "$name, $extent"
                        Arguments.of(Named.of("${case.name}: $key", case), declare, bound, key)
                    }
                }
            }

        @JvmStatic
        fun baselineMovingModifierCases(): List<Arguments> =
            BASELINE_MOVING_MODIFIERS.map { (name, declare, expected) ->
                Arguments.of(name, declare, expected)
            }
    }
}

/**
 * Every built-in layout modifier whose extent along one axis can follow the other axis or the constraints its child is
 * offered, under the name a failure reports it by.
 */
private val SIZING_MODIFIERS: List<Pair<String, ConstrainedScope.() -> SwingModifier>> =
    listOf(
        "aspectRatio(2f)" to { SwingModifier.aspectRatio(2f) },
        "aspectRatio(2f, matchHeightConstraintsFirst)" to { SwingModifier.aspectRatio(2f, true) },
        "aspectRatio(0.5f)" to { SwingModifier.aspectRatio(0.5f) },
        "aspectRatio(2f).padding(5)" to { SwingModifier.aspectRatio(2f).padding(5) },
        "padding(5).aspectRatio(2f)" to { SwingModifier.padding(5).aspectRatio(2f) },
        "width(Min)" to { SwingModifier.width(IntrinsicSize.Min) },
        "width(Max)" to { SwingModifier.width(IntrinsicSize.Max) },
        "height(Min)" to { SwingModifier.height(IntrinsicSize.Min) },
        "height(Max)" to { SwingModifier.height(IntrinsicSize.Max) },
        "requiredWidth(Max)" to { SwingModifier.requiredWidth(IntrinsicSize.Max) },
        "requiredHeight(Min)" to { SwingModifier.requiredHeight(IntrinsicSize.Min) },
        "fillMaxWidth" to { SwingModifier.fillMaxWidth() },
        "fillMaxHeight" to { SwingModifier.fillMaxHeight() },
        "fillMaxSize" to { SwingModifier.fillMaxSize() },
        "wrapContentSize" to { SwingModifier.wrapContentSize() },
        "wrapContentSize(unbounded)" to { SwingModifier.wrapContentSize(unbounded = true) },
        "size(70, 30)" to { SwingModifier.size(70, 30) },
        "requiredSize(70, 30)" to { SwingModifier.requiredSize(70, 30) },
        "sizeIn(min 60 x 50)" to { SwingModifier.sizeIn(minWidth = 60, minHeight = 50) },
        "sizeIn(max 30 x 20)" to { SwingModifier.sizeIn(maxWidth = 30, maxHeight = 20) },
        "defaultMinSize(60, 50)" to { SwingModifier.defaultMinSize(60, 50) },
        "padding(3, 5, 7, 11)" to { SwingModifier.padding(start = 3, top = 5, end = 7, bottom = 11) },
        "offset(5, 7)" to { SwingModifier.offset(5, 7) },
    )

/** The size a container prefers and the size its parent lays it out at. */
internal data class PreferredAndLaidOut(
    val preferred: Dimension,
    val laidOut: Dimension,
)

/**
 * The [SIZING_MODIFIERS] entries a [Row] with no bound at all prefers at one size and is laid out at another, as
 * androidx does: offered no constraint, `AspectRatioNode` finds no size and measures its content unchanged, so the
 * baseline-aligned child keeps its own size and the row lays it out at the height the two baselines need, while
 * the row's max intrinsic height asks the ratio for the child's height at the child's intrinsic width. A row's
 * preferred height also holds the child's baseline, read through `intrinsicPlaceable`, where androidx's intrinsics
 * leave alignment lines out.
 *
 * A bounded row's `fillMaxHeight`/`fillMaxSize` child is measured unbounded for the row's own preferred size, where
 * filling is a no-op, but stretches to the row's real, packed offer once laid out; its fixed baseline does not move
 * with that growth, so the row's cross-axis size, held to the deepest baseline's before and after extent, grows
 * past what it preferred.
 */
private val ROW_OFF_PREFERRED_SIZES: Map<String, PreferredAndLaidOut> =
    mapOf(
        "aspectRatio(2f), unbounded" to PreferredAndLaidOut(Dimension(100, 45), Dimension(100, 60)),
        "aspectRatio(2f, matchHeightConstraintsFirst), unbounded" to
            PreferredAndLaidOut(Dimension(100, 45), Dimension(100, 60)),
        "aspectRatio(0.5f), unbounded" to PreferredAndLaidOut(Dimension(100, 120), Dimension(100, 60)),
        "aspectRatio(2f).padding(5), unbounded" to PreferredAndLaidOut(Dimension(110, 45), Dimension(110, 65)),
        "padding(5).aspectRatio(2f), unbounded" to PreferredAndLaidOut(Dimension(110, 50), Dimension(110, 65)),
        "fillMaxHeight, bounded" to PreferredAndLaidOut(Dimension(100, 60), Dimension(100, 80)),
        "fillMaxSize, bounded" to PreferredAndLaidOut(Dimension(100, 60), Dimension(100, 80)),
    )

/**
 * The [SIZING_MODIFIERS] entries a [Column] with no bound at all prefers at one size and is laid out at another, as
 * androidx does; see [ROW_OFF_PREFERRED_SIZES]. The column's max intrinsic width asks the ratio for the child's
 * width at the child's intrinsic height, and its max intrinsic height reads the ratio for the child's height at the
 * column's intrinsic width, while its measure, offered no constraint, keeps the child's own size.
 */
private val COLUMN_ANDROIDX_SIZES: Map<String, PreferredAndLaidOut> =
    mapOf(
        "aspectRatio(2f), unbounded" to PreferredAndLaidOut(Dimension(80, 80), Dimension(50, 80)),
        "aspectRatio(2f, matchHeightConstraintsFirst), unbounded" to
            PreferredAndLaidOut(Dimension(80, 80), Dimension(50, 80)),
        "aspectRatio(0.5f), unbounded" to PreferredAndLaidOut(Dimension(50, 140), Dimension(50, 80)),
        "aspectRatio(2f).padding(5), unbounded" to PreferredAndLaidOut(Dimension(100, 90), Dimension(60, 90)),
        "padding(5).aspectRatio(2f), unbounded" to PreferredAndLaidOut(Dimension(90, 90), Dimension(60, 90)),
    )

/** The container whose size a case checks, the sizes androidx lays an entry out at, and what a failure says. */
internal class PreferredSizeCase(
    val name: String,
    val androidxSizes: Map<String, PreferredAndLaidOut>,
    val message: String,
    val content: @Composable (
        declare: ConstrainedScope.() -> SwingModifier,
        bound: ConstrainedScope.() -> SwingModifier,
    ) -> Unit,
)

/**
 * One modifier per kind of layout node that sizes to its content, each moving or resizing the child it declares,
 * paired with the row height and the shared baseline y it must line up at, worked out the way androidx's
 * baseline-aligned `Row` does: the shared line sits at the lower of the two children's own baselines, measured from
 * their own top, and the row grows to fit whichever child's own extent below that line is taller.
 */
private val BASELINE_MOVING_MODIFIERS: List<Triple<String, RowScope.() -> SwingModifier, Pair<Int, Int>>> =
    listOf(
        Triple("padding", { SwingModifier.padding(top = 7) }, 47 to 17),
        Triple("offset", { SwingModifier.offset(y = 5) }, 45 to 15),
        Triple("requiredSize", { SwingModifier.requiredSize(30, 20) }, 40 to 10),
        Triple("sizeIn", { SwingModifier.sizeIn(maxHeight = 12) }, 40 to 10),
        Triple("aspectRatio", { SwingModifier.aspectRatio(2f) }, 40 to 10),
        Triple("wrapContentHeight", { SwingModifier.requiredHeight(60).wrapContentHeight() }, 60 to 20),
        Triple("defaultMinSize", { SwingModifier.defaultMinSize(minHeight = 50) }, 50 to 10),
        Triple("height(Max)", { SwingModifier.height(IntrinsicSize.Max) }, 40 to 10),
        Triple("clipToBounds", { SwingModifier.clipToBounds() }, 40 to 10),
        Triple("zIndex", { SwingModifier.zIndex(1f) }, 40 to 10),
        Triple(
            "layout",
            {
                SwingModifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) { placeable.place(0, 6) }
                }
            },
            46 to 16,
        ),
        Triple("LayoutModifierNode", { SwingModifier.then(ShiftDownElement) }, 46 to 16),
    )

/** A layout node that places its content 6 lower than it would otherwise sit. */
private data object ShiftDownElement : LayoutModifierNodeElement<ShiftDownNode>() {
    override fun create(): ShiftDownNode = ShiftDownNode()

    override fun update(node: ShiftDownNode): Unit = Unit
}

private class ShiftDownNode : LayoutModifierNode() {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.place(0, 6) }
    }
}

private fun preferredSizeCases(): List<PreferredSizeCase> =
    listOf(rowPreferredSizeCase(), columnPreferredSizeCase(), boxPreferredSizeCase())

private fun rowPreferredSizeCase(): PreferredSizeCase =
    PreferredSizeCase(
        name = "Row",
        androidxSizes = ROW_OFF_PREFERRED_SIZES,
        message =
            "a row must be laid out at the size it prefers, with or without a genuine bound on its height, " +
                "whatever layout modifier its baseline-aligned child declares, except where androidx " +
                "itself lays it out at another size, at the sizes in ROW_OFF_PREFERRED_SIZES",
        content = { declare, bound ->
            Row {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG).then(bound())) {
                    DecoratedBaselineChild(30, SwingModifier.alignByBaseline())
                    DecoratedBaselineChild(10, declare().alignByBaseline())
                }
            }
        },
    )

private fun columnPreferredSizeCase(): PreferredSizeCase =
    PreferredSizeCase(
        name = "Column",
        androidxSizes = COLUMN_ANDROIDX_SIZES,
        message =
            "a column must be laid out at the size it prefers, with or without a genuine bound on its width, " +
                "whatever layout modifier its aligned child declares, except where androidx " +
                "itself lays it out at another size, at the sizes in COLUMN_ANDROIDX_SIZES",
        content = { declare, bound ->
            Column {
                Column(modifier = SwingModifier.testTag(CONTAINER_TAG).then(bound())) {
                    SizedChild(0, SwingModifier.alignBy { it.measuredWidth })
                    SizedChild(1, declare().alignBy { it.measuredWidth })
                }
            }
        },
    )

private fun boxPreferredSizeCase(): PreferredSizeCase =
    PreferredSizeCase(
        name = "Box",
        androidxSizes =
            mapOf(
                "aspectRatio(2f), bounded" to PreferredAndLaidOut(Dimension(50, 40), Dimension(80, 40)),
                "aspectRatio(2f, matchHeightConstraintsFirst), bounded" to
                    PreferredAndLaidOut(Dimension(50, 40), Dimension(80, 40)),
                "aspectRatio(2f).padding(5), bounded" to
                    PreferredAndLaidOut(Dimension(60, 40), Dimension(100, 50)),
                "padding(5).aspectRatio(2f), bounded" to
                    PreferredAndLaidOut(Dimension(60, 40), Dimension(90, 50)),
                "aspectRatio(0.5f), unbounded" to
                    PreferredAndLaidOut(Dimension(50, 100), Dimension(50, 40)),
                "aspectRatio(2f).padding(5), unbounded" to
                    PreferredAndLaidOut(
                        Dimension(60, 40),
                        Dimension(60, 50),
                    ),
                "padding(5).aspectRatio(2f), unbounded" to
                    PreferredAndLaidOut(
                        Dimension(60, 40),
                        Dimension(60, 50),
                    ),
            ),
        message =
            "a box preserves finite intrinsic offers while its native preferred height is read at its preferred " +
                "width, so the aspect ratio can affect it even when measurement is unbounded, which keeps the " +
                "child's own size, and a bounded Column queries the Box's width at a finite height and grants " +
                "that width",
        content = { declare, bound ->
            Column {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG).then(bound())) {
                    SizedChild(0)
                    SizedChild(1, declare())
                }
            }
        },
    )
