package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.border
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.alignmentX
import org.jetbrains.compose.swing.modifier.layout.alignmentY
import org.jetbrains.compose.swing.modifier.layout.minimumSize
import org.jetbrains.compose.swing.modifier.layout.visible
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.border.EmptyBorder
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What a [Row], a [Column] or a [Box] answers the layout above it, which is everything a parent can ask
 * of a container before it decides where to put it: the smallest extent it can be laid out at, the
 * largest, the alignment its content lines up on, and the extent it prefers.
 *
 * A container's minimum carries weight beyond a parent that honors it: `SplitPane` clamps divider travel
 * against it, so a minimum read off the wrong extent either refuses the divider space it has or lets it
 * crush content that said it could not shrink.
 */
class RowColumnParentQueryTest {
    @Test
    fun aColumnsMinimumStacksItsChildrensMinimaIncludingWeightsAndArrangementGaps() {
        val rows =
            listOf(
                "stacked" to List(CHILD_COUNT) { MIN_HEIGHT },
                "spaced" to List(CHILD_COUNT) { MIN_HEIGHT },
                "weighted" to listOf(MIN_HEIGHT, WEIGHTED_MIN_HEIGHT),
            )
        for ((name, heights) in rows) {
            val gap = if (name == "spaced") SPACING else 0
            runComposeSwingTest {
                setContent {
                    Column(
                        modifier = SwingModifier.testTag(CONTAINER_TAG),
                        verticalArrangement = if (gap == 0) Arrangement.Top else Arrangement.spacedBy(gap),
                    ) {
                        for ((index, height) in heights.withIndex()) {
                            val weight =
                                if (name == "weighted" && index == 1) SwingModifier.weight(1f) else SwingModifier
                            SizedChild(index, weight.minimumSize(MIN_WIDTH, height))
                        }
                    }
                }
                assertEquals(
                    Dimension(MIN_WIDTH, heights.sum() + (heights.size - 1) * gap),
                    container().minimumSize,
                    "$name: the minimum stacks the children's minimum heights and keeps each arrangement gap",
                )
            }
        }
    }

    @Test
    fun aRowAColumnAndABoxTakeAnyExtentTheyAreOffered() =
        runComposeSwingTest {
            setContent {
                ParentQueryContainers.values.forEachIndexed { index, container -> container { SizedChild(index) } }
            }

            val unbounded = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
            for (tag in ParentQueryContainers.keys) {
                assertEquals(
                    unbounded,
                    panel(tag).maximumSize,
                    "'$tag' must offer its parent no ceiling: it places whatever extent it is given rather " +
                        "than refusing the surplus",
                )
            }
        }

    @Test
    fun aContainerReportsItsFirstDeclaredChildsAlignmentOrCenteredAlignmentWhenEmpty() {
        val rows =
            listOf(
                "first declared" to listOf(LEADING to TRAILING, TRAILING to LEADING),
                "hidden" to listOf(TRAILING to LEADING, LEADING to TRAILING),
                "empty" to emptyList(),
            )
        for ((name, alignments) in rows) {
            val containers = ParentQueryContainers
            runComposeSwingTest {
                setContent {
                    for (container in containers.values) {
                        container {
                            for ((index, alignment) in alignments.withIndex()) {
                                val modifier = SwingModifier.alignmentX(alignment.first).alignmentY(alignment.second)
                                SizedChild(
                                    index,
                                    if (name == "hidden" && index == 0) modifier.visible(false) else modifier,
                                )
                            }
                        }
                    }
                }
                val expected = alignments.firstOrNull() ?: (Component.CENTER_ALIGNMENT to Component.CENTER_ALIGNMENT)
                for (tag in containers.keys) {
                    val panel = panel(tag)
                    assertEquals(expected, panel.alignmentX to panel.alignmentY, "$tag, $name: parent alignment")
                }
            }
        }
    }

