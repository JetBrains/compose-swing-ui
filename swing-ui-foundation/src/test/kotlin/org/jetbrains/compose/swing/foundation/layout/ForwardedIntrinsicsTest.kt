package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JComponent
import javax.swing.JTextArea
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * An intrinsic question that passes through code written against the public API - a [LayoutModifierNode] or a
 * [MeasurePolicy] asking its child each intrinsic question as it is asked - is answered as the built-in tree without
 * that code answers it. Each case lays a tree out twice, with the forwarding code and without it, and expects the same
 * bounds, a container as tall as its content, a leaf at the height it needs at the width it is placed at, and the
 * content of a container leaf within it at its own ratio.
 */
class ForwardedIntrinsicsTest {
    @Test
    fun aFillOffersALeafItsWidthThroughForwardingModifiers() {
        for (size in IntrinsicSize.entries) {
            for (fraction in listOf(1f, 0.5f)) {
                for (outside in listOf(false, true)) {
                    forEachLeafAndForwarding { leafName, leaf, forwarding ->
                        val placement = if (outside) "outside" else "inside"
                        assertForwardingKeepsTheLayout(
                            "$size, $leafName, fillMaxWidth($fraction), $forwarding $placement it",
                            size,
                            trailing = forwarding.trailing,
                        ) { forwarded ->
                            Box(modifier = SwingModifier.testTag(CONTAINER_TAG).width(WIDTH).height(size)) {
                                val fill = SwingModifier.fillMaxWidth(fraction)
                                val shape = with(forwarding) { SwingModifier.modifier(forwarded) }
                                leaf(if (outside) shape then fill else fill then shape)
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun aWeightedShareReachesTheLeafThroughForwardingModifiers() {
        for (size in IntrinsicSize.entries) {
            forEachLeafAndForwarding { leafName, leaf, forwarding ->
                assertForwardingKeepsTheLayout(
                    "$size, $leafName, a row's weighted share through $forwarding",
                    size,
                    trailing = forwarding.trailing,
                ) { forwarded ->
                    Row(modifier = SwingModifier.testTag(CONTAINER_TAG).width(WIDTH).height(size)) {
                        leaf(SwingModifier.weight(1f) then with(forwarding) { SwingModifier.modifier(forwarded) })
                    }
                }
                assertForwardingKeepsTheLayout(
                    "$size, $leafName, a filling column's weighted share through $forwarding",
                    size,
                    trailing = forwarding.trailing,
                ) { forwarded ->
                    Column(modifier = SwingModifier.testTag(CONTAINER_TAG).width(WIDTH).height(size)) {
                        leaf(
                            SwingModifier.weight(1f).fillMaxWidth() then
                                with(forwarding) { SwingModifier.modifier(forwarded) },
                        )
                    }
                }
            }
        }
    }

    @Test
    fun aBaselineAlignedLeafTakesItsShareThroughForwardingModifiers() {
        for (size in IntrinsicSize.entries) {
            forEachLeafAndForwarding { leafName, leaf, forwarding ->
                assertForwardingKeepsTheLayout(
                    "$size, $leafName, aligned by its baseline through $forwarding",
                    size,
                    trailing = forwarding.trailing,
                ) { forwarded ->
                    Row(modifier = SwingModifier.testTag(CONTAINER_TAG).width(WIDTH).height(size)) {
                        leaf(
                            SwingModifier.weight(1f).alignByBaseline() then
                                with(forwarding) { SwingModifier.modifier(forwarded) },
                        )
                        Label("Side", modifier = SwingModifier.alignByBaseline())
                    }
                }
            }
        }
    }

    @Test
    fun aBoxPropagatingItsMinimumOffersTheLeafItsWidthThroughForwardingModifiers() {
        for (size in IntrinsicSize.entries) {
            forEachLeafAndForwarding { leafName, leaf, forwarding ->
                assertForwardingKeepsTheLayout(
                    "$size, $leafName, a box propagating its minimum through $forwarding",
                    size,
                    trailing = forwarding.trailing,
                ) { forwarded ->
                    Box(
                        modifier = SwingModifier.testTag(CONTAINER_TAG).width(WIDTH).height(size),
                        propagateMinConstraints = true,
                    ) {
                        leaf(with(forwarding) { SwingModifier.modifier(forwarded) })
                    }
                }
            }
        }
    }

    @Test
    fun aNestedContainerOffersTheLeafTheWidthItsPolicyMeasuresItAtThroughForwardingModifiers() {
        val nested: Map<String, @Composable (SwingModifier, @Composable () -> Unit) -> Unit> =
            mapOf(
                "a box propagating its minimum" to { modifier, content ->
                    Box(modifier = modifier, propagateMinConstraints = true) { content() }
                },
                "a box propagating its minimum and preferring all of the width" to { modifier, content ->
                    Box(modifier = modifier, propagateMinConstraints = true) {
                        Box(modifier = SwingModifier.width(WIDTH))
                        content()
                    }
                },
                "a column" to { modifier, content -> Column(modifier = modifier) { content() } },
            )
        for (size in IntrinsicSize.entries) {
            for ((nestedName, container) in nested) {
                forEachLeafAndForwarding { leafName, leaf, forwarding ->
                    val case = "$size, $leafName in $nestedName through $forwarding"
                    assertForwardingKeepsTheLayout("$case, filling a box", size, forwarding.trailing) { forwarded ->
                        Box(modifier = SwingModifier.testTag(CONTAINER_TAG).width(WIDTH).height(size)) {
                            container(
                                SwingModifier.fillMaxWidth(),
                            ) { leaf(with(forwarding) { SwingModifier.modifier(forwarded) }) }
                        }
                    }
                    assertForwardingKeepsTheLayout("$case, filling a column", size, forwarding.trailing) { forwarded ->
                        Column(modifier = SwingModifier.testTag(CONTAINER_TAG).width(WIDTH).height(size)) {
                            container(
                                SwingModifier.fillMaxWidth(),
                            ) { leaf(with(forwarding) { SwingModifier.modifier(forwarded) }) }
                        }
                    }
                    assertForwardingKeepsTheLayout("$case, a row's share", size, forwarding.trailing) { forwarded ->
                        Row(modifier = SwingModifier.testTag(CONTAINER_TAG).width(WIDTH).height(size)) {
                            container(
                                SwingModifier.weight(1f),
                            ) { leaf(with(forwarding) { SwingModifier.modifier(forwarded) }) }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun aRequiredWidthOffersTheLeafTheWidthItFixesThroughForwardingModifiers() {
        for (size in IntrinsicSize.entries) {
            for ((containerName, container) in Containers) {
                forEachLeafAndForwarding { leafName, leaf, forwarding ->
                    assertForwardingKeepsTheLayout(
                        "$size, $leafName in $containerName, requiredWidth($FIXED_WIDTH) outside $forwarding",
                        size,
                        trailing = forwarding.trailing,
                    ) { forwarded ->
                        container(SwingModifier.testTag(CONTAINER_TAG).width(WIDTH).height(size)) {
                            leaf(
                                SwingModifier.requiredWidth(FIXED_WIDTH) then
                                    with(forwarding) { SwingModifier.modifier(forwarded) },
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun aRequiredMinimumWidthOffersAStockLeafTheWidthItHoldsThroughForwardingModifiers() {
        val minimums: Map<String, ConstrainedScope.() -> SwingModifier> =
            mapOf(
                "requiredWidthIn(min = $FIXED_WIDTH)" to { SwingModifier.requiredWidthIn(min = FIXED_WIDTH) },
                "requiredSizeIn(minWidth = $FIXED_WIDTH)" to { SwingModifier.requiredSizeIn(minWidth = FIXED_WIDTH) },
            )
        for (size in IntrinsicSize.entries) {
            for ((containerName, container) in Containers) {
                for ((minimumName, minimum) in minimums) {
                    // A modifier that asks its child at a narrower width keeps the offer's least width, so only
                    // forwarding that asks at the width it is asked lays out as the built-in tree does.
                    val forwardings = listOf(Forwarding.Unchanged, Forwarding.Stacked)
                    forEachLeafAndForwarding(StockLeaves, forwardings) { leafName, leaf, forwarding ->
                        assertForwardingKeepsTheLayout(
                            "$size, $leafName in $containerName, $minimumName outside $forwarding",
                            size,
                            trailing = forwarding.trailing,
                        ) { forwarded ->
                            container(SwingModifier.testTag(CONTAINER_TAG).width(WIDTH).height(size)) {
                                leaf(minimum() then with(forwarding) { SwingModifier.modifier(forwarded) })
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun aPropagatedMinimumReachesTheLeafThroughASizeInAndForwardingModifiers() {
        for (size in IntrinsicSize.entries) {
            forEachLeafAndForwarding { leafName, leaf, forwarding ->
                assertForwardingKeepsTheLayout(
                    "$size, $leafName, sizeIn(minWidth = $FIXED_WIDTH) in a box propagating its minimum, " +
                        "outside $forwarding",
                    size,
                    trailing = forwarding.trailing,
                ) { forwarded ->
                    Box(
                        modifier = SwingModifier.testTag(CONTAINER_TAG).width(WIDTH).height(size),
                        propagateMinConstraints = true,
                    ) {
                        leaf(
                            SwingModifier.sizeIn(minWidth = FIXED_WIDTH) then
                                with(forwarding) { SwingModifier.modifier(forwarded) },
                        )
                    }
                }
            }
        }
    }

    @Test
    fun aRequiredWidthBoundOffersTheLeafTheMinimumItMeasuresItUnderThroughForwardingModifiers() {
        val bounds: Map<String, ConstrainedScope.() -> SwingModifier> =
            mapOf(
                "requiredWidthIn(min = 0, max = $WIDTH)" to { SwingModifier.requiredWidthIn(min = 0, max = WIDTH) },
                "requiredSizeIn(minWidth = 0, maxWidth = $WIDTH)" to {
                    SwingModifier.requiredSizeIn(minWidth = 0, maxWidth = WIDTH)
                },
                "requiredWidthIn(max = $WIDTH)" to { SwingModifier.requiredWidthIn(max = WIDTH) },
            )
        for (size in IntrinsicSize.entries) {
            for ((boundName, bound) in bounds) {
                forEachLeafAndForwarding { leafName, leaf, forwarding ->
                    assertForwardingKeepsTheLayout(
                        "$size, $leafName, $boundName in a box propagating its minimum, outside $forwarding",
                        size,
                        trailing = forwarding.trailing,
                    ) { forwarded ->
                        Box(
                            modifier = SwingModifier.testTag(CONTAINER_TAG).width(WIDTH).height(size),
                            propagateMinConstraints = true,
                        ) {
                            leaf(bound() then with(forwarding) { SwingModifier.modifier(forwarded) })
                        }
                    }
                }
            }
        }
    }

    @Test
    fun aModifierDroppingTheMinimumAnswersAsTheRequiredWidthThatDropsIt() {
        for (size in IntrinsicSize.entries) {
            for ((leafName, leaf) in Leaves) {
                assertForwardingKeepsTheLayout("$size, $leafName", size, trailing = 0) { forwarded ->
                    Box(
                        modifier = SwingModifier.testTag(CONTAINER_TAG).width(WIDTH).height(size),
                        propagateMinConstraints = true,
                    ) {
                        val dropping = SwingModifier.requiredWidthIn(min = 0, max = WIDTH)
                        leaf(if (forwarded) MinimumDroppingElement else dropping)
                    }
                }
            }
        }
    }

    @Test
    fun anOfferFromANegativeWidthIsRejected() {
        val asksFromANegativeWidth =
            object : MeasurePolicy {
                override fun MeasureScope.measure(
                    measurables: List<Measurable>,
                    constraints: Constraints,
                ): MeasureResult = layout(constraints.minWidth, constraints.minHeight) {}

                override fun IntrinsicMeasureScope.minIntrinsicHeight(
                    measurables: List<IntrinsicMeasurable>,
                    width: Int,
                ): Int = measurables.single().withOfferedMinWidth(-1).minIntrinsicHeight(width)
            }
        runComposeSwingTest {
            setContent {
                Layout(asksFromANegativeWidth, SwingModifier.testTag(CONTAINER_TAG)) { Label("Leaf") }
            }
            awaitIdle()
            val container = onNodeWithTag(CONTAINER_TAG).fetch<JComponent>() as Constrainable
            assertFailsWith<IllegalArgumentException> { container.minIntrinsicHeight(WIDTH) }
        }
    }

    @Test
    fun aLayoutAnswersAsTheBoxThatMeasuresItsChildAlike() {
        val offers: Map<String, ConstrainedScope.() -> SwingModifier> =
            mapOf(
                "no offer" to { SwingModifier },
                "fillMaxWidth()" to { SwingModifier.fillMaxWidth() },
            )
        for (size in IntrinsicSize.entries) {
            for (policy in CustomPolicy.entries) {
                for ((offerName, offer) in offers) {
                    for ((leafName, leaf) in Leaves) {
                        val case = "$size, $leafName, $offerName, $policy"
                        assertForwardingKeepsTheLayout(case, size, trailing = 0) { forwarded ->
                            val modifier = SwingModifier.testTag(CONTAINER_TAG).width(WIDTH).height(size)
                            if (forwarded) {
                                Layout(measurePolicy = policy.policy, modifier = modifier) { leaf(offer()) }
                            } else {
                                Box(modifier = modifier, propagateMinConstraints = policy.propagatesMinimum) {
                                    leaf(offer())
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun aDirectAskOfAChildCarriesNoOfferSoALayoutAnswersAsThoughItHadNoMinimum() {
        for (size in IntrinsicSize.entries) {
            for ((leafName, leaf) in NarrowLeaf) {
                val case = "$size, $leafName"
                val modifier: ConstrainedScope.() -> SwingModifier = {
                    SwingModifier.testTag(CONTAINER_TAG).width(WIDTH).height(size)
                }
                val relaxed =
                    laidOut(size, CONTAINER_TAG, LEAF_TAG) {
                        Box(modifier = modifier(), propagateMinConstraints = false) { leaf(SwingModifier) }
                    }
                val forwarded =
                    laidOut(size, CONTAINER_TAG, LEAF_TAG) {
                        Layout(ForwardingPolicy(keepsMinimum = true, passesOffer = false), modifier()) {
                            leaf(SwingModifier)
                        }
                    }
                assertEquals(
                    relaxed.container,
                    forwarded.container,
                    "$case: a direct ask carries no offer, so the policy answers as though the offer had no minimum",
                )
                assertTrue(
                    forwarded.leafNeeds < forwarded.container.height,
                    "$case: the leaf the policy measures under the minimum needs less than the height it answered",
                )
            }
        }
    }

    private companion object {
        const val CONTAINER_TAG = "container"
        const val LEAF_TAG = "leaf"
        const val WIDTH = 320
        const val FIXED_WIDTH = 200

        /** The width-to-height ratio of a container leaf's content. */
        const val CONTENT_RATIO = 2f

        /** Containers holding one child, each declared with the modifier handed in. */
        val Containers: Map<String, @Composable (SwingModifier, @Composable ConstrainedScope.() -> Unit) -> Unit> =
            mapOf(
                "a box" to { modifier, content -> Box(modifier = modifier) { content() } },
                "a column" to { modifier, content -> Column(modifier = modifier) { content() } },
                "a row" to { modifier, content -> Row(modifier = modifier) { content() } },
            )

        /**
         * A stock leaf that prefers less than [WIDTH] and whose height follows the width it holds, so a minimum width
         * offered to it changes its height.
         */
        val NarrowLeaf: Map<String, @Composable (SwingModifier) -> Unit> =
            mapOf(
                "a narrow wrapping widget" to { modifier ->
                    SwingNode(
                        factory = { NarrowWrappingComponent() },
                        modifier = SwingModifier.testTag(LEAF_TAG) then modifier,
                    )
                },
            )

        /** Stock leaves whose height follows their width, tagged [LEAF_TAG] and declared with the modifier given. */
        val StockLeaves: Map<String, @Composable (SwingModifier) -> Unit> =
            mapOf<String, @Composable (SwingModifier) -> Unit>(
                "a wrapping text area" to { modifier ->
                    SwingNode(
                        factory = {
                            JTextArea(WRAPPING_TEXT).apply {
                                columns = 10
                                lineWrap = true
                                wrapStyleWord = true
                            }
                        },
                        modifier = SwingModifier.testTag(LEAF_TAG) then modifier,
                    )
                },
                "an HTML label" to { modifier ->
                    Label("<html>$WRAPPING_TEXT</html>", modifier = SwingModifier.testTag(LEAF_TAG) then modifier)
                },
            ) + NarrowLeaf

        /** The [StockLeaves], and a container whose one child's height follows its width. */
        val Leaves: Map<String, @Composable (SwingModifier) -> Unit> =
            StockLeaves +
                mapOf(
                    "a nested column" to { modifier ->
                        Column(modifier = SwingModifier.testTag(LEAF_TAG) then modifier) {
                            Label("Preview", modifier = SwingModifier.fillMaxWidth().aspectRatio(CONTENT_RATIO))
                        }
                    },
                )

        inline fun forEachLeafAndForwarding(
            leaves: Map<String, @Composable (SwingModifier) -> Unit> = Leaves,
            forwardings: List<Forwarding> = Forwarding.entries,
            block: (String, @Composable (SwingModifier) -> Unit, Forwarding) -> Unit,
        ) {
            for ((leafName, leaf) in leaves) {
                for (forwarding in forwardings) {
                    block(leafName, leaf, forwarding)
                }
            }
        }

        /**
         * Lays [scene], whose container asks its [size] intrinsic height, out without and with forwarding code, and
         * asserts both place the container and the leaf alike, that the container is as tall as the leaf plus the
         * [trailing] extent the forwarding adds below it, that the leaf takes its [size] height at its width, and that
         * a container leaf's content keeps its ratio inside the leaf.
         */
        fun assertForwardingKeepsTheLayout(
            case: String,
            size: IntrinsicSize,
            trailing: Int,
            scene: @Composable ColumnScope.(forwarded: Boolean) -> Unit,
        ) {
            val builtIn = laidOut(size, CONTAINER_TAG, LEAF_TAG) { scene(false) }
            val forwarded = laidOut(size, CONTAINER_TAG, LEAF_TAG) { scene(true) }

            assertEquals(builtIn, forwarded, "$case: the tree lays out as it does without the forwarding code")
            assertEquals(
                forwarded.leaf.y + forwarded.leaf.height + trailing,
                forwarded.container.height,
                "$case: the container is as tall as its content, with no gap below it",
            )
            assertEquals(
                forwarded.leafNeeds,
                forwarded.leaf.height,
                "$case: the leaf takes the height it needs at the width it is placed at, so nothing is cut off",
            )
            val content = forwarded.content ?: return
            assertEquals(
                (content.width / CONTENT_RATIO).roundToInt(),
                content.height,
                "$case: the leaf's content keeps its ratio",
            )
            assertTrue(
                content.y >= 0 && content.y + content.height <= forwarded.leaf.height,
                "$case: the leaf's content lies within the leaf",
            )
        }
    }
}

/**
 * A modifier written against the public API that asks its child each intrinsic question, and its built-in twin, which
 * reserve [trailing] below the child.
 */
private enum class Forwarding(
    val trailing: Int,
) {
    /** A [ForwardingNode], where the built-in tree declares nothing. */
    Unchanged(0),

    /** Two [ForwardingNode]s, where the built-in tree declares nothing. */
    Stacked(0),

    /** A [PaddingLikeNode], where the built-in tree declares [padding] of [PADDING]. */
    PaddingLike(PADDING),
    ;

    /** The forwarding modifier where [forwarded], and the built-in one otherwise. */
    context(scope: ConstrainedScope)
    fun SwingModifier.modifier(forwarded: Boolean): SwingModifier =
        when (this@Forwarding) {
            Forwarding.Unchanged -> if (forwarded) (this then ForwardingElement) else this
            Forwarding.Stacked -> if (forwarded) (this then ForwardingElement then ForwardingElement) else this
            Forwarding.PaddingLike -> if (forwarded) (this then PaddingLikeElement) else padding(PADDING)
        }
}

private const val PADDING = 4

private data object ForwardingElement : LayoutModifierNodeElement<ForwardingNode>() {
    override fun create(): ForwardingNode = ForwardingNode()

    override fun update(node: ForwardingNode): Unit = Unit
}

/** Measures its child under the constraints it is given, and asks each intrinsic question of its child as asked. */
private class ForwardingNode : LayoutModifierNode() {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
    ): Int = measurable.minIntrinsicWidth(height)

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
    ): Int = measurable.maxIntrinsicWidth(height)

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int = measurable.minIntrinsicHeight(width)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int = measurable.maxIntrinsicHeight(width)
}

private data object PaddingLikeElement : LayoutModifierNodeElement<PaddingLikeNode>() {
    override fun create(): PaddingLikeNode = PaddingLikeNode()

    override fun update(node: PaddingLikeNode): Unit = Unit
}

/**
 * Reserves [PADDING] along every edge of its child, as [padding] does, and answers each intrinsic question by asking
 * its child at the extent less the padding and adding the padding back.
 */
private class PaddingLikeNode : LayoutModifierNode() {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable =
            measurable.measure(
                Constraints(
                    minWidth = constraints.minWidth.lessPadding(),
                    maxWidth = constraints.maxWidth.lessPadding(),
                    minHeight = constraints.minHeight.lessPadding(),
                    maxHeight = constraints.maxHeight.lessPadding(),
                ),
            )
        val width = (placeable.width + 2 * PADDING).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (placeable.height + 2 * PADDING).coerceIn(constraints.minHeight, constraints.maxHeight)
        return layout(width, height) { placeable.place(PADDING, PADDING) }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
    ): Int = measurable.minIntrinsicWidth(height.lessPadding()) + 2 * PADDING

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
    ): Int = measurable.maxIntrinsicWidth(height.lessPadding()) + 2 * PADDING

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int = measurable.minIntrinsicHeight(width.lessPadding()) + 2 * PADDING

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int = measurable.maxIntrinsicHeight(width.lessPadding()) + 2 * PADDING

    /** This extent less the padding on both of its edges, never below zero; an unbounded extent stays unbounded. */
    private fun Int.lessPadding(): Int =
        if (this == Constraints.Infinity) this else (this - 2 * PADDING).coerceAtLeast(0)
}

/**
 * A [MeasurePolicy] written against the public API, and whether the [Box] that measures its child alike propagates its
 * minimum.
 */
private enum class CustomPolicy(
    val policy: MeasurePolicy,
    val propagatesMinimum: Boolean,
) {
    /** Measures its child under the constraints it is given less their minimum, and asks it each question as asked. */
    Forwarding(ForwardingPolicy(keepsMinimum = false, passesOffer = false), propagatesMinimum = false),

    /**
     * Measures its child under the constraints it is given, and asks it each question as asked, its height under the
     * offer it is asked under.
     */
    ForwardingTheOffer(ForwardingPolicy(keepsMinimum = true, passesOffer = true), propagatesMinimum = true),

    /** Measures its child under the constraints it is given, and answers its intrinsic questions by default. */
    Measuring(MeasuringPolicy(dropsMinimum = false), propagatesMinimum = true),

    /** Measures its child under the constraints it is given less their minimum, and answers by default. */
    MeasuringWithoutMinimum(MeasuringPolicy(dropsMinimum = true), propagatesMinimum = false),
}

/** Measures its one child under the constraints it is given, or under them less their minimum where [dropsMinimum]. */
private class MeasuringPolicy(
    private val dropsMinimum: Boolean,
) : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val offered = if (dropsMinimum) constraints.copy(minWidth = 0, minHeight = 0) else constraints
        val placeable = measurables.single().measure(offered)
        val width = placeable.width.coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = placeable.height.coerceIn(constraints.minHeight, constraints.maxHeight)
        return layout(width, height) { placeable.place(0, 0) }
    }
}

/**
 * Measures its one child under the constraints it is given, less their minimum unless it [keepsMinimum], and asks it
 * each intrinsic question, its height under the offer it is asked under where it [passesOffer].
 */
private class ForwardingPolicy(
    keepsMinimum: Boolean,
    private val passesOffer: Boolean,
) : MeasurePolicy by MeasuringPolicy(dropsMinimum = !keepsMinimum) {
    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int = measurables.single().minIntrinsicWidth(height)

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int = measurables.single().maxIntrinsicWidth(height)

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int = measurables.single().heightAsked().minIntrinsicHeight(width)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int = measurables.single().heightAsked().maxIntrinsicHeight(width)

    context(scope: IntrinsicMeasureScope)
    private fun IntrinsicMeasurable.heightAsked(): IntrinsicMeasurable =
        if (passesOffer) withOfferedMinWidth(scope.offeredMinWidth) else this
}

private data object MinimumDroppingElement : LayoutModifierNodeElement<MinimumDroppingNode>() {
    override fun create(): MinimumDroppingNode = MinimumDroppingNode()

    override fun update(node: MinimumDroppingNode): Unit = Unit
}

/**
 * Measures its child under the constraints it is given less their minimum width, and asks its child each height
 * question under no width offer.
 */
private class MinimumDroppingNode : LayoutModifierNode() {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints.copy(minWidth = 0))
        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int = measurable.withOfferedMinWidth(0).minIntrinsicHeight(width)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int = measurable.withOfferedMinWidth(0).maxIntrinsicHeight(width)
}