    /** Restacking a child by z-index changes the paint and hit-testing order, not which child a parent query asks. */
    @Test
    fun aBoxRestackedByAZIndexStillReportsItsFirstDeclaredChild() =
        runComposeSwingTest {
            var z by mutableFloatStateOf(0f)
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    SizedChild(0, SwingModifier.alignmentX(LEADING).alignmentY(TRAILING).zIndex(z))
                    SizedChild(1, SwingModifier.alignmentX(Component.CENTER_ALIGNMENT))
                    SizedChild(2, SwingModifier.alignmentX(TRAILING))
                }
            }

            for (index in listOf(0f, 5f)) {
                z = index
                awaitIdle()
                assertEquals(
                    LEADING to TRAILING,
                    container().alignmentX to container().alignmentY,
                    "z-index=$index: restacking keeps the first declared child's alignment on both axes",
                )
            }
        }

    /** A container's declaration order, and so the child a parent query asks, survives removal and insertion. */
    @Test
    fun aContainerFollowsItsFirstDeclaredChildThroughRemovalAndInsertion() =
        runComposeSwingTest {
            var presentA by mutableStateOf(true)
            var presentC by mutableStateOf(false)
            setContent {
                ParentQueryContainers.values.forEach { it { OrderedChildren(presentA, presentC) } }
            }

            assertContainerAlignments(LEADING, "before A is removed")
            presentA = false
            awaitIdle()
            assertContainerAlignments(TRAILING, "once A is removed")
            presentC = true
            awaitIdle()
            assertContainerAlignments(Component.CENTER_ALIGNMENT, "once C is declared first")
        }

    private fun ComposeSwingTest.assertContainerAlignments(
        expected: Float,
        state: String,
    ) {
        for (tag in ParentQueryContainers.keys) {
            assertEquals(expected, panel(tag).alignmentX, "$tag reports its first declared child's alignment $state")
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
                        SizedChild(0, SwingModifier.alignmentX(LEADING).alignmentY(TRAILING))
                        SizedChild(1, SwingModifier.alignmentX(TRAILING).alignmentY(LEADING))
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

            assertEquals(
                LEADING,
                container().alignmentX,
                "a custom layout must report its first declared child's x alignment",
            )
            assertEquals(TRAILING, container().alignmentY, "and its y alignment, not the order it placed them in")
        }

    /** Content shared by [aContainerFollowsItsFirstDeclaredChildThroughRemovalAndInsertion]'s three containers. */
    @Composable
    private fun OrderedChildren(
        presentA: Boolean,
        presentC: Boolean,
    ) {
        if (presentC) SizedChild(2, SwingModifier.alignmentX(Component.CENTER_ALIGNMENT))
        if (presentA) SizedChild(0, SwingModifier.alignmentX(LEADING))
        SizedChild(1, SwingModifier.alignmentX(TRAILING))
    }

    @Test
    fun aColumnsBorderInsetsReachBothTheExtentsItAsksFor() =
        runComposeSwingTest {
            setContent {
                Column(modifier = SwingModifier.testTag(CONTAINER_TAG).border(EmptyBorder(TOP, LEFT, BOTTOM, RIGHT))) {
                    SizedChild(0, SwingModifier.minimumSize(MIN_WIDTH, MIN_HEIGHT))
                }
            }

            assertEquals(
                Dimension(CHILD_WIDTH + LEFT + RIGHT, CHILD_HEIGHT + TOP + BOTTOM),
                container().preferredSize,
                "the space the border takes must be added to the extent the column prefers, or its child is " +
                    "laid out in less width and height than it asked for",
            )
            assertEquals(
                Dimension(MIN_WIDTH + LEFT + RIGHT, MIN_HEIGHT + TOP + BOTTOM),
                container().minimumSize,
                "and to the extent it can shrink to, which the border does not shrink with it",
            )
        }

    @Test
    fun aRowsPreferredSizeIsItsMeasuredSizeWhateverLayoutModifierItsChildDeclares() {
        val mismatches =
            preferredAndMeasuredMismatches(ROW_OFF_PREFERRED_SIZES) { declare, bounded ->
                Row {
                    val bound = if (bounded) SwingModifier else SwingModifier.wrapContentSize(unbounded = true)
                    Row(modifier = SwingModifier.testTag(CONTAINER_TAG).then(bound)) {
                        DecoratedBaselineChild(30, SwingModifier.alignByBaseline())
                        DecoratedBaselineChild(10, declare().alignByBaseline())
                    }
                }
            }

        assertEquals(
            emptyList(),
            mismatches,
            "a row must be laid out at the size it prefers, with or without a genuine bound on its height, " +
                "whatever layout modifier its baseline-aligned child declares, except where androidx itself lays " +
                "it out at another size, at the sizes in ROW_OFF_PREFERRED_SIZES",
        )
    }

    @Test
    fun aColumnsPreferredSizeIsItsMeasuredSizeWhateverLayoutModifierItsChildDeclares() {
        val mismatches =
            preferredAndMeasuredMismatches(COLUMN_ANDROIDX_SIZES) { declare, bounded ->
                Column {
                    val bound = if (bounded) SwingModifier else SwingModifier.wrapContentSize(unbounded = true)
                    Column(modifier = SwingModifier.testTag(CONTAINER_TAG).then(bound)) {
                        SizedChild(0, SwingModifier.alignBy { it.measuredWidth })
                        SizedChild(1, declare().alignBy { it.measuredWidth })
                    }
                }
            }

        assertEquals(
            emptyList(),
            mismatches,
            "a column must be laid out at the size it prefers, with or without a genuine bound on its width, " +
                "whatever layout modifier its aligned child declares, except where androidx itself lays it out at " +
                "another size, at the sizes in COLUMN_ANDROIDX_SIZES",
        )
    }

    /**
     * Preferred height is queried at the preferred width, so the aspect ratio can affect that result
     * even when measurement is unbounded. Unbounded measurement keeps the child's own size.
     * A bounded Column queries the Box's width at a finite height and grants that width.
     */
    @Test
    fun aBoxUsesTheParentsFiniteIntrinsicOfferWhenItsChildDeclaresAnAspectRatio() {
        val mismatches =
            preferredAndMeasuredMismatches(
                mapOf(
                    "aspectRatio(2f), bounded" to PreferredAndLaidOut(Dimension(50, 40), Dimension(80, 40)),
                    "aspectRatio(2f, matchHeightConstraintsFirst), bounded" to
                        PreferredAndLaidOut(Dimension(50, 40), Dimension(80, 40)),
                    "aspectRatio(2f).padding(5), bounded" to PreferredAndLaidOut(Dimension(60, 40), Dimension(100, 50)),
                    "padding(5).aspectRatio(2f), bounded" to PreferredAndLaidOut(Dimension(60, 40), Dimension(90, 50)),
                    "aspectRatio(0.5f), unbounded" to PreferredAndLaidOut(Dimension(50, 100), Dimension(50, 40)),
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
            ) { declare, bounded ->
                Column {
                    val bound = if (bounded) SwingModifier else SwingModifier.wrapContentSize(unbounded = true)
                    Box(modifier = SwingModifier.testTag(CONTAINER_TAG).then(bound)) {
                        SizedChild(0)
                        SizedChild(1, declare())
                    }
                }
            }

        assertEquals(
            emptyList(),
            mismatches,
            "A box preserves finite intrinsic offers while its native preferred height " +
                "is read at its preferred width.",
        )
    }

    /** A row sizing itself reads a child's baseline where each kind of layout node moves it, as its layout does. */
    @Test
    fun aRowPrefersItsMeasuredHeightAndLinesUpBaselinesWhateverLayoutNodeMovesOne() =
        runComposeSwingTest {
            setContent {
                Column {
                    for ((name, declare, _) in BASELINE_MOVING_MODIFIERS) {
                        Row(modifier = SwingModifier.testTag(name)) {
                            DecoratedBaselineChild(10, SwingModifier.alignByBaseline())
                            DecoratedBaselineChild(10, declare().alignByBaseline())
                        }
                    }
                }
            }

            val mismatches =
                BASELINE_MOVING_MODIFIERS.mapNotNull { (name, _, expected) ->
                    val (height, baseline) = expected
                    val row = onNodeWithTag(name).fetch<JComponent>()
                    val (a, b) = row.childrenInDeclarationOrder()
                    val baselines = listOf(a, b).map { it.y + it.getBaseline(it.width, it.height) }
                    val actual = Triple(row.height, baselines[0], baselines[1])
                    val message = "$name: expected height $height and baseline $baseline, was $actual"
                    message.takeIf { actual != Triple(height, baseline, baseline) }
                }
            assertEquals(emptyList(), mismatches)
        }

    private companion object {
        // Below the fixture child on both axes, so a minimum read off the preferred extent is unmistakable.
        const val MIN_WIDTH = 18
        const val MIN_HEIGHT = 12

        // Different again, so the weighted child's own minimum is what its share is taken against.
        const val WEIGHTED_MIN_HEIGHT = 30

        const val SPACING = 8

        // Neither is the centered default, and the two differ, so an alignment reported on the wrong axis
        // is caught as readily as a fixed one.
        const val LEADING = 0f
        const val TRAILING = 1f

        // The four insets of the border a column is given, each different, so no two can be mistaken.
        const val TOP = 5
        const val LEFT = 10
        const val BOTTOM = 15
        const val RIGHT = 20

        const val ROW_TAG = "row"
        const val COLUMN_TAG = "column"
        const val BOX_TAG = "box"

        val ParentQueryContainers: Map<String, @Composable (@Composable () -> Unit) -> Unit> =
            mapOf(
                ROW_TAG to { content -> Row(modifier = SwingModifier.testTag(ROW_TAG)) { content() } },
                COLUMN_TAG to { content -> Column(modifier = SwingModifier.testTag(COLUMN_TAG)) { content() } },
                BOX_TAG to { content -> Box(modifier = SwingModifier.testTag(BOX_TAG)) { content() } },
            )
    }
}

/** The one container a test tagged [CONTAINER_TAG], which every reading of a single container is taken from. */
private fun ComposeSwingTest.container(): JComponent = panel(CONTAINER_TAG)

/** The container a test tagged [tag], for a case that declares more than one. */
private fun ComposeSwingTest.panel(tag: String): JComponent = onNodeWithTag(tag).fetch<JComponent>()

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
private data class PreferredAndLaidOut(
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

/**
 * Each [SIZING_MODIFIERS] entry, with a real bound on the container and with none at all
 * (`wrapContentSize(unbounded = true)`, which is the only way this Swing-packed harness decouples a container's real
 * layout from its own preferred size - a single-axis `wrapContentWidth`/`wrapContentHeight` still leaves the other
 * axis tied to the window's pack-to-preferred-size real bound, so it never differs from the bounded case here), for
 * which the container [content] declares is laid out at a size other than the one it prefers, or, for an entry in
 * [androidxSizes], at sizes other than the ones listed there, described for a failure message. [content] declares
 * the entry on a child through `declare`, and bounds the container when `bounded`.
 */
private fun preferredAndMeasuredMismatches(
    androidxSizes: Map<String, PreferredAndLaidOut>,
    content: @Composable (declare: ConstrainedScope.() -> SwingModifier, bounded: Boolean) -> Unit,
): List<String> {
    val mismatches = mutableListOf<String>()
    for ((name, declare) in SIZING_MODIFIERS) {
        for (bounded in listOf(true, false)) {
            runComposeSwingTest {
                setContent { content(declare, bounded) }

                val actual = PreferredAndLaidOut(container().preferredSize, container().size)
                val key = "$name, ${if (bounded) "bounded" else "unbounded"}"
                val expected = androidxSizes[key] ?: PreferredAndLaidOut(actual.preferred, actual.preferred)
                if (actual != expected) mismatches += "$key: expected $expected, was $actual"
            }
        }
    }
    return mismatches
}

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
